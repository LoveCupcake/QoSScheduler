package com.qos.scheduler

import android.app.Application
import com.qos.scheduler.model.ConnectedDevice
import com.qos.scheduler.service.RelayHealthSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-level singleton to share device state between Service and UI.
 * The VpnService updates this, and the ViewModel observes it.
 */
class QosApplication : Application() {
    
    private val _devicesFlow = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val devicesFlow: StateFlow<List<ConnectedDevice>> = _devicesFlow.asStateFlow()
    
    private val _appsFlow = MutableStateFlow<List<com.qos.scheduler.model.AppTraffic>>(emptyList())
    val appsFlow: StateFlow<List<com.qos.scheduler.model.AppTraffic>> = _appsFlow.asStateFlow()
    
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _relayHealth = MutableStateFlow(RelayHealthSnapshot())
    val relayHealth: StateFlow<RelayHealthSnapshot> = _relayHealth.asStateFlow()
    
    fun updateDevices(devices: List<ConnectedDevice>) {
        _devicesFlow.value = devices
    }
    
    fun updateApps(apps: List<com.qos.scheduler.model.AppTraffic>) {
        _appsFlow.value = apps
    }
    
    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun updateRelayHealth(snapshot: RelayHealthSnapshot) {
        _relayHealth.value = snapshot
    }
    
    companion object {
        private var instance: QosApplication? = null
        fun getInstance(): QosApplication = instance!!
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
