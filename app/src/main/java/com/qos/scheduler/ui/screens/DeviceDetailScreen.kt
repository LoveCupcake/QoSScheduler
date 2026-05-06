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
    var showPriorityDialog by remember { mutableStateOf(false) }

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
                            if (it != device.ipAddress) {
                                InfoRow("Device Name", it)
                            }
                        }
                        InfoRow("IP Address", device.ipAddress)
                        device.macAddress?.let { InfoRow("MAC Address", it) }
                    }
                }
            }

            // Priority
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Priority Class", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PriorityBadge(device.priorityClass)
                            Button(onClick = { showPriorityDialog = true }) {
                                Text("Change")
                            }
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
                items(device.activeFlows.values.toList()) { flow ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(flow.category.name, fontWeight = FontWeight.SemiBold)
                            Text("${flow.key.dstIp}:${flow.key.dstPort}", style = MaterialTheme.typography.bodySmall)
                            Text("${flow.key.protocol.name} • ${formatBytes(flow.byteCount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        if (showPriorityDialog) {
            AlertDialog(
                onDismissRequest = { showPriorityDialog = false },
                title = { Text("Select Priority") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrafficClass.entries.forEach { priority ->
                            Button(
                                onClick = {
                                    onPriorityChanged(priority)
                                    showPriorityDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(priority.name)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPriorityDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
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
