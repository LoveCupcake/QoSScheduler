package com.qos.scheduler.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qos.scheduler.model.AppTraffic
import com.qos.scheduler.model.TrafficClass
import com.qos.scheduler.service.RelayRuntimeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    app: AppTraffic,
    runtimeMode: RelayRuntimeMode,
    qosEnforced: Boolean,
    onPriorityChanged: (TrafficClass) -> Unit,
    onBack: () -> Unit
) {
    val isMonitorOnlyMode = !qosEnforced
    
    // Local state for optimistic UI: moves the checkmark immediately
    var localPriority by androidx.compose.runtime.remember(app.uid) { 
        androidx.compose.runtime.mutableStateOf(app.priorityClass) 
    }
    
    // Sync local state when the underlying app data updates from the service
    androidx.compose.runtime.LaunchedEffect(app.priorityClass) {
        localPriority = app.priorityClass
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.appName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Info & Status
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            Text("UID: ${app.uid}", style = MaterialTheme.typography.labelSmall)
                        }
                        
                        Surface(
                            color = if (isMonitorOnlyMode) MaterialTheme.colorScheme.secondaryContainer else Color(0xFF4CAF50).copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                if (isMonitorOnlyMode) "MONITORING" else "QoS ACTIVE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isMonitorOnlyMode) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        ThroughputItem("Tải xuống", formatTraffic(app.bytesIn))
                        ThroughputItem("Tải lên", formatTraffic(app.bytesOut))
                        ThroughputItem("Hiện tại", "${app.currentThroughputBps / 1024} KB/s")
                    }
                }
            }

            // Priority Selection
            Text("Mức độ Ưu tiên", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    TrafficClass.values().forEach { priority ->
                        PriorityOption(
                            priority = priority,
                            selected = localPriority == priority,
                            onClick = { 
                                localPriority = priority
                                onPriorityChanged(priority) 
                            }
                        )
                    }
                }
            }
            
            // Active Connections
            Text("Kết nối Đang hoạt động (${app.activeFlows.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Use weight to fill remaining space
            ) {
                if (app.activeFlows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Không có kết nối nào", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    val sortedFlows = app.activeFlows.values.toList()
                        .sortedByDescending { it.lastSeen }
                        .take(20)

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            count = sortedFlows.size,
                            key = { index -> sortedFlows[index].key.hashCode() }
                        ) { index ->
                            val flow = sortedFlows[index]
                            FlowItem(flow)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowItem(flow: com.qos.scheduler.model.PacketFlow) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${flow.key.dstIp}:${flow.key.dstPort}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${flow.key.protocol} • ${formatTraffic(flow.byteCount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Surface(
                color = when(flow.category) {
                    com.qos.scheduler.model.TrafficCategory.ONLINE_GAMING, com.qos.scheduler.model.TrafficCategory.VOIP -> Color(0xFFFFEB3B).copy(alpha = 0.3f)
                    com.qos.scheduler.model.TrafficCategory.STREAMING, com.qos.scheduler.model.TrafficCategory.VIDEO_CONFERENCING -> Color(0xFF2196F3).copy(alpha = 0.2f)
                    com.qos.scheduler.model.TrafficCategory.WEB_BROWSING -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    flow.category.displayName,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ThroughputItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExplanationItem(label: String, description: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PriorityOption(
    priority: TrafficClass,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            priority.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun formatTraffic(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${String.format("%.1f", bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", bytes / (1024.0 * 1024))} MB"
        else -> "${String.format("%.2f", bytes / (1024.0 * 1024 * 1024))} GB"
    }
}
