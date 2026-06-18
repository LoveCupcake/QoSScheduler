package com.qos.scheduler.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.qos.scheduler.model.AppTraffic
import com.qos.scheduler.model.TrafficClass
import com.qos.scheduler.service.RelayRuntimeMode
import com.qos.scheduler.ui.theme.*

private val ScreenBg = Brush.radialGradient(
    colors = listOf(Color(0xFF0D1B2E), Color(0xFF080B12)),
    radius = 1800f
)

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

    var localPriority by remember(app.uid) { mutableStateOf(app.priorityClass) }
    var isApplying    by remember { mutableStateOf(false) }

    LaunchedEffect(app.priorityClass) {
        localPriority = app.priorityClass
        isApplying = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(app.appName, color = StarWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                .background(ScreenBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── App Info & Status card ──────────────────────────────────
                CyberCard(glowColor = if (isMonitorOnlyMode) ElectricPurple else NeonCyan) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                app.packageName,
                                fontSize = 11.sp,
                                color = StarGrey
                            )
                            NeonBadge(
                                text = if (isMonitorOnlyMode) "MONITORING" else "QoS ACTIVE",
                                color = if (isMonitorOnlyMode) ElectricPurple else MatrixGreen
                            )
                        }

                        Divider(color = StarDim.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            ThroughputItem("Downloaded", formatTraffic(app.bytesIn))
                            
                            val bps = app.currentThroughputBps
                            val speedStr = when {
                                bps >= 1_000_000 -> "%.2f Mbps".format(bps / 1_000_000.0)
                                bps >= 1_000 -> "%.0f Kbps".format(bps / 1000.0)
                                else -> "$bps Bps"
                            }
                            ThroughputItem("Speed", speedStr)
                            
                            val dropRate = app.getQosDropRatePercent()
                            ThroughputItem("Drop Rate", "%.1f%%".format(dropRate))
                        }
                    }
                }

                // ── Priority selection ──────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Priority Level",
                        fontWeight = FontWeight.Bold,
                        color = StarWhite,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                    AnimatedVisibility(visible = isApplying, enter = fadeIn(), exit = fadeOut()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = NeonCyan
                            )
                            Text("Applying…", fontSize = 11.sp, color = StarGrey)
                        }
                    }
                }

                CyberCard(glowColor = ElectricPurple) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        TrafficClass.values().forEach { priority ->
                            PriorityOption(
                                priority = priority,
                                selected = localPriority == priority,
                                onClick = {
                                    if (localPriority != priority) {
                                        localPriority = priority
                                        isApplying = true
                                        onPriorityChanged(priority)
                                    }
                                }
                            )
                        }
                    }
                }

                // ── Active Connections ──────────────────────────────────────
                val totalFlows = app.activeFlows.size
                val shownFlows = minOf(totalFlows, 20)
                Text(
                    if (totalFlows > 20) "Active Connections ($shownFlows / $totalFlows)"
                    else "Active Connections ($totalFlows)",
                    fontWeight = FontWeight.Bold,
                    color = StarWhite,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )

                CyberCard(
                    modifier = Modifier.weight(1f),
                    glowColor = NeonCyan
                ) {
                    if (app.activeFlows.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No active connections", fontSize = 12.sp, color = StarGrey)
                        }
                    } else {
                        val sortedFlows = app.activeFlows.values.toList()
                            .sortedByDescending { it.lastSeen }
                            .take(20)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                count = sortedFlows.size,
                                key = { i -> sortedFlows[i].key.hashCode() }
                            ) { i -> FlowItem(sortedFlows[i]) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowItem(flow: com.qos.scheduler.model.PacketFlow) {
    var displayHost by remember(flow.key.dstIp) { mutableStateOf(flow.key.dstIp) }
    LaunchedEffect(flow.key.dstIp) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val addr = java.net.InetAddress.getByName(flow.key.dstIp)
                val host = addr.canonicalHostName
                if (host != flow.key.dstIp && !host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                    displayHost = host
                }
            } catch (_: Exception) { }
        }
    }

    val categoryColor = when (flow.category) {
        com.qos.scheduler.model.TrafficCategory.ONLINE_GAMING,
        com.qos.scheduler.model.TrafficCategory.VOIP                -> CyberAmber
        com.qos.scheduler.model.TrafficCategory.STREAMING,
        com.qos.scheduler.model.TrafficCategory.VIDEO_CONFERENCING  -> NeonCyan
        com.qos.scheduler.model.TrafficCategory.WEB_BROWSING        -> MatrixGreen
        else                                                         -> ElectricPurple
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .border(1.dp, categoryColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$displayHost:${flow.key.dstPort}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = StarWhite
                )
                Text(
                    "${flow.key.protocol} • ${formatTraffic(flow.byteCount)}",
                    fontSize = 10.sp,
                    color = StarGrey
                )
            }
            NeonBadge(flow.category.displayName, categoryColor)
        }
    }
}

@Composable
private fun ThroughputItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = StarGrey)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
    }
}

@Composable
private fun PriorityOption(
    priority: TrafficClass,
    selected: Boolean,
    onClick: () -> Unit
) {
    val description = when (priority) {
        TrafficClass.HIGH   -> "Maximum throughput — reduces allocation of lower-priority apps"
        TrafficClass.MEDIUM -> "Balanced allocation — default for most apps"
        TrafficClass.LOW    -> "Background only — minimal bandwidth share"
    }
    val accentColor = when (priority) {
        TrafficClass.HIGH   -> MatrixGreen
        TrafficClass.MEDIUM -> CyberAmber
        TrafficClass.LOW    -> CyberPink
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (selected) Modifier.background(accentColor.copy(alpha = 0.12f))
                              .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
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
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun formatTraffic(bytes: Long): String = when {
    bytes < 1024            -> "$bytes B"
    bytes < 1024 * 1024     -> "${"%.1f".format(bytes / 1024.0)} KB"
    bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
    else                    -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
}
