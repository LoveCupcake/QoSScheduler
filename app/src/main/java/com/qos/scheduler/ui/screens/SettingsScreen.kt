package com.qos.scheduler.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qos.scheduler.service.RelayFallbackReason
import com.qos.scheduler.service.RelayHealthSnapshot
import com.qos.scheduler.service.RelayRuntimeMode

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
    var uplinkError by remember { mutableStateOf(false) }   // [K] validation state
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Mode Selection ────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Runtime Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeOption(
                            title = "Monitor (Safe)",
                            description = "Observe traffic only. Guarantees host connectivity is never disrupted.",
                            selected = runtimeMode == RelayRuntimeMode.MONITOR,
                            onClick = { onModeChanged(RelayRuntimeMode.MONITOR) }
                        )
                        ModeOption(
                            title = "Relay Experimental",
                            description = "Enable real QoS enforcement for TCP/DNS. May add slight latency on first connect.",
                            selected = runtimeMode == RelayRuntimeMode.RELAY_EXPERIMENTAL,
                            onClick = { onModeChanged(RelayRuntimeMode.RELAY_EXPERIMENTAL) }
                        )
                    }
                }
            }

            // ── Health & Telemetry ────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("System Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    TelemetryRow(
                        "Status",
                        if (relayHealth.qosEnforced) "Enforcing" else "Monitoring"
                    )

                    // [M] Color-coded error rates
                    ColoredMetricRow("DNS Error Rate", relayHealth.dnsErrorRate)
                    ColoredMetricRow("Relay Error Rate", relayHealth.relayErrorRate)

                    TelemetryRow("Active Flows", relayHealth.activeFlowCount.toString())
                    TelemetryRow("Fallback Count", relayHealth.fallbackCount.toString())

                    if (relayHealth.fallbackReason != RelayFallbackReason.NONE) {
                        Text(
                            "Last fallback: ${relayHealth.fallbackReason.userLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Uplink Bandwidth ──────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Uplink Bandwidth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Set your uplink speed so QoS can allocate bandwidth accurately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedTextField(
                        value = uplinkText,
                        onValueChange = { input ->
                            uplinkText = input
                            val parsed = input.toFloatOrNull()
                            uplinkError = parsed == null && input.isNotBlank()
                            if (parsed != null) onUplinkChanged(parsed)
                        },
                        label = { Text("Mbps") },
                        // [K] Validation error
                        isError = uplinkError,
                        supportingText = if (uplinkError) {
                            { Text("Enter a valid number (e.g. 100)", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // ── Reset Priorities ──────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reset Priorities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Reset all apps back to MEDIUM priority.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    // [L] Destructive button with warning icon
                    Button(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Reset All")
                    }
                }
            }

            // ── Cloud Server ──────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("☁️ Cloud Server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Connect to a Web Admin server to sync QoS policies and push telemetry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Button(
                        onClick = onServerConnectClick,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Configure Connection")
                    }
                }
            }

            // ── Info card ─────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("QoS Scheduler Phase 1", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Production-Candidate Build", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "The fail-open safety system will automatically fall back to Monitor mode if relay error rates exceed thresholds, ensuring you never lose internet connectivity.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Confirm Reset?") },
                text = { Text("All priority settings will be reset to MEDIUM.") },
                confirmButton = {
                    TextButton(onClick = {
                        onResetPriorities()
                        showResetDialog = false
                    }) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// [M] Color-coded metric row based on error threshold
@Composable
private fun ColoredMetricRow(label: String, rate: Double) {
    val (color, valueText) = when {
        rate >= 0.5  -> MaterialTheme.colorScheme.error to String.format("%.1f%%", rate * 100)
        rate >= 0.1  -> Color(0xFFF57F17) to String.format("%.1f%%", rate * 100) // amber
        else         -> Color(0xFF388E3C) to String.format("%.1f%%", rate * 100) // green
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(valueText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}
