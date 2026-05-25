package com.qos.scheduler

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qos.scheduler.ui.MainViewModel
import com.qos.scheduler.ui.screens.AppDetailScreen
import com.qos.scheduler.ui.screens.DashboardScreen
import com.qos.scheduler.ui.screens.ServerConnectScreen
import com.qos.scheduler.ui.screens.SettingsScreen
import com.qos.scheduler.ui.theme.QoSSchedulerTheme

sealed class Screen {
    data object Dashboard : Screen()
    data class AppDetail(val app: com.qos.scheduler.model.AppTraffic) : Screen()
    data object Settings : Screen()
    data object ServerConnect : Screen()
}

/** Returns true if the physical back button should navigate up rather than exit the app. */
private fun Screen.canGoBack(): Boolean = this != Screen.Dashboard

/** The screen to navigate to when back is pressed. */
private fun Screen.backDestination(): Screen = when (this) {
    is Screen.AppDetail -> Screen.Dashboard
    Screen.Settings     -> Screen.Dashboard
    Screen.ServerConnect -> Screen.Settings
    Screen.Dashboard    -> Screen.Dashboard
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

                // [V] Handle physical back button — navigate up instead of exiting
                BackHandler(enabled = currentScreen.canGoBack()) {
                    currentScreen = currentScreen.backDestination()
                }

                // [W] Animated screen transitions — slide in/out horizontally
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val goingDeeper = targetState != Screen.Dashboard &&
                                targetState != Screen.Settings
                        if (goingDeeper) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it / 3 } + fadeOut())
                        }
                    },
                    label = "ScreenTransition"
                ) { screen ->
                    when (screen) {
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
                                onServerConnectClick = { currentScreen = Screen.ServerConnect },
                                onBack = { currentScreen = Screen.Dashboard }
                            )
                        }
                        Screen.ServerConnect -> {
                            ServerConnectScreen(
                                currentServerUrl = uiState.serverUrl,
                                isConnected = uiState.isConnectedToServer,
                                syncStatusMessage = uiState.syncStatusMessage,
                                onSaveServerUrl = { viewModel.saveServerUrl(it) },
                                onSync = { viewModel.syncFromServer() },
                                onBack = { currentScreen = Screen.Settings }
                            )
                        }
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
