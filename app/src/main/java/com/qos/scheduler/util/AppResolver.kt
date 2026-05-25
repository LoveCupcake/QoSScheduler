package com.qos.scheduler.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.util.Log
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves network connections to UIDs and provides application information.
 *
 * Fixes applied:
 *  - Removed GlobalScope / launch / withLock imports that were never used (dead imports)
 *  - Removed resolveMutex that was declared but never wired in
 *  - getUidForConnection() no longer returns -1 to callers (was treated as valid UID)
 *    → returns null for negative-cached keys so callers can distinguish "not found"
 *  - failCountCache increments use compute() for atomic read-modify-write
 *  - clearCache() now also clears failCountCache
 *  - Hardcoded UID 10145="Google Play Services" removed (UIDs are not stable across devices)
 *  - Vietnamese strings replaced with English
 */
class AppResolver(private val context: Context) {

    private val pm = context.packageManager
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    companion object {
        private val SYSTEM_UIDS = mapOf(
            0    to "Root",
            1000 to "Android System",
            1001 to "Phone Service",
            1027 to "NFC Service",
            2000 to "Shell / ADB",
            1073 to "WebView Service"
        )

        // Static cache: survives service restarts intentionally (app name lookup is expensive)
        private val appInfoCache = ConcurrentHashMap<Int, AppInfo>()

        // Instance-scoped caches managed per DataPlaneProcessor lifecycle
        // (moved out of companion so they clear on service restart)
    }

    // Per-instance caches (cleared when service restarts)
    private val uidCache      = ConcurrentHashMap<String, Int>()   // cacheKey -> uid (positive only)
    private val negativeCache = ConcurrentHashMap<String, Boolean>() // cacheKey -> true = "definitely not found"
    private val failCountCache = ConcurrentHashMap<String, Int>()

    data class AppInfo(
        val uid: Int,
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean
    )

    /**
     * Returns the UID that owns the given connection, or null if unknown / not found.
     *
     * FIX: Never returns -1 to callers. Previously -1 was cached and returned as a "valid" UID,
     * causing all subsequent packets on that flow to be misattributed to a synthetic bucket forever.
     * Now:
     *  - Positive UID → cached and returned
     *  - UID=-1 (kernel socket, transient) → NOT cached; return null so next packet retries
     *  - After 3 consecutive -1 results → move to negativeCache, return null (stop retrying)
     */
    fun getUidForConnection(
        protocol: Int,
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int
    ): Int? {
        val cacheKey = "$protocol:$srcIp:$srcPort:$dstIp:$dstPort"

        // Positive cache hit
        uidCache[cacheKey]?.let { return it }

        // Negative cache hit — we've retried 3 times and it's genuinely unresolvable
        if (negativeCache.containsKey(cacheKey)) return null

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val isIpv6 = dstIp.contains(":")
                val localAddr  = if (isIpv6) InetSocketAddress("::", srcPort) else InetSocketAddress("0.0.0.0", srcPort)
                val remoteAddr = InetSocketAddress(dstIp, dstPort)

                val uid = cm.getConnectionOwnerUid(protocol, localAddr, remoteAddr)
                if (uid > 0) {
                    uidCache[cacheKey] = uid
                    failCountCache.remove(cacheKey)
                    return uid
                } else {
                    // uid == -1: kernel socket or not yet bound — transient state, do NOT cache permanently
                    // FIX: atomic increment to avoid lost-update under concurrent calls
                    val failCount = failCountCache.compute(cacheKey) { _, v -> (v ?: 0) + 1 } ?: 1
                    if (failCount >= 3) {
                        // Give up retrying — move to negative cache
                        negativeCache[cacheKey] = true
                        failCountCache.remove(cacheKey)
                    }
                    return null // Never return -1 to callers
                }
            } catch (e: Exception) {
                val failCount = failCountCache.compute(cacheKey) { _, v -> (v ?: 0) + 1 } ?: 1
                if (failCount >= 3) {
                    negativeCache[cacheKey] = true
                    failCountCache.remove(cacheKey)
                }
            }
        }
        return null
    }

    fun getAppInfo(uid: Int): AppInfo {
        appInfoCache[uid]?.let { return it }

        SYSTEM_UIDS[uid]?.let { name ->
            val info = AppInfo(uid, "android.system.$uid", name, true)
            appInfoCache[uid] = info
            return info
        }

        return try {
            val packages = pm.getPackagesForUid(uid)
            val info = if (packages != null && packages.isNotEmpty()) {
                val pkgName = packages[0]
                val appInfo = pm.getApplicationInfo(pkgName, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                AppInfo(uid, pkgName, label, isSystem)
            } else {
                // FIX: removed hardcoded uid==10145 → "Google Play Services" mapping.
                // UIDs are assigned dynamically and are NOT stable across devices/installs.
                val label = if (uid < 2000) "System Service" else "App UID $uid"
                AppInfo(uid, "unknown.app.$uid", label, uid < 10000)
            }
            appInfoCache[uid] = info
            info
        } catch (e: Exception) {
            AppInfo(uid, "unknown.app.$uid", "App UID $uid", uid < 10000)
        }
    }

    /**
     * FIX: clearCache() now clears ALL three caches including failCountCache.
     * Previously stale fail counts survived a cache reset, causing premature negative-caching.
     */
    fun clearCache() {
        uidCache.clear()
        negativeCache.clear()
        failCountCache.clear()
        appInfoCache.clear()
    }
}
