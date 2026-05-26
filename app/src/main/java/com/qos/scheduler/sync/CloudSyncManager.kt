package com.qos.scheduler.sync

import android.util.Log
import com.qos.scheduler.model.TrafficClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "CloudSync"

/**
 * Data class representing a single QoS policy fetched from the server.
 * Maps directly to the `policies` table in the backend.
 */
data class ServerPolicy(
    val packageName: String,
    val appName: String,
    val priority: TrafficClass,
    val maxBps: Long          // 0 = auto WFQ, >0 = hard cap in bps
)

/**
 * Result sealed class — all network calls return this, never throw to caller.
 */
sealed class SyncResult {
    data class Success(val policies: List<ServerPolicy>) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

/**
 * Stateless manager for all server communication.
 * Uses OkHttp directly (no Retrofit) to keep the dependency footprint minimal.
 * All functions are suspend and run on Dispatchers.IO — safe to call from ViewModel.
 */
object CloudSyncManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    // ─────────────────────────────────────────────
    // Connectivity check
    // ─────────────────────────────────────────────

    /**
     * Quick ping to /api/info. Returns true if server is reachable.
     */
    suspend fun ping(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$serverUrl/api/info").get().build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.d(TAG, "ping failed: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────
    // Register this device on the server
    // ─────────────────────────────────────────────

    /**
     * Registers (or updates heartbeat of) this device on the server.
     * Call this once after connecting and then periodically.
     */
    suspend fun registerDevice(
        serverUrl: String,
        deviceId: String,
        deviceName: String,
        model: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("id",    deviceId)
                put("name",  deviceName)
                put("model", model)
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("$serverUrl/api/devices")
                .post(body)
                .build()

            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "registerDevice failed: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────
    // Fetch policies from server
    // ─────────────────────────────────────────────

    /**
     * Fetches all policies applicable to this device from the server.
     * The server returns both __all__ policies and device-specific ones.
     */
    suspend fun fetchPolicies(serverUrl: String, deviceId: String): SyncResult =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$serverUrl/api/policies?device_id=$deviceId")
                    .get()
                    .build()

                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext SyncResult.Error("HTTP ${response.code}")
                    }
                    val body = response.body?.string()
                        ?: return@withContext SyncResult.Error("Empty response")

                    val arr = JSONArray(body)
                    val policies = (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.getJSONObject(i)
                        val priorityStr = obj.optString("priority", "MEDIUM")
                        val priority = runCatching {
                            TrafficClass.valueOf(priorityStr)
                        }.getOrDefault(TrafficClass.MEDIUM)

                        ServerPolicy(
                            packageName = obj.getString("package_name"),
                            appName     = obj.getString("app_name"),
                            priority    = priority,
                            maxBps      = obj.optLong("max_bps", 0L)
                        )
                    }
                    SyncResult.Success(policies)
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchPolicies failed: ${e.message}")
                SyncResult.Error(e.message ?: "Unknown error")
            }
        }

    // ─────────────────────────────────────────────
    // Push telemetry to server
    // ─────────────────────────────────────────────

    /**
     * Sends a batch of per-app traffic stats to the server.
     * Entries is a list of (packageName, appName, bytesIn, bytesOut).
     * Fire-and-forget — failure is logged but not surfaced to UI.
     */
    suspend fun pushTelemetry(
        serverUrl: String,
        deviceId: String,
        entries: List<TelemetryEntry>
    ) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        try {
            val arr = JSONArray().apply {
                entries.forEach { e ->
                    put(JSONObject().apply {
                        put("package_name", e.packageName)
                        put("app_name",     e.appName)
                        put("priority",     e.priority)
                        put("bytes_in",     e.bytesIn)
                        put("bytes_out",    e.bytesOut)
                        put("requested_bps",e.requestedBps)
                        put("allowed_bps",  e.allowedBps)
                        put("dropped_pkts", e.droppedPkts)
                        put("tcp_flows",    e.tcpFlows)
                        put("udp_flows",    e.udpFlows)
                    })
                }
            }
            val payload = JSONObject().apply {
                put("device_id", deviceId)
                put("entries",   arr)
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("$serverUrl/api/telemetry")
                .post(payload)
                .build()

            client.newCall(req).execute().use { /* discard */ }
        } catch (e: Exception) {
            Log.w(TAG, "pushTelemetry failed (non-critical): ${e.message}")
        }
    }
}

data class TelemetryEntry(
    val packageName: String,
    val appName: String,
    val priority: String,
    val bytesIn: Long,
    val bytesOut: Long,
    val requestedBps: Long,
    val allowedBps: Long,
    val droppedPkts: Long,
    val tcpFlows: Int,
    val udpFlows: Int
)
