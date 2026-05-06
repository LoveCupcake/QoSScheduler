package com.qos.scheduler.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qos.scheduler.model.TrafficClass
import com.qos.scheduler.ui.UiState

@Composable
fun DashboardScreen(
    uiState: UiState,
    onToggleScheduler: () -> Unit,
    onAppClick: (com.qos.scheduler.model.AppTraffic) -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("QoS Scheduler", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
                Switch(
                    checked = uiState.isRunning,
                    onCheckedChange = { onToggleScheduler() },
                    enabled = !uiState.isTransitioning
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Emulator / Local Test Banner
        val isEmulator = android.os.Build.PRODUCT.contains("sdk") || android.os.Build.MODEL.contains("Emulator")
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
                    Text("🛠️ Emulator Mode:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text("Sẵn sàng để test logic QoS cho App Host.", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Surface(
            color = if (uiState.isMonitorOnlyMode) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Chế độ: ${uiState.runtimeMode.displayName()}",
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
                Text(
                    if (uiState.isMonitorOnlyMode) {
                        "Đang giám sát traffic của các app trên host. Chuyển sang Relay để thực thi ưu tiên băng thông."
                    } else {
                        "QoS đang thực thi lập lịch cho các app local dựa trên độ ưu tiên đã thiết lập."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (uiState.relayHealth.fallbackReason != com.qos.scheduler.service.RelayFallbackReason.NONE && 
                    uiState.relayHealth.fallbackReason != com.qos.scheduler.service.RelayFallbackReason.DEFAULT_MONITOR_MODE) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Tự động ngắt QoS: ${uiState.relayHealth.fallbackReason.userLabel()}",
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

        // Aggregate stats
        if (uiState.isRunning) {
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
                    StatChip("Active Apps", "${uiState.apps.size}")
                    StatChip("Total Usage", "%.1f MB".format(totalMB))
                    StatChip("Combined Speed", "%.2f Mbps".format(totalBps / 1_000_000.0))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // App list
        if (uiState.apps.isEmpty() && uiState.isRunning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Waiting for apps to run...", color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Open any app (Chrome, YouTube, etc.) to see it here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
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
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(app.getDisplayName(), fontWeight = FontWeight.SemiBold)
                if (app.appName != app.packageName) {
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                val dataMB = app.bytesOut / (1024.0 * 1024.0)
                Text(
                    "%.2f MB • %.2f Mbps".format(dataMB, app.currentThroughputBps / 1_000_000.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Activity: ${app.getDominantCategory().displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    app.getConnectionSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            PriorityBadge(app.priorityClass)
        }
    }
}

@Composable
fun PriorityBadge(priority: TrafficClass) {
    val (color, label) = when (priority) {
        TrafficClass.HIGH   -> Color(0xFF4CAF50) to "HIGH"
        TrafficClass.MEDIUM -> Color(0xFFFFC107) to "MED"
        TrafficClass.LOW    -> Color(0xFFF44336) to "LOW"
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
