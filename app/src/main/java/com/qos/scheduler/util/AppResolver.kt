package com.qos.scheduler.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.util.Log
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Resolves network connections to UIDs and provides application information.
 */
class AppResolver(private val context: Context) {
    
    private val pm = context.packageManager
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val resolveMutex = kotlinx.coroutines.sync.Mutex()
    
    companion object {
        private val SYSTEM_UIDS = mapOf(
            0 to "Hệ thống (Root)",
            1000 to "Hệ thống (System)",
            1001 to "Điện thoại (Phone)",
            1027 to "NFC Service",
            2000 to "Shell/ADB",
            1073 to "Webview Service"
        )
        
        // STATIC CACHE: Survive service restarts
        private val uidCache = ConcurrentHashMap<String, Int>(10000)
        private val appInfoCache = ConcurrentHashMap<Int, AppInfo>()
    }

    data class AppInfo(
        val uid: Int,
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean
    )

    fun getUidForConnection(protocol: Int, srcIp: String, srcPort: Int, dstIp: String, dstPort: Int): Int? {
        val cacheKey = "$protocol:$srcIp:$srcPort:$dstIp:$dstPort"
        uidCache[cacheKey]?.let { return it }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                // Use wildcard address for local side to match intercepted connections on any local interface
                // This is critical for VPNs because the local IP might be the TUN IP or the Wi-Fi IP
                val isIpv6 = dstIp.contains(":")
                val localAddr = if (isIpv6) InetSocketAddress("::", srcPort) else InetSocketAddress("0.0.0.0", srcPort)
                val remoteAddr = InetSocketAddress(dstIp, dstPort)
                
                val uid = cm.getConnectionOwnerUid(protocol, localAddr, remoteAddr)
                if (uid != -1) {
                    uidCache[cacheKey] = uid
                    return uid
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
        
        return null
    }

    fun getAppInfo(uid: Int): AppInfo {
        appInfoCache[uid]?.let { return it }
        
        // Check static system mappings first
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
                // Special case for emulator and well-known UIDs
                val label = when {
                    uid < 2000 -> "Dịch vụ hệ thống"
                    uid == 10145 -> "Google Play Services" // Common on emulators
                    else -> "Ứng dụng $uid"
                }
                AppInfo(uid, "unknown.app.$uid", label, uid < 10000)
            }
            appInfoCache[uid] = info
            info
        } catch (e: Exception) {
            AppInfo(uid, "unknown.app.$uid", "Ứng dụng $uid", uid < 10000)
        }
    }

    fun clearCache() {
        uidCache.clear()
        appInfoCache.clear()
    }
}
