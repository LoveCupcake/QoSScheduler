package com.qos.scheduler.registry

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qos.scheduler.model.ConnectedDevice
import com.qos.scheduler.model.TrafficClass
import com.qos.scheduler.util.HotspotManager
import com.qos.scheduler.util.HostnameResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val Context.dataStore by preferencesDataStore(name = "device_priorities")

private const val INACTIVITY_TIMEOUT_MS = 60_000L
private const val THROUGHPUT_WINDOW_MS  = 3000L

class DeviceRegistry(private val context: Context) {

    private val devices = ConcurrentHashMap<String, ConnectedDevice>()
    private val byteSamples = ConcurrentHashMap<String, Long>()
    private val hotspotManager = HotspotManager(context)
    private val hostnameResolver by lazy { HostnameResolver(context) }

    private val _devicesFlow = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val devicesFlow: StateFlow<List<ConnectedDevice>> = _devicesFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var timeoutJob: Job? = null
    private var throughputJob: Job? = null

    fun start() {
        scope.launch { loadPersistedPriorities() }
        startTimeoutChecker()
        startThroughputSampler()
    }

    fun stop() {
        timeoutJob?.cancel()
        throughputJob?.cancel()
    }

    fun getOrCreate(ipAddress: String): ConnectedDevice {
        val isNew = !devices.containsKey(ipAddress)
        val device = devices.getOrPut(ipAddress) { 
            val mac = resolveMacAddress(ipAddress)
            val cachedHostname = hostnameResolver.getCachedHostname(ipAddress)
            val friendlyName = cachedHostname ?: hostnameResolver.generateFriendlyName(mac)
            
            val newDevice = ConnectedDevice(
                ipAddress = ipAddress,
                macAddress = mac,
                hostname = friendlyName
            )
            
            if (cachedHostname == null) {
                hostnameResolver.resolveHostname(ipAddress) { hostname ->
                    newDevice.hostname = hostname
                    publish()
                }
            }
            
            mac?.let { macAddr ->
                scope.launch {
                    val prefs = context.dataStore.data.first()
                    val key = stringPreferencesKey("priority_$macAddr")
                    prefs[key]?.let { priorityName ->
                        runCatching { TrafficClass.valueOf(priorityName) }
                            .getOrNull()?.let { priority ->
                                newDevice.priorityClass = priority
                                publish()
                            }
                    }
                }
            }
            newDevice
        }
        if (isNew) publish()
        return device
    }
    
    private fun resolveMacAddress(ipAddress: String): String? {
        return try {
            val arpCache = java.io.File("/proc/net/arp")
            if (!arpCache.exists()) return null
            arpCache.readLines().forEach { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 4 && parts[0] == ipAddress) {
                    val mac = parts[3]
                    if (mac.matches(Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"))) return mac
                }
            }
            null
        } catch (e: Exception) { null }
    }

    fun updateStats(ipAddress: String, bytes: Long, isInbound: Boolean) {
        devices[ipAddress]?.let { device ->
            if (isInbound) device.bytesIn += bytes else device.bytesOut += bytes
            device.lastSeenTimestamp = System.currentTimeMillis()
        }
    }

    fun setPriority(ipAddress: String, priority: TrafficClass) {
        devices[ipAddress]?.let { device ->
            device.priorityClass = priority
            device.macAddress?.let { mac ->
                scope.launch { persistPriority(mac, priority) }
            }
            publish()
        }
    }

    fun getAll(): List<ConnectedDevice> = devices.values.toList()
    fun isHotspotEnabled(): Boolean = hotspotManager.isHotspotEnabled()
    fun getHotspotSubnet(): String? = hotspotManager.getHotspotSubnet()

    fun resetAllPriorities() {
        devices.values.forEach { it.priorityClass = TrafficClass.MEDIUM }
        scope.launch { context.dataStore.edit { it.clear() } }
        publish()
    }

    private fun publish() {
        val filteredDevices = devices.values.filter { shouldShowDevice(it.ipAddress) }
        _devicesFlow.value = filteredDevices
    }
    
    private fun shouldShowDevice(ipAddress: String): Boolean {
        if (ipAddress.startsWith("10.0.0.") || ipAddress.startsWith("fd00::")) return false
        if (ipAddress.startsWith("127.") || ipAddress == "::1") return false
        if (ipAddress.startsWith("255.") || ipAddress.startsWith("224.")) return false
        return ipAddress.startsWith("192.168.") || ipAddress.startsWith("172.") || ipAddress.startsWith("10.")
    }

    private fun startTimeoutChecker() {
        timeoutJob = scope.launch {
            while (true) {
                delay(30000L) // Increase to 30s
                val now = System.currentTimeMillis()
                val expired = devices.entries
                    .filter { now - it.value.lastSeenTimestamp > INACTIVITY_TIMEOUT_MS }
                    .map { it.key }
                if (expired.isNotEmpty()) {
                    expired.forEach { devices.remove(it) }
                    publish()
                }
                devices.values.forEach { device ->
                    device.activeFlows.entries.removeIf { now - it.value.lastSeen > 30_000L }
                }
            }
        }
    }

    private fun startThroughputSampler() {
        throughputJob = scope.launch {
            while (true) {
                delay(3000L)
                devices.values.forEach { device ->
                    val prev = byteSamples[device.ipAddress] ?: 0L
                    val current = device.bytesIn + device.bytesOut
                    device.currentThroughputBps = (current - prev) / 3
                    byteSamples[device.ipAddress] = current
                }
                publish()
            }
        }
    }

    private suspend fun persistPriority(mac: String, priority: TrafficClass) {
        val key = stringPreferencesKey("priority_$mac")
        context.dataStore.edit { it[key] = priority.name }
    }

    private suspend fun loadPersistedPriorities() {
        val prefs = context.dataStore.data.first()
        prefs.asMap().forEach { (key, value) ->
            if (key.name.startsWith("priority_") && value is String) {
                val mac = key.name.removePrefix("priority_")
                val priority = runCatching { TrafficClass.valueOf(value) }.getOrNull() ?: return@forEach
                devices.values.find { it.macAddress == mac }?.priorityClass = priority
            }
        }
    }
}
