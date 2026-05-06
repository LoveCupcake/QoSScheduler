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
    val isTransitioning: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val qosApp = app as QosApplication
    private val _uplinkMbps = MutableStateFlow(100f)
    private val _hotspotState = MutableStateFlow(Pair(false, null as String?))
    private val _targetMode = MutableStateFlow(RelayRuntimeMode.MONITOR)
    private val _isTransitioning = MutableStateFlow(false)
    
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
            _isTransitioning
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
        
        // Auto-clear transitioning once service state changes have propagated
        // Also clear transitioning if we are somehow stuck but isRunning hasn't become true yet
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
            isMonitorOnlyMode = relayHealth.mode == RelayRuntimeMode.MONITOR
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

    fun resetAllPriorities() {
        viewModelScope.launch {
            QosVpnService.getInstance()?.resetAllPriorities()
        }
    }
}
