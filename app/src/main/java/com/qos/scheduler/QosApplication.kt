package com.qos.scheduler

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
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

    // ── Server URL ──────────────────────────────────────────────────────────
    private lateinit var prefs: SharedPreferences

    /** Persisted server URL, e.g. "http://192.168.10.115:3000" */
    fun getServerUrl(): String =
        prefs.getString(PREF_SERVER_URL, "") ?: ""

    fun saveServerUrl(url: String) {
        prefs.edit().putString(PREF_SERVER_URL, url.trimEnd('/')).apply()
        _serverUrlFlow.value = url.trimEnd('/')
    }

    private val _serverUrlFlow = MutableStateFlow("")
    val serverUrlFlow: StateFlow<String> = _serverUrlFlow.asStateFlow()
    
    companion object {
        private var instance: QosApplication? = null
        fun getInstance(): QosApplication = instance!!
        private const val PREF_SERVER_URL = "server_url"
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences("qos_prefs", Context.MODE_PRIVATE)
        _serverUrlFlow.value = getServerUrl()
    }
}
