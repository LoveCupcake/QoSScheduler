package com.qos.scheduler.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qos.scheduler.service.RelayFallbackReason
import com.qos.scheduler.service.RelayHealthSnapshot
import com.qos.scheduler.service.RelayRuntimeMode
import com.qos.scheduler.ui.theme.*

private val SettingsBg = Brush.radialGradient(
    colors = listOf(Color(0xFF0D1B2E), Color(0xFF080B12)),
    radius = 1800f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    runtimeMode: RelayRuntimeMode,
    relayHealth: RelayHealthSnapshot,
    currentUplinkMbps: Float,
    onModeChanged: (RelayRuntimeMode) -> Unit,
    onUplinkChanged: (Float) -> Unit,
    onResetPriorities: () -> Unit,
    onServerConnectClick: () -> Unit,
    onBack: () -> Unit
) {
    var uplinkText by remember(currentUplinkMbps) { mutableStateOf(currentUplinkMbps.toString()) }
    var uplinkError by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings", color = StarWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("SYSTEM CONFIGURATION", fontSize = 8.sp, color = NeonCyan, letterSpacing = 2.sp)
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
                .background(SettingsBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Mode Selection ──────────────────────────────────────────
                SectionLabel("Runtime Mode")
                CyberCard(glowColor = ElectricPurple) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CyberModeOption(
                            title = "Monitor (Safe)",
                            description = "Observe traffic only. Host connectivity is never disrupted.",
                            selected = runtimeMode == RelayRuntimeMode.MONITOR,
                            color = ElectricPurple,
                            onClick = { onModeChanged(RelayRuntimeMode.MONITOR) }
                        )
                        CyberModeOption(
                            title = "Relay Experimental",
                            description = "Enable real QoS enforcement for TCP/DNS. May add slight latency on first connect.",
                            selected = runtimeMode == RelayRuntimeMode.RELAY_EXPERIMENTAL,
                            color = NeonCyan,
                            onClick = { onModeChanged(RelayRuntimeMode.RELAY_EXPERIMENTAL) }
                        )
                    }
                }

                // ── System Health ───────────────────────────────────────────
                SectionLabel("System Health")
                CyberCard(glowColor = MatrixGreen) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CyberTelemetryRow(
                            "Status",
                            if (relayHealth.qosEnforced) "Enforcing" else "Monitoring",
                            if (relayHealth.qosEnforced) MatrixGreen else ElectricPurple
                        )
                        CyberColoredMetricRow("DNS Error Rate", relayHealth.dnsErrorRate)
                        CyberColoredMetricRow("Relay Error Rate", relayHealth.relayErrorRate)
                        CyberTelemetryRow("Active Flows", relayHealth.activeFlowCount.toString(), NeonCyan)
                        CyberTelemetryRow("Fallback Count", relayHealth.fallbackCount.toString(), StarGrey)
                        if (relayHealth.fallbackReason != RelayFallbackReason.NONE) {
                            Text(
                                "Last fallback: ${relayHealth.fallbackReason.userLabel()}",
                                fontSize = 11.sp,
                                color = CyberPink,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // ── Uplink Bandwidth ────────────────────────────────────────
                SectionLabel("Uplink Bandwidth")
                CyberCard(glowColor = NeonCyan) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Set your uplink speed so QoS can allocate bandwidth accurately.",
                            fontSize = 11.sp,
                            color = StarGrey
                        )
                        OutlinedTextField(
                            value = uplinkText,
                            onValueChange = { input ->
                                uplinkText = input
                                val parsed = input.toFloatOrNull()
                                uplinkError = parsed == null && input.isNotBlank()
                                if (parsed != null) onUplinkChanged(parsed)
                            },
                            label = { Text("Mbps", color = NeonCyan, fontSize = 12.sp) },
                            isError = uplinkError,
                            supportingText = if (uplinkError) {
                                { Text("Enter a valid number (e.g. 100)", color = CyberPink, fontSize = 11.sp) }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = StarDim,
                                focusedTextColor = StarWhite,
                                unfocusedTextColor = StarWhite,
                                cursorColor = NeonCyan,
                                errorBorderColor = CyberPink
                            )
                        )
                    }
                }

                // ── Reset Priorities ────────────────────────────────────────
                SectionLabel("Danger Zone")
                CyberCard(glowColor = CyberPink) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Reset Priorities", fontWeight = FontWeight.Bold, color = StarWhite, fontSize = 14.sp)
                        Text("Reset all apps back to MEDIUM priority.", fontSize = 11.sp, color = StarGrey)
                        Button(
                            onClick = { showResetDialog = true },
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberPink.copy(alpha = 0.2f),
                                contentColor = CyberPink
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberPink.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // ── Cloud Server ────────────────────────────────────────────
                SectionLabel("Cloud Server")
                CyberCard(glowColor = ElectricPurple) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("☁  Web Admin", fontWeight = FontWeight.Bold, color = StarWhite, fontSize = 14.sp)
                        Text(
                            "Connect to a Web Admin server to sync QoS policies and push telemetry.",
                            fontSize = 11.sp,
                            color = StarGrey
                        )
                        Button(
                            onClick = onServerConnectClick,
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricPurple.copy(alpha = 0.2f),
                                contentColor = ElectricPurple
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.6f))
                        ) {
                            Text("Configure Connection", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // ── Info card ───────────────────────────────────────────────
                CyberCard(glowColor = StarDim) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("QoS Scheduler Phase 1", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 12.sp)
                        Text("Production-Candidate Build", fontSize = 11.sp, color = StarGrey)
                        Text(
                            "The fail-open safety system will automatically fall back to Monitor mode if relay error rates exceed thresholds, ensuring you never lose internet connectivity.",
                            fontSize = 11.sp,
                            color = StarGrey
                        )
                    }
                }
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Confirm Reset?", color = StarWhite, fontWeight = FontWeight.Bold) },
                text  = { Text("All priority settings will be reset to MEDIUM.", color = StarGrey) },
                containerColor = SurfaceCard,
                confirmButton = {
                    TextButton(onClick = { onResetPriorities(); showResetDialog = false }) {
                        Text("Reset", color = CyberPink, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel", color = StarGrey)
                    }
                }
            )
        }
    }
}

// ── Private helpers ────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = NeonCyan,
        letterSpacing = 2.sp
    )
}

@Composable
private fun CyberModeOption(
    title: String,
    description: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) color.copy(alpha = 0.12f) else SurfaceElevated)
            .border(
                1.dp,
                if (selected) color.copy(alpha = 0.6f) else StarDim.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            )
            .then(Modifier.clickable(onClick = onClick))
            .padding(12.dp)
    ) {
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) color else StarWhite)
            Text(description, fontSize = 11.sp, color = StarGrey)
        }
    }
}

@Composable
private fun CyberTelemetryRow(label: String, value: String, valueColor: Color = StarWhite) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = StarGrey)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun CyberColoredMetricRow(label: String, rate: Double) {
    val (color, valueText) = when {
        rate >= 0.5 -> CyberPink   to "%.1f%%".format(rate * 100)
        rate >= 0.1 -> CyberAmber  to "%.1f%%".format(rate * 100)
        else        -> MatrixGreen to "%.1f%%".format(rate * 100)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = StarGrey)
        Text(valueText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
