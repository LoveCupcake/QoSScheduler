package com.qos.scheduler.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qos.scheduler.model.ConnectedDevice
import com.qos.scheduler.model.TrafficClass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: ConnectedDevice,
    onPriorityChanged: (TrafficClass) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device info
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Device Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        device.hostname?.let {
                            if (it != device.ipAddress) InfoRow("Name", it)
                        }
                        InfoRow("IP Address", device.ipAddress)
                        device.macAddress?.let { InfoRow("MAC Address", it) }
                    }
                }
            }

            // [T] Inline priority selector — consistent with AppDetailScreen (no dialog)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Priority Level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        TrafficClass.entries.forEach { priority ->
                            // [F] Include description per level, same pattern as AppDetailScreen
                            val description = when (priority) {
                                TrafficClass.HIGH   -> "Maximum throughput — reduces allocation of lower-priority devices"
                                TrafficClass.MEDIUM -> "Balanced allocation — default for most devices"
                                TrafficClass.LOW    -> "Background only — minimal bandwidth share"
                            }
                            Surface(
                                onClick = { onPriorityChanged(priority) },
                                color = if (device.priorityClass == priority)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small,
                                border = if (device.priorityClass == priority) null else
                                    androidx.compose.foundation.BorderStroke(
                                        1.dp, MaterialTheme.colorScheme.outlineVariant
                                    ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            priority.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (device.priorityClass == priority)
                                                FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    if (device.priorityClass == priority) {
                                        PriorityBadge(priority)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            // Statistics
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        InfoRow("Throughput", "${String.format("%.2f", device.currentThroughputBps / 1_000_000.0)} Mbps")
                        InfoRow("Downloaded", formatBytes(device.bytesIn))
                        InfoRow("Uploaded", formatBytes(device.bytesOut))
                        InfoRow("Active Flows", "${device.activeFlows.size}")
                    }
                }
            }

            // Active flows
            if (device.activeFlows.isNotEmpty()) {
                item {
                    Text("Active Flows", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                // [U] Use category.displayName (friendly) instead of category.name (enum raw)
                items(device.activeFlows.values.toList()) { flow ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(flow.category.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${flow.key.dstIp}:${flow.key.dstPort}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "${flow.key.protocol.name} • ${formatBytes(flow.byteCount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${String.format("%.1f", bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", bytes / (1024.0 * 1024))} MB"
        else -> "${String.format("%.2f", bytes / (1024.0 * 1024 * 1024))} GB"
    }
}
