package com.qos.scheduler.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qos.scheduler.QosApplication
import com.qos.scheduler.model.ConnectedDevice
import com.qos.scheduler.model.TrafficClass
import com.qos.scheduler.service.RelayHealthSnapshot
import com.qos.scheduler.service.RelayRuntimeMode
import com.qos.scheduler.service.QosVpnService
import com.qos.scheduler.sync.CloudSyncManager
import com.qos.scheduler.sync.SyncResult
import com.qos.scheduler.sync.TelemetryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val isRunning: Boolean = false,
    val devices: List<ConnectedDevice> = emptyList(),
    val apps: List<com.qos.scheduler.model.AppTraffic> = emptyList(),
    val uplinkMbps: Float = 100f,
    val isHotspotEnabled: Boolean = false,
    val hotspotSubnet: String? = null,
    val relayHealth: RelayHealthSnapshot = RelayHealthSnapshot(),
    val runtimeMode: RelayRuntimeMode = RelayRuntimeMode.MONITOR,
    val isMonitorOnlyMode: Boolean = true,
    val isTransitioning: Boolean = false,
    // ── Cloud Server ──
    val serverUrl: String = "",
    val isConnectedToServer: Boolean = false,
    val syncStatusMessage: String = ""
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val qosApp = app as QosApplication
    private val _uplinkMbps = MutableStateFlow(100f)
    private val _hotspotState = MutableStateFlow(Pair(false, null as String?))
    private val _targetMode = MutableStateFlow(RelayRuntimeMode.MONITOR)
    private val _isTransitioning = MutableStateFlow(false)
    private val _isConnectedToServer = MutableStateFlow(false)
    private val _syncStatusMessage = MutableStateFlow("")
    
    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isActive) {
                val service = QosVpnService.getInstance()
                if (service != null) {
                    try {
                        val enabled = service.isHotspotEnabled()
                        val subnet = service.getHotspotSubnet()
                        _hotspotState.value = Pair(enabled, subnet)
                    } catch (e: Exception) {
                        // Ignore service-not-ready errors
                    }
                }
                kotlinx.coroutines.delay(5000) // Increase delay to 5s for better emulator performance
            }
        }

        // ── Background loop: Push Telemetry every 3 seconds ──
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isActive) {
                val url = qosApp.getServerUrl()
                
                // Only send if URL is set, server is connected AND QoS is running
                if (url.isNotBlank() && _isConnectedToServer.value && uiState.value.isRunning) {
                    val deviceId = android.provider.Settings.Secure.getString(
                        getApplication<android.app.Application>().contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    )
                    
                    val currentApps = qosApp.appsFlow.value
                    if (currentApps.isNotEmpty()) {
                        val telemetry = currentApps.map { app ->
                            // Calculate TCP/UDP flows
                            var tcpCount = 0
                            var udpCount = 0
                            app.activeFlows.values.forEach { flow ->
                                if (flow.key.protocol == com.qos.scheduler.model.Protocol.TCP) tcpCount++
                                if (flow.key.protocol == com.qos.scheduler.model.Protocol.UDP) udpCount++
                            }
                            
                            TelemetryEntry(
                                packageName = app.packageName,
                                appName     = app.appName,
                                priority    = app.priorityClass.name,
                                bytesIn     = app.bytesIn, 
                                bytesOut    = app.bytesOut,
                                requestedBps = app.currentRequestedBps,
                                allowedBps   = app.currentAllowedBps,
                                droppedPkts  = app.qosDroppedPackets,
                                tcpFlows     = tcpCount,
                                udpFlows     = udpCount
                            )
                        }
                        CloudSyncManager.pushTelemetry(url, deviceId, telemetry)
                    }
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }
    
    val uiState: StateFlow<UiState> = combine(
        listOf(
            qosApp.isServiceRunning,
            qosApp.devicesFlow,
            qosApp.appsFlow,
            _uplinkMbps,
            _hotspotState,
            qosApp.relayHealth,
            _targetMode,
            _isTransitioning,
            qosApp.serverUrlFlow,
            _isConnectedToServer,
            _syncStatusMessage
        )
    ) { args ->
        val isRunning = args[0] as Boolean
        val devices = args[1] as List<ConnectedDevice>
        val apps = args[2] as List<com.qos.scheduler.model.AppTraffic>
        val uplink = args[3] as Float
        val hotspotState = args[4] as Pair<Boolean, String?>
        val relayHealth = args[5] as RelayHealthSnapshot
        val targetMode = args[6] as RelayRuntimeMode
        val transitioning = args[7] as Boolean
        val serverUrl = args[8] as String
        val isConnected = args[9] as Boolean
        val syncMsg = args[10] as String
        
        if (transitioning) {
            val wasRunning = uiState.value?.isRunning ?: false
            if (isRunning != wasRunning) {
                _isTransitioning.value = false
            }
        }
        
        UiState(
            isRunning = isRunning,
            isTransitioning = transitioning,
            devices = devices,
            apps = apps,
            uplinkMbps = uplink,
            isHotspotEnabled = hotspotState.first,
            hotspotSubnet = hotspotState.second,
            relayHealth = relayHealth,
            runtimeMode = if (isRunning) relayHealth.mode else targetMode,
            isMonitorOnlyMode = relayHealth.mode == RelayRuntimeMode.MONITOR,
            serverUrl = serverUrl,
            isConnectedToServer = isConnected,
            syncStatusMessage = syncMsg
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    fun startScheduler() {
        _isTransitioning.value = true
        val intent = Intent(getApplication(), QosVpnService::class.java).apply {
            action = QosVpnService.ACTION_START
            putExtra(QosVpnService.EXTRA_UPLINK_BPS, (_uplinkMbps.value * 1_000_000).toLong())
            putExtra(QosVpnService.EXTRA_INITIAL_MODE, _targetMode.value.name)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun stopScheduler() {
        _isTransitioning.value = true
        val intent = Intent(getApplication(), QosVpnService::class.java).apply {
            action = QosVpnService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    fun setUplinkMbps(mbps: Float) {
        _uplinkMbps.value = mbps
        viewModelScope.launch {
            QosVpnService.getInstance()?.updateUplinkBps((mbps * 1_000_000).toLong())
        }
    }

    fun setRuntimeMode(mode: RelayRuntimeMode) {
        _targetMode.value = mode
        viewModelScope.launch {
            QosVpnService.getInstance()?.let { service ->
                service.setRuntimeMode(mode)
            }
        }
    }

    fun onPriorityChanged(ipAddress: String, priority: TrafficClass) {
        viewModelScope.launch {
            QosVpnService.getInstance()?.updateDevicePriority(ipAddress, priority)
        }
    }
    
    fun onAppPriorityChanged(uid: Int, priority: TrafficClass) {
        viewModelScope.launch {
            QosVpnService.getInstance()?.updateAppPriority(uid, priority)
        }
    }

    fun onAppManualCapChanged(uid: Int, maxBps: Long) {
        viewModelScope.launch {
            QosVpnService.getInstance()?.updateAppManualCap(uid, maxBps)
        }
    }

    fun resetAllPriorities() {
        viewModelScope.launch {
            QosVpnService.getInstance()?.resetAllPriorities()
        }
    }

    // ── Cloud Server ──────────────────────────────────────────────────────

    fun saveServerUrl(url: String) {
        qosApp.saveServerUrl(url)
    }

    /**
     * Ping server, register device, fetch policies, apply them.
     * Non-blocking, safe — errors update syncStatusMessage but never crash.
     */
    fun syncFromServer() {
        val url = qosApp.getServerUrl().takeIf { it.isNotBlank() } ?: run {
            _syncStatusMessage.value = "⚠️ No server address configured"
            return
        }
        viewModelScope.launch {
            _syncStatusMessage.value = "⏳ Connecting…"
            val reachable = CloudSyncManager.ping(url)
            if (!reachable) {
                _isConnectedToServer.value = false
                _syncStatusMessage.value = "❌ Cannot reach server"
                return@launch
            }

            // Register this device
            val deviceId = android.provider.Settings.Secure.getString(
                getApplication<android.app.Application>().contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            val model = android.os.Build.MODEL
            val name  = android.os.Build.MANUFACTURER + " " + model
            CloudSyncManager.registerDevice(url, deviceId, name, model)

            // Fetch and apply policies
            when (val result = CloudSyncManager.fetchPolicies(url, deviceId)) {
                is SyncResult.Success -> {
                    _isConnectedToServer.value = true
                    result.policies.forEach { policy ->
                        // Match by package name against tracked apps
                        val app = qosApp.appsFlow.value.find { it.packageName == policy.packageName }
                        if (app != null) {
                            onAppPriorityChanged(app.uid, policy.priority)
                            onAppManualCapChanged(app.uid, policy.maxBps)
                        }
                    }
                    _syncStatusMessage.value = "✅ Synced ${result.policies.size} policies"

                    // Push telemetry
                    val telemetry = qosApp.appsFlow.value.map { app ->
                        // Calculate TCP/UDP flows
                        var tcpCount = 0
                        var udpCount = 0
                        app.activeFlows.values.forEach { flow ->
                            if (flow.key.protocol == com.qos.scheduler.model.Protocol.TCP) tcpCount++
                            if (flow.key.protocol == com.qos.scheduler.model.Protocol.UDP) udpCount++
                        }
                        
                        TelemetryEntry(
                            packageName = app.packageName,
                            appName     = app.appName,
                            priority    = app.priorityClass.name,
                            bytesIn     = app.bytesIn, 
                            bytesOut    = app.bytesOut,
                            requestedBps = app.currentRequestedBps,
                            allowedBps   = app.currentAllowedBps,
                            droppedPkts  = app.qosDroppedPackets,
                            tcpFlows     = tcpCount,
                            udpFlows     = udpCount
                        )
                    }
                    CloudSyncManager.pushTelemetry(url, deviceId, telemetry)
                }
                is SyncResult.Error -> {
                    _isConnectedToServer.value = false
                    _syncStatusMessage.value = "❌ Error: ${result.message}"
                }
            }
        }
    }
}
