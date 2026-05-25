package com.qos.scheduler.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qos.scheduler.model.TrafficClass
import com.qos.scheduler.ui.UiState
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    uiState: UiState,
    onToggleScheduler: () -> Unit,
    onAppClick: (com.qos.scheduler.model.AppTraffic) -> Unit,
    onSettingsClick: () -> Unit
) {
    // [D] Delay "waiting" text so it doesn't flash immediately after start
    var showEmptyHint by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRunning, uiState.isTransitioning) {
        if (uiState.isRunning && !uiState.isTransitioning) {
            delay(2500)
            showEmptyHint = true
        } else {
            showEmptyHint = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // [A] Header — title + settings icon only; Switch is separate below
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "QoS Scheduler",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Emulator / Local Test Banner
        val isEmulator = android.os.Build.PRODUCT.contains("sdk") ||
                android.os.Build.MODEL.contains("Emulator")
        if (isEmulator) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "🛠️ Emulator Mode:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        "Ready to test QoS logic for App Host.",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Mode status card + Switch in same card
        Surface(
            color = if (uiState.isMonitorOnlyMode)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Mode: ${uiState.runtimeMode.displayName()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!uiState.isMonitorOnlyMode) {
                                Surface(
                                    color = Color(0xFF4CAF50),
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        "ACTIVE",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (uiState.isMonitorOnlyMode)
                                "Monitoring app traffic on host. Switch to Relay to enforce bandwidth priorities."
                            else
                                "QoS is enforcing scheduling for local apps based on assigned priorities.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // [A] Switch moved inside status card, clearly associated with on/off
                    Switch(
                        checked = uiState.isRunning,
                        onCheckedChange = { onToggleScheduler() },
                        enabled = !uiState.isTransitioning
                    )
                }

                // Fallback reason
                if (uiState.relayHealth.fallbackReason != com.qos.scheduler.service.RelayFallbackReason.NONE &&
                    uiState.relayHealth.fallbackReason != com.qos.scheduler.service.RelayFallbackReason.DEFAULT_MONITOR_MODE
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Auto-fallback: ${uiState.relayHealth.fallbackReason.userLabel()}",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // [B] Empty/not-started state with clear call-to-action
        if (!uiState.isRunning && !uiState.isTransitioning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📡", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "QoS Scheduler is stopped",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Toggle the switch above to start monitoring\nand controlling per-app bandwidth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return
        }

        // [D] Transitioning spinner
        if (uiState.isTransitioning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        if (uiState.isRunning) "Stopping…" else "Starting VPN tunnel…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            return
        }

        // Aggregate stats
        val totalBps = uiState.apps.sumOf { it.currentThroughputBps }
        val totalMB = uiState.apps.sumOf { it.bytesOut + it.bytesIn } / (1024.0 * 1024.0)
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // [E] Show dash when no data instead of "0.00"
                StatChip("Active Apps", if (uiState.apps.isEmpty()) "—" else "${uiState.apps.size}")
                StatChip("Total Usage", if (totalMB < 0.01) "—" else "%.1f MB".format(totalMB))
                StatChip(
                    "Combined Speed",
                    if (totalBps < 1000) "—" else "%.2f Mbps".format(totalBps / 1_000_000.0)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // App list
        if (uiState.apps.isEmpty()) {
            // [D] Only show "waiting" after delay
            AnimatedVisibility(visible = showEmptyHint, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Waiting for app traffic…",
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Open any app (Chrome, YouTube, etc.) to see it here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.apps, key = { it.uid }) { app ->
                    AppCard(app = app, onClick = { onAppClick(app) })
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (value == "—") MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun AppCard(app: com.qos.scheduler.model.AppTraffic, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // [C] Priority badge first — prominent, left-aligned visual anchor
            PriorityBadge(app.priorityClass)

            Column(modifier = Modifier.weight(1f)) {
                // [C] App name larger and bolder
                Text(
                    app.getDisplayName(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                val dataMB = (app.bytesOut + app.bytesIn) / (1024.0 * 1024.0)
                Text(
                    "%.2f MB  •  %.2f Mbps".format(dataMB, app.currentThroughputBps / 1_000_000.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${app.getDominantCategory().displayName}  •  ${app.getConnectionSummary()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Chevron hint
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun PriorityBadge(priority: TrafficClass) {
    val (color, label, icon) = when (priority) {
        TrafficClass.HIGH   -> Triple(Color(0xFF4CAF50), "HIGH",   "▲")
        TrafficClass.MEDIUM -> Triple(Color(0xFFFFC107), "MED",    "●")
        TrafficClass.LOW    -> Triple(Color(0xFFF44336), "LOW",    "▼")
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, style = MaterialTheme.typography.labelSmall, color = Color.White)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
