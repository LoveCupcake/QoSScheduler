package com.qos.scheduler.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qos.scheduler.model.ConnectedDevice
import com.qos.scheduler.model.TrafficClass
import com.qos.scheduler.ui.theme.*

private val DeviceBg = Brush.radialGradient(
    colors = listOf(Color(0xFF0D1B2E), Color(0xFF080B12)),
    radius = 1800f
)

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
                title = {
                    Column {
                        Text("Device Details", color = StarWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("CONNECTED DEVICE", fontSize = 8.sp, color = NeonCyan, letterSpacing = 2.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidBlack)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeviceBg)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Device info ───────────────────────────────────────────
                item {
                    SectionLabel("Device Information")
                    Spacer(Modifier.height(6.dp))
                    CyberCard(glowColor = NeonCyan) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            device.hostname?.let {
                                if (it != device.ipAddress) CyberInfoRow("Name", it)
                            }
                            CyberInfoRow("IP Address", device.ipAddress)
                            device.macAddress?.let { CyberInfoRow("MAC Address", it) }
                        }
                    }
                }

                // ── Priority selector ─────────────────────────────────────
                item {
                    SectionLabel("Priority Level")
                    Spacer(Modifier.height(6.dp))
                    CyberCard(glowColor = ElectricPurple) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TrafficClass.entries.forEach { priority ->
                                val description = when (priority) {
                                    TrafficClass.HIGH   -> "Maximum throughput — reduces allocation of lower-priority devices"
                                    TrafficClass.MEDIUM -> "Balanced allocation — default for most devices"
                                    TrafficClass.LOW    -> "Background only — minimal bandwidth share"
                                }
                                val accentColor = when (priority) {
                                    TrafficClass.HIGH   -> MatrixGreen
                                    TrafficClass.MEDIUM -> CyberAmber
                                    TrafficClass.LOW    -> CyberPink
                                }
                                val selected = device.priorityClass == priority
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) accentColor.copy(alpha = 0.12f) else SurfaceElevated)
                                        .border(
                                            1.dp,
                                            if (selected) accentColor.copy(alpha = 0.5f) else StarDim.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onPriorityChanged(priority) }
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                priority.name,
                                                fontSize = 14.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selected) accentColor else StarWhite
                                            )
                                            Text(description, fontSize = 11.sp, color = StarGrey)
                                        }
                                        if (selected) {
                                            CyberPriorityBadge(priority)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Statistics ────────────────────────────────────────────
                item {
                    SectionLabel("Statistics")
                    Spacer(Modifier.height(6.dp))
                    CyberCard(glowColor = MatrixGreen) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CyberInfoRow("Throughput", "${"%.2f".format(device.currentThroughputBps / 1_000_000.0)} Mbps", NeonCyan)
                            CyberInfoRow("Downloaded", formatBytesDevice(device.bytesIn))
                            CyberInfoRow("Uploaded",   formatBytesDevice(device.bytesOut))
                            CyberInfoRow("Active Flows", "${device.activeFlows.size}")
                        }
                    }
                }

                // ── Active flows ──────────────────────────────────────────
                if (device.activeFlows.isNotEmpty()) {
                    item {
                        SectionLabel("Active Flows")
                        Spacer(Modifier.height(6.dp))
                    }
                    items(device.activeFlows.values.toList()) { flow ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceCard)
                                .border(1.dp, NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(flow.category.displayName, fontWeight = FontWeight.SemiBold, color = NeonCyan, fontSize = 12.sp)
                                Text("${flow.key.dstIp}:${flow.key.dstPort}", fontSize = 11.sp, color = StarWhite)
                                Text(
                                    "${flow.key.protocol.name} • ${formatBytesDevice(flow.byteCount)}",
                                    fontSize = 11.sp,
                                    color = StarGrey
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan, letterSpacing = 2.sp)
}

@Composable
private fun CyberInfoRow(label: String, value: String, valueColor: Color = StarWhite) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = StarGrey)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

private fun formatBytesDevice(bytes: Long): String = when {
    bytes < 1024            -> "$bytes B"
    bytes < 1024 * 1024     -> "${"%.1f".format(bytes / 1024.0)} KB"
    bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
    else                    -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
}
