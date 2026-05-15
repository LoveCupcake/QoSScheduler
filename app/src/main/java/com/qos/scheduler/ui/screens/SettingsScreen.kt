package com.qos.scheduler.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.qos.scheduler.util.PacketLogger

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
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt") },
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
            // Mode Selection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Chế độ Runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeOption(
                            title = "Monitor (An toàn)",
                            description = "Chỉ theo dõi lưu lượng, đảm bảo kết nối host ổn định tuyệt đối.",
                            selected = runtimeMode == RelayRuntimeMode.MONITOR,
                            onClick = { onModeChanged(RelayRuntimeMode.MONITOR) }
                        )
                        ModeOption(
                            title = "Relay Experimental",
                            description = "Bật QoS thực tế cho TCP/DNS. Có thể gây trễ nhẹ khi khởi tạo.",
                            selected = runtimeMode == RelayRuntimeMode.RELAY_EXPERIMENTAL,
                            onClick = { onModeChanged(RelayRuntimeMode.RELAY_EXPERIMENTAL) }
                        )
                    }
                }
            }

            // Health & Telemetry
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Thông số Kỹ thuật", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    TelemetryRow("Trạng thái", if (relayHealth.qosEnforced) "Đang thực thi" else "Đang theo dõi")
                    TelemetryRow("Lỗi DNS", String.format("%.1f%%", relayHealth.dnsErrorRate * 100))
                    TelemetryRow("Lỗi Relay", String.format("%.1f%%", relayHealth.relayErrorRate * 100))
                    TelemetryRow("Số luồng hoạt động", relayHealth.activeFlowCount.toString())
                    TelemetryRow("Số lần Fallback", relayHealth.fallbackCount.toString())
                    
                    if (relayHealth.fallbackReason != RelayFallbackReason.NONE) {
                        Text(
                            "Lý do ngắt gần nhất: ${relayHealth.fallbackReason.userLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Uplink bandwidth config
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Băng thông Uplink", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Cấu hình tốc độ mạng để QoS phân bổ chính xác",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    OutlinedTextField(
                        value = uplinkText,
                        onValueChange = { 
                            uplinkText = it
                            it.toFloatOrNull()?.let { mbps -> onUplinkChanged(mbps) }
                        },
                        label = { Text("Mbps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Reset priorities
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Khôi phục Ưu tiên", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Đặt lại tất cả ứng dụng về mức MEDIUM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    Button(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset All")
                    }
                }
            }

            // Cloud Server connect
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("☁️ Cloud Server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Kết nối với Web Admin Server để đồng bộ luật QoS và gửi thống kê.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Button(
                        onClick = onServerConnectClick,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cấu hình kết nối")
                    }
                }
            }

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("QoS Scheduler Phase 1", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Production-Candidate Build", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Hệ thống tự động bảo vệ (fail-open) sẽ ngắt QoS nếu phát hiện lỗi relay vượt ngưỡng để đảm bảo bạn không bị mất mạng.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Xác nhận Reset?") },
                text = { Text("Tất cả thiết lập ưu tiên sẽ quay về mặc định.") },
                confirmButton = {
                    TextButton(onClick = {
                        onResetPriorities()
                        showResetDialog = false
                    }) {
                        Text("Reset", color = MaterialTheme.errorColor())
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Hủy")
                    }
                }
            )
        }
    }
}

@Composable
fun MaterialTheme.errorColor() = colorScheme.error

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
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            androidx.compose.material3.Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            androidx.compose.material3.Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Text(text: String, style: androidx.compose.ui.text.TextStyle, alpha: Float = 1f, fontWeight: FontWeight? = null) {
    androidx.compose.material3.Text(
        text = text,
        style = style,
        fontWeight = fontWeight,
        color = androidx.compose.material3.LocalContentColor.current.copy(alpha = alpha)
    )
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        androidx.compose.material3.Text(label, style = MaterialTheme.typography.bodySmall)
        androidx.compose.material3.Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}
