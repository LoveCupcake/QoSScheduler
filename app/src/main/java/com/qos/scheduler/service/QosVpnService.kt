package com.qos.scheduler.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.qos.scheduler.MainActivity
import com.qos.scheduler.QosApplication
import com.qos.scheduler.R
import com.qos.scheduler.classifier.DpiClassifier
import com.qos.scheduler.registry.DeviceRegistry
import com.qos.scheduler.scheduler.BandwidthScheduler
import com.qos.scheduler.service.dataplane.DataPlaneProcessor
import com.qos.scheduler.service.relay.TcpRelayManager
import com.qos.scheduler.service.relay.UdpRelayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import com.qos.scheduler.model.*

class QosVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.qos.scheduler.START"
        const val ACTION_STOP  = "com.qos.scheduler.STOP"
        const val EXTRA_UPLINK_BPS = "uplink_bps"
        const val EXTRA_INITIAL_MODE = "initial_mode"
        
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "qos_scheduler_channel"
        private const val HOST_BUCKET_KEY = "__host__"
        
        @Volatile
        private var instance: QosVpnService? = null
        fun getInstance(): QosVpnService? = instance

        // PERSISTENT DATA: Stored in companion object to survive service recreation
        private val appTraffic = java.util.concurrent.ConcurrentHashMap<Int, com.qos.scheduler.model.AppTraffic>()
        private val lastAppTotals = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        private val lastAppDropped = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        private val lastAppAllowed = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    }

    @Volatile private var tunInterface: ParcelFileDescriptor? = null
    @Volatile private var tunInputStream: FileInputStream? = null
    @Volatile private var tunOutputStream: FileOutputStream? = null
    private val tunnelMutex = kotlinx.coroutines.sync.Mutex()
    private val scheduler  = BandwidthScheduler()
    private lateinit var hostStateRegistry: DeviceRegistry
    private val classifier = DpiClassifier()
    private val appResolver by lazy { com.qos.scheduler.util.AppResolver(this) }
    private val dataPlaneProcessor by lazy {
        DataPlaneProcessor(
            classifier = classifier,
            scheduler = scheduler,
            appResolver = appResolver,
            appTraffic = appTraffic,
            hostBucketKey = HOST_BUCKET_KEY
        )
    }

    private val serviceJob = SupervisorJob()
    // Significantly reduce parallelism for better stability on emulators
    private val serviceDispatcher = Dispatchers.IO.limitedParallelism(8)
    private val scope = CoroutineScope(serviceDispatcher + serviceJob)
    private var tunReadJob: Job? = null
    private var packetProcessJob: Job? = null
    private var hostStateObserverJob: Job? = null
    private var appObserverJob: Job? = null
    private var socketRefreshJob: Job? = null
    private var appFlowCleanupJob: Job? = null
    private var healthMonitorJob: Job? = null
    private val diagnosticTool by lazy { com.qos.scheduler.util.DiagnosticTool(appTraffic) }
    
    private var tcpRelayManager: TcpRelayManager? = null
    private var udpRelayManager: UdpRelayManager? = null

    // FIX: @Volatile ensures cross-coroutine/cross-thread visibility without a lock
    @Volatile private var currentMode = RelayRuntimeMode.MONITOR
    @Volatile private var healthSnapshot = RelayHealthSnapshot()
    
    // Telemetry counters — written from multiple coroutines, must be atomic/volatile
    @Volatile private var tcpTotalFlows = 0
    @Volatile private var tcpErrorRate = 0.0
    @Volatile private var dnsErrorRate = 0.0
    private val packetsInModeCount = java.util.concurrent.atomic.AtomicLong(0L)
    private val droppedPacketsCount = java.util.concurrent.atomic.AtomicLong(0L)

    fun getHealthSnapshot(): RelayHealthSnapshot = healthSnapshot

    fun setRuntimeMode(mode: RelayRuntimeMode) {
        if (currentMode != mode) {
            android.util.Log.i("QosVpnService", "Switching mode from $currentMode to $mode")
            currentMode = mode
            resetTelemetry()
            
            // Re-establish tunnel on background scope - never block main thread
            scope.launch { startTunnel() }
            
            // FIX: refresh foreground notification to reflect new mode
            startForeground(NOTIFICATION_ID, buildNotification())
            
            // Notify UI
            updateHealthSnapshot(RelayFallbackReason.NONE)
        }
    }

    private fun resetTelemetry() {
        packetsInModeCount.set(0L)
        droppedPacketsCount.set(0L)
        tcpErrorRate = 0.0
        dnsErrorRate = 0.0
    }

    
    private class PacketPool {
        private val pool = java.util.concurrent.ArrayBlockingQueue<ByteArray>(2048)
        fun acquire(): ByteArray = pool.poll() ?: ByteArray(32767)
        fun release(bytes: ByteArray) { pool.offer(bytes) }
    }
    private val packetPool = PacketPool()

    private data class TunPacket(
        val bytes: ByteArray,
        val length: Int
    )
    private val packetChannel = Channel<TunPacket>(capacity = 4096)
    
    // Dedicated channel for writing to TUN to prevent thread exhaustion
    private val tunWriteChannel = Channel<ByteArray>(capacity = 4096)
    private var tunWriteJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize DeviceRegistry on background thread to avoid blocking main thread
        hostStateRegistry = DeviceRegistry(this)
        initializeManagers()
    }

    private fun initializeManagers() {
        tcpRelayManager?.close()
        udpRelayManager?.close()

        val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        
        val getRealNetwork = {
            val networks = cm.allNetworks
            networks.find { network ->
                val caps = cm.getNetworkCapabilities(network)
                caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) && 
                          !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
            } ?: cm.activeNetwork
        }

        val protectSocket: (java.net.Socket) -> Boolean = { socket -> 
            var success = false
            val network = getRealNetwork()
            
            try {
                if (network != null) {
                    network.bindSocket(socket)
                    success = true
                }
            } catch (e: Exception) {
                android.util.Log.w("QosVpnService", "bindSocket failed: ${e.message}")
            }
            
            if (!success) {
                try {
                    // FIX: close PFD after extracting fd to avoid file descriptor leak
                    val pfd = android.os.ParcelFileDescriptor.fromSocket(socket)
                    success = protect(pfd.fd)
                    runCatching { pfd.close() }
                } catch (e: Exception) {
                    android.util.Log.e("QosVpnService", "protect(fd) failed: ${e.message}")
                    success = protect(socket)
                }
            }
            success
        }

        val protectUdpSocket: (java.net.DatagramSocket) -> Boolean = { socket ->
            var success = false
            val network = getRealNetwork()
            
            try {
                if (network != null) {
                    network.bindSocket(socket)
                    success = true
                }
            } catch (e: Exception) {
                android.util.Log.w("QosVpnService", "bindSocket failed (UDP): ${e.message}")
            }
            
            if (!success) {
                try {
                    // FIX: close PFD after extracting fd to avoid file descriptor leak
                    val pfd = android.os.ParcelFileDescriptor.fromDatagramSocket(socket)
                    success = protect(pfd.fd)
                    runCatching { pfd.close() }
                } catch (e: Exception) {
                    android.util.Log.e("QosVpnService", "protect(fd) failed (UDP): ${e.message}")
                    success = protect(socket)
                }
            }
            success
        }

        tcpRelayManager = TcpRelayManager(
            appResolver = appResolver,
            protectSocket = protectSocket,
            emitToTun = { packet -> emitPacketToTun(packet) },
            onFlowCountChanged = { count -> tcpTotalFlows = count },
            onError = { tcpErrorRate = (tcpErrorRate + 0.1).coerceAtMost(1.0) }
        )

        udpRelayManager = UdpRelayManager(
            protectSocket = protectUdpSocket,
            emitToTun = { packet -> emitPacketToTun(packet) },
            onFlowCountChanged = { /* Handled in health check */ },
            onError = { address, port -> incrementDnsError(address, port) }
        )
    }

    private fun incrementDnsError(address: String, port: Int) {
        // Only increment if we are not in the middle of a restart/shutdown
        if (tunInterface != null) {
            // Ignore multicast/broadcast errors as they are expected to fail occasionally
            if (address.startsWith("ff") || address.startsWith("224.") || address == "255.255.255.255") {
                return
            }
            
            // Only count errors for actual DNS ports (53, 853)
            if (port == 53 || port == 853) {
                dnsErrorRate = (dnsErrorRate * 0.9 + 0.1).coerceAtMost(1.0)
                android.util.Log.w("QosVpnService", "DNS Error detected to $address:$port. Rate now: $dnsErrorRate")
            } else {
                // For other UDP ports, we just log it but don't break the QoS
                android.util.Log.v("QosVpnService", "UDP Error to $address:$port (Non-DNS, ignoring for health check)")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    stopTunnel()
                    hostStateRegistry.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY 
            }
            else -> {
                val uplinkBps = intent?.getLongExtra(EXTRA_UPLINK_BPS, 100_000_000L) ?: 100_000_000L
                scheduler.uplinkBps = uplinkBps
                
                // If tunnel is already running, just update parameters and don't reset data
                if (tunInterface != null) {
                    android.util.Log.d("QosVpnService", "Service already running, updating parameters only")
                    return START_STICKY
                }
                
                val initialModeName = intent?.getStringExtra(EXTRA_INITIAL_MODE)
                currentMode = initialModeName?.let { RelayRuntimeMode.valueOf(it) } ?: RelayRuntimeMode.MONITOR
                
                healthSnapshot = RelayHealthSnapshot(
                    mode = currentMode,
                    fallbackReason = if (currentMode == RelayRuntimeMode.MONITOR) 
                        RelayFallbackReason.DEFAULT_MONITOR_MODE else RelayFallbackReason.NONE
                )                // Always restart or re-establish tunnel to ensure mode/scope changes take effect
                scope.launch {
                    if (tunInterface != null) {
                        stopTunnel()
                        delay(200)
                    }
                    startTunnel()
                    observeRuntimeState()
                    startHealthMonitor()
                }
                
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, buildNotification())
                hostStateRegistry.start()
                QosApplication.getInstance().setServiceRunning(true)
            }
        }
        return START_STICKY
    }

    private fun startHealthMonitor() {
        healthMonitorJob?.cancel()
        healthMonitorJob = scope.launch {
            while (isActive) {
                delay(10000) // Increase delay to 10s for emulator stability
                performHealthCheck()
            }
        }
    }

    private fun performHealthCheck() {
        val activeFlows = (tcpRelayManager?.getFlowCount() ?: 0)
        
        // Decay error rates slowly
        dnsErrorRate *= 0.98 // Slower decay
        tcpErrorRate *= 0.98
        
        updateHealthSnapshot(healthSnapshot.fallbackReason)

        // Only fallback if we have significant traffic and very high error rates
        if (currentMode != RelayRuntimeMode.MONITOR && packetsInModeCount.get() > 100) {
            val reason = when {
                dnsErrorRate > 0.7 -> RelayFallbackReason.DNS_ERROR_RATE // Increased threshold
                activeFlows > 2000 -> RelayFallbackReason.QUEUE_PRESSURE 
                else -> null
            }
            
            if (reason != null) {
                fallbackToMonitor(reason)
            }
        }
    }

    private fun updateHealthSnapshot(reason: RelayFallbackReason) {
        val newSnapshot = healthSnapshot.copy(
            mode = currentMode,
            activeFlowCount = (tcpRelayManager?.getFlowCount() ?: 0),
            dnsErrorRate = dnsErrorRate,
            relayErrorRate = tcpErrorRate,
            fallbackReason = reason,
            qosEnforced = currentMode != RelayRuntimeMode.MONITOR
        )
        
        // Only notify app if state has meaningfully changed to reduce UI pressure
        if (newSnapshot.activeFlowCount != healthSnapshot.activeFlowCount || 
            newSnapshot.mode != healthSnapshot.mode || 
            newSnapshot.fallbackReason != healthSnapshot.fallbackReason) {
            healthSnapshot = newSnapshot
            QosApplication.getInstance().updateRelayHealth(healthSnapshot)
        }
    }

    private fun fallbackToMonitor(reason: RelayFallbackReason) {
        android.util.Log.w("QosVpnService", "CRITICAL: Falling back to MONITOR mode. Reason: ${reason.userLabel()}")
        currentMode = RelayRuntimeMode.MONITOR
        // FIX: read snapshot once, increment fallbackCount atomically in a single assignment
        val current = healthSnapshot
        healthSnapshot = current.copy(
            mode = currentMode,
            fallbackReason = reason,
            fallbackCount = current.fallbackCount + 1,
            qosEnforced = false
        )
        QosApplication.getInstance().updateRelayHealth(healthSnapshot)
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch { startTunnel() }
    }

    override fun onRevoke() {
        // runBlocking is acceptable here — system is revoking VPN permission, not user interaction
        runBlocking { stopTunnel() }
        super.onRevoke()
    }

    override fun onDestroy() {
        // runBlocking is acceptable here — service is shutting down entirely
        runBlocking { stopTunnel() }
        hostStateRegistry.stop()
        serviceJob.cancel()
        tcpRelayManager?.close()
        tcpRelayManager = null
        udpRelayManager?.close()
        udpRelayManager = null
        QosApplication.getInstance().setServiceRunning(false)
        QosApplication.getInstance().updateDevices(emptyList())
        instance = null
        super.onDestroy()
    }

    private fun observeRuntimeState() {
        hostStateObserverJob?.cancel()
        appObserverJob?.cancel()
        appFlowCleanupJob?.cancel()

        hostStateObserverJob = scope.launch {
            hostStateRegistry.devicesFlow.collect { devices ->
                QosApplication.getInstance().updateDevices(devices)
            }
        }
        
        appObserverJob = scope.launch {
            while (isActive) {
                delay(1000) // 1s interval for PERFECT UX
                try {
                    val now = System.currentTimeMillis()
                    
                    // Calculate real throughput (BPS) for each app
                    appTraffic.values.forEach { app ->
                        // 1. Total bytes throughput (BPS)
                        val prev = lastAppTotals[app.uid] ?: 0L
                        val current = app.bytesIn + app.bytesOut
                        val bytesDiff = if (current >= prev) (current - prev) else 0L
                        app.currentThroughputBps = bytesDiff * 8 // Bytes/sec to Bits/sec
                        lastAppTotals[app.uid] = current
                        
                        // 2. QoS Packet Drops and Allowed
                        val prevDropped = lastAppDropped[app.uid] ?: 0L
                        val currentDropped = app.qosDroppedPackets
                        val droppedDiff = if (currentDropped >= prevDropped) (currentDropped - prevDropped) else 0L
                        lastAppDropped[app.uid] = currentDropped
                        
                        val prevAllowed = lastAppAllowed[app.uid] ?: 0L
                        val currentAllowed = app.qosAllowedPackets
                        val allowedDiff = if (currentAllowed >= prevAllowed) (currentAllowed - prevAllowed) else 0L
                        lastAppAllowed[app.uid] = currentAllowed
                        
                        // Approximate BPS based on packet counts (assuming avg packet size ~1000 bytes)
                        // This gives a nice smooth chart for requested vs allowed bandwidth
                        app.currentRequestedBps = (allowedDiff + droppedDiff) * 1000 * 8
                        app.currentAllowedBps = allowedDiff * 1000 * 8
                    }
                    
                    // 1. Run detailed diagnostic reporting
                    diagnosticTool.report()
                    
                    // 2. Cleanup stale apps (5 minutes inactivity)
                    appTraffic.entries.removeIf { (_, app) ->
                        now - app.lastSeenTimestamp > 300_000
                    }
                    lastAppTotals.entries.removeIf { !appTraffic.containsKey(it.key) }
                    lastAppDropped.entries.removeIf { !appTraffic.containsKey(it.key) }
                    lastAppAllowed.entries.removeIf { !appTraffic.containsKey(it.key) }
                    
                    // 3. Always get list and update UI (fixes the "stuck" issue)
                    val currentApps = appTraffic.values.toList()
                    scheduler.rebalanceWithApps(currentApps)
                    QosApplication.getInstance().updateApps(currentApps)
                } catch (e: Exception) {
                    android.util.Log.e("QosVpnService", "Error in app observer: ${e.message}")
                }
            }
        }
        
        appFlowCleanupJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                appTraffic.values.forEach { app ->
                    val iterator = app.activeFlows.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (now - entry.value.lastSeen > 30_000L) {
                            dataPlaneProcessor.clearFlow(entry.key)
                            iterator.remove()
                        }
                    }
                }
                delay(10000)
            }
        }
        
        // healthMonitorJob started elsewhere
    }
    
    private fun detectRunningApps() {
        // DISABLED: Too heavy for initial debugging
        return
    }

    fun updateAppPriority(uid: Int, priority: com.qos.scheduler.model.TrafficClass) {
        appTraffic[uid]?.let { app ->
            app.priorityClass = priority
            scheduler.rebalanceWithApps(appTraffic.values)
            QosApplication.getInstance().updateApps(appTraffic.values.toList())
        }
    }

    fun updateAppManualCap(uid: Int, maxBps: Long) {
        val key = com.qos.scheduler.scheduler.BandwidthScheduler.appBucketKey(uid)
        if (maxBps > 0) {
            scheduler.setManualCap(key, maxBps)
        } else {
            scheduler.removeManualCap(key)
        }
    }

    fun updateUplinkBps(uplinkBps: Long) {
        scheduler.uplinkBps = uplinkBps
        scheduler.rebalanceWithApps(appTraffic.values)
    }

    fun resetAllPriorities() {
        hostStateRegistry.resetAllPriorities()
        appTraffic.values.forEach { it.priorityClass = com.qos.scheduler.model.TrafficClass.MEDIUM }
        scheduler.rebalanceWithApps(appTraffic.values)
        QosApplication.getInstance().updateApps(appTraffic.values.toList())
    }
    
    fun isHotspotEnabled(): Boolean = hostStateRegistry.isHotspotEnabled()
    fun getHotspotSubnet(): String? = hostStateRegistry.getHotspotSubnet()
    fun updateDevicePriority(ip: String, priority: com.qos.scheduler.model.TrafficClass) = hostStateRegistry.setPriority(ip, priority)

    private suspend fun startTunnel() {
        tunnelMutex.withLock {
            stopTunnelInternal()
            delay(100)
            
            android.util.Log.d("QosVpnService", "Establishing tunnel in mode: $currentMode")
        
        // Removed aggressive early checkProtectCapability()
        // On newer Android versions (API 36), protect() might fail if called BEFORE builder.establish()
        // We already handle protect() failures gracefully inside the Relay Managers anyway.

        val builder = Builder()
            .setSession("QoS Scheduler")
            .addAddress("10.0.0.1", 32)
            .addAddress("fd00:716f:733a:7363::1", 64)
            .setMtu(1400)
        
        // Auto-detect DNS servers from system instead of hardcoding
        val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val linkProperties = activeNetwork?.let { cm.getLinkProperties(it) }
        
        val dnsServers = linkProperties?.dnsServers?.take(2) // Take first 2 DNS servers
        if (dnsServers != null && dnsServers.isNotEmpty()) {
            dnsServers.forEach { dns ->
                builder.addDnsServer(dns.hostAddress ?: dns.toString())
                android.util.Log.d("QosVpnService", "Using system DNS: ${dns.hostAddress}")
            }
        } else {
            // Fallback to public DNS if system DNS not available
            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("8.8.4.4")
            android.util.Log.d("QosVpnService", "Using fallback DNS: 8.8.8.8, 8.8.4.4")
        }
        
        builder.addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .setUnderlyingNetworks(null)
            .allowBypass()
            .allowFamily(android.system.OsConstants.AF_INET)
            .allowFamily(android.system.OsConstants.AF_INET6)
        
        try {
            if (currentMode == RelayRuntimeMode.MONITOR) {
                // Monitor mode: Capture ONLY our own app to avoid breaking host connectivity
                // (Without a relay, capturing all apps and writing back to TUN causes a routing loop)
                builder.addAllowedApplication(packageName)
                android.util.Log.i("QosVpnService", "VPN Scope: Restricted to controller app (Monitor Mode)")
            } else {
                // Relay mode: Explicitly include all apps to force traffic through VPN
                val pm = packageManager
                val installedApps = pm.getInstalledPackages(0)
                var includedCount = 0
                installedApps.forEach { app ->
                    val pkgName = app.packageName
                    if (pkgName != packageName) {
                        try {
                            builder.addAllowedApplication(pkgName)
                            includedCount++
                        } catch (e: Exception) {
                            // Some system packages are not addable — ignore silently
                        }
                    }
                }
                android.util.Log.i("QosVpnService", "VPN Scope: $includedCount apps included (Relay Mode)")
            }
        } catch (e: Exception) {
            android.util.Log.e("QosVpnService", "App scope error: ${e.message}")
        }

        initializeManagers() // Ensure fresh managers for the new mode

        tunInterface = builder.establish()
        if (tunInterface == null) {
            android.util.Log.e("QosVpnService", "Failed to establish TUN interface!")
            updateHealthSnapshot(RelayFallbackReason.TUN_INTERFACE_FAILED)
            stopSelf()
            return
        }

        tunInterface?.let { tun ->
            scheduler.ensureBucket(HOST_BUCKET_KEY)
            tunReadJob = scope.launch { runTunReadLoop(tun) }
            packetProcessJob = scope.launch { runPacketProcessLoop(tun) }
            tunWriteJob = scope.launch { runTunWriteLoop(tun) }
        } ?: run {
            android.util.Log.e("QosVpnService", "Failed to establish TUN interface!")
        }
        } // end mutex lock
    }

    private fun checkProtectCapability(): Boolean {
        return try {
            val testSocket = java.net.Socket()
            val tcpSuccess = protect(testSocket)
            testSocket.close()

            if (!tcpSuccess) {
                android.util.Log.e("QosVpnService", "FATAL: System refused to protect test TCP socket.")
                return false
            }

            val testUdp = java.net.DatagramSocket()
            val udpSuccess = protect(testUdp)
            testUdp.close()

            if (!udpSuccess) {
                android.util.Log.e("QosVpnService", "FATAL: System refused to protect test UDP socket.")
                return false
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("QosVpnService", "Error during protect self-check: ${e.message}")
            false
        }
    }

    private suspend fun stopTunnel() {
        tunnelMutex.withLock { stopTunnelInternal() }
    }
    
    private suspend fun stopTunnelInternal() {
        // Cancel jobs first
        val jobs = listOfNotNull(tunReadJob, packetProcessJob, tunWriteJob)
        jobs.forEach { it.cancel() }
        
        // CRITICAL: Close streams FIRST to unblock I/O operations
        // Blocking I/O operations like FileInputStream.read() do NOT respond to coroutine cancellation.
        // Closing the streams forces read/write to throw an IOException, unblocking the threads.
        runCatching { tunInputStream?.close() }
        tunInputStream = null
        runCatching { tunOutputStream?.close() }
        tunOutputStream = null
        
        // Then close TUN interface
        tunInterface?.close()
        tunInterface = null

        // Now safe to wait for jobs to terminate
        jobs.forEach { it.join() }
        
        // Drain channels
        packetChannel.close()
        while (packetChannel.tryReceive().isSuccess) {}
        while (tunWriteChannel.tryReceive().isSuccess) {}
        
        android.util.Log.d("QosVpnService", "Tunnel stopped completely")
    }

    private suspend fun runTunReadLoop(tun: ParcelFileDescriptor) {
        val inputStream = FileInputStream(tun.fileDescriptor)
        tunInputStream = inputStream
        
        android.util.Log.d("QosVpnService", "TUN Read Loop started")
        
        while (scope.isActive) {
            val buffer = packetPool.acquire()
            val length = try { 
                inputStream.read(buffer) 
            } catch (e: kotlinx.coroutines.CancellationException) {
                packetPool.release(buffer)
                throw e
            } catch (e: Exception) { 
                // Expected when stream is closed during shutdown
                if (scope.isActive && tunInterface != null) {
                    android.util.Log.w("QosVpnService", "TUN Read Loop stopped: ${e.message}")
                }
                packetPool.release(buffer)
                break 
            }
            if (length <= 0) {
                packetPool.release(buffer)
                continue
            }
            val packet = TunPacket(buffer, length)
            if (!packetChannel.trySend(packet).isSuccess) {
                packetPool.release(buffer)
            }
        }
        
        android.util.Log.d("QosVpnService", "TUN Read Loop exited")
    }

    private suspend fun runTunWriteLoop(tun: ParcelFileDescriptor) {
        val outputStream = FileOutputStream(tun.fileDescriptor)
        tunOutputStream = outputStream
        while (scope.isActive) {
            val packet = tunWriteChannel.receiveCatching().getOrNull() ?: break
            try {
                outputStream.write(packet)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore broken pipe errors
            }
        }
    }

    private suspend fun runPacketProcessLoop(tun: ParcelFileDescriptor) {
        android.util.Log.i("QosVpnService", "Packet Process Loop started")
        
        while (scope.isActive) {
            try {
                val packet = packetChannel.receiveCatching().getOrNull() ?: break
                try {
                    packetsInModeCount.incrementAndGet()

                    // FIX: Do NOT parse here — DataPlaneProcessor.process() already parses
                    // Parsing twice per packet was pure redundant CPU work.
                    // We only parse to decide relay vs forward AFTER the scheduler decision.

                    // All packets from tunReadLoop are OUTBOUND
                    val decision = dataPlaneProcessor.process(packet.bytes, packet.length, isOutbound = true)
                    if (decision.droppedByScheduler) {
                        droppedPacketsCount.incrementAndGet()
                        continue
                    }

                    val parsedPacket = decision.packet
                    if (currentMode == RelayRuntimeMode.MONITOR || parsedPacket == null) {
                        // Monitor mode: Just forward back to TUN (read-only observation)
                        tunWriteChannel.trySend(packet.bytes.copyOf(packet.length))
                    } else {
                        // RELAY MODE: Process through managers
                        var handled = false
                        if (parsedPacket.protocol == Protocol.TCP) {
                            handled = tcpRelayManager?.handlePacket(parsedPacket) ?: false
                        } else if (parsedPacket.protocol == Protocol.UDP) {
                            handled = udpRelayManager?.handlePacket(parsedPacket) ?: false
                        }
                        
                        if (!handled) {
                            // If not handled by relay (e.g. ICMP or unsupported), forward back to TUN
                            tunWriteChannel.trySend(packet.bytes.copyOf(packet.length))
                        }
                    }
                    
                } finally {
                    packetPool.release(packet.bytes)
                }
                
                // Yield to prevent CPU starvation on high-traffic loops
                kotlinx.coroutines.yield()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("QosVpnService", "Packet Process Loop Error", e)
                delay(100) 
            }
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val content = when(currentMode) {
            RelayRuntimeMode.MONITOR -> "Safely monitoring host apps"
            RelayRuntimeMode.RELAY_EXPERIMENTAL -> "Active QoS (Experimental)"
            RelayRuntimeMode.RELAY_STABLE -> "Active QoS (Stable)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QoS Scheduler")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "QoS Scheduler", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun emitPacketToTun(packet: ByteArray) {
        // Track inbound traffic (from internet to apps)
        val decision = dataPlaneProcessor.process(packet, packet.size, isOutbound = false)
        
        // CRITICAL: Apply Token Bucket / WFQ decision to inbound traffic
        // If we drop inbound packets, TCP Congestion Control will naturally slow down the sender,
        // effectively throttling the download speed.
        if (decision.droppedByScheduler) {
            droppedPacketsCount.incrementAndGet()
            return // DROP PACKET
        }
        
        // Push to channel instead of blocking thread!
        // We use trySend; if the queue is full (4096 packets), we drop the packet.
        // TCP will retransmit. This completely prevents deadlocks and thread exhaustion.
        val result = tunWriteChannel.trySend(packet)
        if (!result.isSuccess) {
            android.util.Log.w("QosVpnService", "TUN write queue is FULL! Dropping inbound packet.")
        }
    }
}
