package com.qos.scheduler.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.qos.scheduler.ui.theme.*
import kotlinx.coroutines.delay
import org.json.JSONException
import org.json.JSONObject

private val ServerBg = Brush.radialGradient(
    colors = listOf(Color(0xFF0D1B2E), Color(0xFF080B12)),
    radius = 1800f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConnectScreen(
    currentServerUrl: String,
    isConnected: Boolean,
    syncStatusMessage: String,
    onSaveServerUrl: (String) -> Unit,
    onSync: () -> Unit,
    onBack: () -> Unit
) {
    var urlInput by remember(currentServerUrl) { mutableStateOf(currentServerUrl) }
    var qrError  by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(syncStatusMessage) {
        if (syncStatusMessage.isNotBlank()) delay(5000)
    }
    LaunchedEffect(syncStatusMessage) {
        isSyncing = syncStatusMessage == "⏳ Connecting…"
    }

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            qrError = ""
            val scanned = result.contents
            val url = try {
                JSONObject(scanned).getString("server")
            } catch (e: JSONException) {
                if (scanned.startsWith("http")) scanned else null
            }
            if (url != null) {
                urlInput = url
                onSaveServerUrl(url)
                isSyncing = true
                onSync()
            } else {
                qrError = "Invalid QR code. Please scan the code from Web Admin."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Server Connection", color = StarWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("WEB ADMIN SYNC", fontSize = 8.sp, color = NeonCyan, letterSpacing = 2.sp)
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
                .background(ServerBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // ── Connection status badge ───────────────────────────────
                CyberConnectionBadge(isConnected)

                // ── QR section ───────────────────────────────────────────
                CyberCard(glowColor = NeonCyan) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📷  Scan QR Code", fontWeight = FontWeight.Bold, color = StarWhite, fontSize = 15.sp)
                        Text(
                            "Open Web Admin on your PC → go to \"Connect App\" → scan the QR code shown there.",
                            fontSize = 12.sp,
                            color = StarGrey,
                            lineHeight = 18.sp
                        )
                        Button(
                            onClick = {
                                val options = ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt("Scan QR Code from Web Admin")
                                    setBeepEnabled(true)
                                    setBarcodeImageEnabled(false)
                                    setOrientationLocked(true)
                                }
                                qrLauncher.launch(options)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidBlack)
                        ) {
                            Text("Open Camera & Scan QR", fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                        AnimatedVisibility(visible = qrError.isNotBlank()) {
                            Text("❌  $qrError", fontSize = 11.sp, color = CyberPink)
                        }
                    }
                }

                // ── OR divider ────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = StarDim.copy(alpha = 0.5f))
                    Text("  or enter manually  ", fontSize = 11.sp, color = StarGrey)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = StarDim.copy(alpha = 0.5f))
                }

                // ── Manual URL ────────────────────────────────────────────
                CyberCard(glowColor = ElectricPurple) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Server Address", fontWeight = FontWeight.Bold, color = StarWhite, fontSize = 14.sp)
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("http://IP:3000", fontSize = 12.sp, color = ElectricPurple) },
                            placeholder = { Text("http://192.168.10.115:3000", fontSize = 12.sp, color = StarDim) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                keyboard?.hide()
                                onSaveServerUrl(urlInput); isSyncing = true; onSync()
                            }),
                            trailingIcon = if (isConnected) {
                                { Icon(Icons.Default.Check, null, tint = MatrixGreen) }
                            } else null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricPurple,
                                unfocusedBorderColor = StarDim,
                                focusedTextColor = StarWhite,
                                unfocusedTextColor = StarWhite,
                                cursorColor = ElectricPurple
                            )
                        )
                        Button(
                            onClick = {
                                keyboard?.hide()
                                onSaveServerUrl(urlInput); isSyncing = true; onSync()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricPurple.copy(alpha = 0.2f),
                                contentColor = ElectricPurple,
                                disabledContainerColor = StarDim.copy(alpha = 0.1f),
                                disabledContentColor = StarDim
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.5f))
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ElectricPurple)
                                Spacer(Modifier.width(8.dp))
                                Text("Connecting…", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save & Connect", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // ── Sync status toast ─────────────────────────────────────
                AnimatedVisibility(visible = syncStatusMessage.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                    val (statusColor, borderColor) = when {
                        syncStatusMessage.startsWith("✅") -> MatrixGreen to MatrixGreen
                        syncStatusMessage.startsWith("❌") -> CyberPink   to CyberPink
                        else                               -> CyberAmber  to CyberAmber
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(statusColor.copy(alpha = 0.1f))
                            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Text(syncStatusMessage, fontSize = 13.sp, color = statusColor, fontWeight = FontWeight.Medium)
                    }
                }

                // ── Info card ─────────────────────────────────────────────
                CyberCard(glowColor = StarDim) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ℹ  What does sync do?", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 13.sp)
                        listOf(
                            "📱" to "Register this device with the server",
                            "⬇" to "Download QoS priority rules from Web Admin",
                            "⚡" to "Apply those rules to the Token Bucket instantly",
                            "📊" to "Push bandwidth telemetry to server dashboards"
                        ).forEach { (icon, text) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(icon, fontSize = 14.sp)
                                Text(text, fontSize = 11.sp, color = StarGrey)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CyberConnectionBadge(isConnected: Boolean) {
    val (color, label) = if (isConnected)
        MatrixGreen to "Connected to Server"
    else
        StarDim to "Not Connected"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
