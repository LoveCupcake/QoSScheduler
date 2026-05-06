package com.qos.scheduler

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qos.scheduler.ui.MainViewModel
import com.qos.scheduler.ui.screens.AppDetailScreen
import com.qos.scheduler.ui.screens.DashboardScreen
import com.qos.scheduler.ui.screens.SettingsScreen
import com.qos.scheduler.ui.theme.QoSSchedulerTheme

sealed class Screen {
    data object Dashboard : Screen()
    data class AppDetail(val app: com.qos.scheduler.model.AppTraffic) : Screen()
    data object Settings : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) viewModel.startScheduler()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QoSSchedulerTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

                when (val screen = currentScreen) {
                    Screen.Dashboard -> {
                        DashboardScreen(
                            uiState = uiState,
                            onToggleScheduler = {
                                if (uiState.isRunning) viewModel.stopScheduler()
                                else requestVpnAndStart()
                            },
                            onAppClick = { currentScreen = Screen.AppDetail(it) },
                            onSettingsClick = { currentScreen = Screen.Settings }
                        )
                    }
                    is Screen.AppDetail -> {
                        // Find the latest state of this app from the uiState
                        val currentApp = uiState.apps.find { it.uid == screen.app.uid } ?: screen.app
                        AppDetailScreen(
                            app = currentApp,
                            runtimeMode = uiState.runtimeMode,
                            qosEnforced = uiState.relayHealth.qosEnforced,
                            onPriorityChanged = { priority ->
                                viewModel.onAppPriorityChanged(screen.app.uid, priority)
                            },
                            onBack = { currentScreen = Screen.Dashboard }
                        )
                    }
                    Screen.Settings -> {
                        SettingsScreen(
                            runtimeMode = uiState.runtimeMode,
                            relayHealth = uiState.relayHealth,
                            currentUplinkMbps = uiState.uplinkMbps,
                            onModeChanged = { viewModel.setRuntimeMode(it) },
                            onUplinkChanged = { viewModel.setUplinkMbps(it) },
                            onResetPriorities = { viewModel.resetAllPriorities() },
                            onBack = { currentScreen = Screen.Dashboard }
                        )
                    }
                }
            }
        }
    }

    private fun requestVpnAndStart() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            viewModel.startScheduler()
        }
    }
}
