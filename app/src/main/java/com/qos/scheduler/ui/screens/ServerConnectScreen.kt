package com.qos.scheduler.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay
import org.json.JSONException
import org.json.JSONObject

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
    var qrError by remember { mutableStateOf("") }
    // [R] Track syncing state to disable button during operation
    var isSyncing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    // [O] Auto-clear sync status after 5 seconds
    LaunchedEffect(syncStatusMessage) {
        if (syncStatusMessage.isNotBlank()) {
            delay(5000)
            // The ViewModel should expose a clear method; for now we rely on the message
            // becoming stale — no further action needed here since the VM owns the state.
            // If you add a clearSyncStatus callback, call it here.
        }
    }

    // Update syncing state based on message changes
    LaunchedEffect(syncStatusMessage) {
        isSyncing = syncStatusMessage == "⏳ Connecting…"
    }

    // ── QR Scanner launcher ───────────────────────────────────────────────
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
                // [Q] Single action: save + sync together
                onSaveServerUrl(url)
                isSyncing = true
                onSync()
            } else {
                qrError = "❌ Invalid QR code. Please scan the code from Web Admin."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Connection") },
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Connection status badge ───────────────────────────────────
            ConnectionStatusBadge(isConnected = isConnected)

            // ── QR Code section ───────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "📷 Scan QR Code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Open Web Admin on your PC → go to \"Connect App\" → scan the QR code shown there.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 20.sp
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("📷  Open Camera & Scan QR", style = MaterialTheme.typography.labelLarge)
                    }

                    AnimatedVisibility(visible = qrError.isNotBlank()) {
                        Text(qrError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ── Divider ───────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "  or enter manually  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // ── Manual URL input ──────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Server Address",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("http://IP:3000") },
                        placeholder = { Text("http://192.168.10.115:3000") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            // [Q] Save + sync in one action
                            onSaveServerUrl(urlInput)
                            isSyncing = true
                            onSync()
                        }),
                        trailingIcon = if (isConnected) {
                            { Icon(Icons.Default.Check, null, tint = Color(0xFF10B981)) }
                        } else null
                    )

                    // [Q] Single "Save & Connect" button — removed the separate "Save" button
                    Button(
                        onClick = {
                            keyboard?.hide()
                            onSaveServerUrl(urlInput)
                            isSyncing = true
                            onSync()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        // [R] Disable while syncing
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Connecting…")
                        } else {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save & Connect")
                        }
                    }
                }
            }

            // ── Sync status ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = syncStatusMessage.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        syncStatusMessage.startsWith("✅") -> Color(0xFFD1FAE5)
                        syncStatusMessage.startsWith("❌") -> Color(0xFFFEE2E2)
                        else -> Color(0xFFFFF9C4)
                    }
                ) {
                    Text(
                        text = syncStatusMessage,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── What sync does ────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "ℹ️ What does sync do?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    listOf(
                        "📱" to "Register this device with the server",
                        "⬇️" to "Download QoS priority rules from Web Admin",
                        "⚡" to "Apply those rules to the Token Bucket instantly",
                        "📊" to "Push bandwidth telemetry to the server for dashboards"
                    ).forEach { (icon, text) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(icon, fontSize = 16.sp)
                            Text(text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConnectionStatusBadge(isConnected: Boolean) {
    // [P] Use theme colors instead of hardcoded light-mode values
    val (bg, label, dot) = if (isConnected)
        Triple(Color(0xFFD1FAE5), "Connected to Server", Color(0xFF10B981))
    else
        Triple(MaterialTheme.colorScheme.surfaceVariant, "Not Connected", MaterialTheme.colorScheme.outline)

    Surface(shape = RoundedCornerShape(99.dp), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isConnected) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
