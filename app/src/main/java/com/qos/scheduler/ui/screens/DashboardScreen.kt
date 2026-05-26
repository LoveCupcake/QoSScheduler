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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qos.scheduler.model.TrafficClass
import com.qos.scheduler.ui.UiState
import com.qos.scheduler.ui.theme.*
import kotlinx.coroutines.delay

// ── Space radial gradient applied to the whole screen ─────────────────────
private val ScreenBackground = Brush.radialGradient(
    colors = listOf(
        Color(0xFF0D1B2E),   // deep blue centre
        Color(0xFF080B12),   // void black edge
    ),
    radius = 1800f
)

@Composable
fun DashboardScreen(
    uiState: UiState,
    onToggleScheduler: () -> Unit,
    onAppClick: (com.qos.scheduler.model.AppTraffic) -> Unit,
    onSettingsClick: () -> Unit
) {
    var showEmptyHint by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRunning, uiState.isTransitioning) {
        if (uiState.isRunning && !uiState.isTransitioning) {
            delay(2500); showEmptyHint = true
        } else { showEmptyHint = false }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "QoS Scheduler",
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = StarWhite,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "NETWORK TRAFFIC MANAGER",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 3.sp
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = NeonCyan
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Emulator banner ───────────────────────────────────────────
            val isEmulator = android.os.Build.PRODUCT.contains("sdk") ||
                    android.os.Build.MODEL.contains("Emulator")
            if (isEmulator) {
                CyberBanner(
                    text = "🛠  Emulator Mode — QoS logic test ready",
                    color = ElectricPurple
                )
                Spacer(Modifier.height(10.dp))
            }

            // ── Mode / Control card ───────────────────────────────────────
            CyberCard(
                glowColor = if (uiState.isMonitorOnlyMode) ElectricPurple else NeonCyan
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                    fontWeight = FontWeight.Bold,
                                    color = StarWhite,
                                    fontSize = 14.sp
                                )
                                if (!uiState.isMonitorOnlyMode) {
                                    NeonBadge("ACTIVE", MatrixGreen)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (uiState.isMonitorOnlyMode)
                                    "Monitoring app traffic. Switch to Relay to enforce priorities."
                                else
                                    "QoS is enforcing bandwidth scheduling for local apps.",
                                fontSize = 11.sp,
                                color = StarGrey
                            )
                        }

                        Switch(
                            checked = uiState.isRunning,
                            onCheckedChange = { onToggleScheduler() },
                            enabled = !uiState.isTransitioning,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VoidBlack,
                                checkedTrackColor = NeonCyan,
                                uncheckedThumbColor = StarGrey,
                                uncheckedTrackColor = SurfaceElevated
                            )
                        )
                    }

                    // Fallback reason
                    if (uiState.relayHealth.fallbackReason != com.qos.scheduler.service.RelayFallbackReason.NONE &&
                        uiState.relayHealth.fallbackReason != com.qos.scheduler.service.RelayFallbackReason.DEFAULT_MONITOR_MODE
                    ) {
                        NeonBanner(
                            "⚠  Auto-fallback: ${uiState.relayHealth.fallbackReason.userLabel()}",
                            color = CyberPink
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Empty / stopped / transitioning states ────────────────────
            if (!uiState.isRunning && !uiState.isTransitioning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📡", fontSize = 56.sp)
                        Text(
                            "QoS Scheduler is stopped",
                            fontWeight = FontWeight.Bold,
                            color = StarWhite,
                            fontSize = 16.sp
                        )
                        Text(
                            "Toggle the switch above to start monitoring\nand controlling per-app bandwidth.",
                            fontSize = 12.sp,
                            color = StarGrey,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                return@Column
            }

            if (uiState.isTransitioning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.dp)
                        Text(
                            if (uiState.isRunning) "Stopping…" else "Starting VPN tunnel…",
                            color = StarGrey,
                            fontSize = 13.sp
                        )
                    }
                }
                return@Column
            }

            // ── Aggregate stats ───────────────────────────────────────────
            val totalBps = uiState.apps.sumOf { it.currentThroughputBps }
            val totalMB  = uiState.apps.sumOf { it.bytesOut + it.bytesIn } / (1024.0 * 1024.0)

            CyberCard(glowColor = NeonCyan) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CyberStatChip("Active Apps",   if (uiState.apps.isEmpty()) "—" else "${uiState.apps.size}")
                    VerticalDivider(color = StarDim, modifier = Modifier.height(36.dp))
                    CyberStatChip("Total Usage",   if (totalMB < 0.01) "—" else "%.1f MB".format(totalMB))
                    VerticalDivider(color = StarDim, modifier = Modifier.height(36.dp))
                    CyberStatChip("Speed", if (totalBps < 1000) "—" else "%.2f Mbps".format(totalBps / 1_000_000.0))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── App list ──────────────────────────────────────────────────
            if (uiState.apps.isEmpty()) {
                AnimatedVisibility(visible = showEmptyHint, enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp,
                                color = NeonCyan
                            )
                            Text("Waiting for app traffic…", color = StarGrey)
                            Text(
                                "Open any app (Chrome, YouTube, etc.) to see it here",
                                fontSize = 12.sp,
                                color = StarDim,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.apps, key = { it.uid }) { app ->
                        CyberAppCard(app = app, onClick = { onAppClick(app) })
                    }
                }
            }
        }
    }
}

// ── Reusable Cyber components ──────────────────────────────────────────────

@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    glowColor: Color = NeonCyan,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(SurfaceCard, SurfaceElevated)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(glowColor.copy(alpha = 0.6f), glowColor.copy(alpha = 0.1f))
                ),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        content()
    }
}

@Composable
fun NeonBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Black, color = color, letterSpacing = 1.sp)
    }
}

@Composable
private fun NeonBanner(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun CyberBanner(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun CyberStatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = if (value == "—") StarDim else NeonCyan
        )
        Text(label, fontSize = 10.sp, color = StarGrey, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun CyberAppCard(app: com.qos.scheduler.model.AppTraffic, onClick: () -> Unit) {
    val glowColor = when (app.priorityClass) {
        TrafficClass.HIGH   -> MatrixGreen
        TrafficClass.MEDIUM -> CyberAmber
        TrafficClass.LOW    -> CyberPink
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(SurfaceCard, SurfaceElevated)
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(glowColor.copy(alpha = 0.5f), glowColor.copy(alpha = 0.05f))
                ),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Priority badge
            CyberPriorityBadge(app.priorityClass)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.getDisplayName(),
                    fontWeight = FontWeight.Bold,
                    color = StarWhite,
                    fontSize = 14.sp
                )
                val dataMB = (app.bytesOut + app.bytesIn) / (1024.0 * 1024.0)
                Text(
                    "%.2f MB  •  %.2f Mbps".format(dataMB, app.currentThroughputBps / 1_000_000.0),
                    fontSize = 11.sp,
                    color = NeonCyan
                )
                Text(
                    "${app.getDominantCategory().displayName}  •  ${app.getConnectionSummary()}",
                    fontSize = 11.sp,
                    color = StarGrey
                )
            }

            Text("›", fontSize = 22.sp, color = StarDim, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
fun PriorityBadge(priority: TrafficClass) {
    CyberPriorityBadge(priority)
}

@Composable
fun CyberPriorityBadge(priority: TrafficClass) {
    val (color, label, icon) = when (priority) {
        TrafficClass.HIGH   -> Triple(MatrixGreen, "HIGH", "▲")
        TrafficClass.MEDIUM -> Triple(CyberAmber,  "MED",  "●")
        TrafficClass.LOW    -> Triple(CyberPink,   "LOW",  "▼")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 10.sp, color = color, fontWeight = FontWeight.Black)
            Text(label, fontSize = 9.sp, color = color, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}
