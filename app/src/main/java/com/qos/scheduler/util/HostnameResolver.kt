package com.qos.scheduler.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Resolves IP addresses to human-readable hostnames
 */
class HostnameResolver(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val hostnameCache = ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Get cached hostname for an IP address
     * Returns null if not yet resolved
     */
    fun getCachedHostname(ipAddress: String): String? {
        return hostnameCache[ipAddress]
    }

    /**
     * Resolve hostname for an IP address asynchronously
     * Updates cache when resolved
     */
    fun resolveHostname(ipAddress: String, onResolved: (String) -> Unit) {
        // Check cache first
        hostnameCache[ipAddress]?.let {
            onResolved(it)
            return
        }

        scope.launch {
            val hostname = tryResolveHostname(ipAddress)
            if (hostname != null) {
                hostnameCache[ipAddress] = hostname
                onResolved(hostname)
            }
        }
    }

    /**
     * Try multiple methods to resolve hostname
     */
    private suspend fun tryResolveHostname(ipAddress: String): String? {
        // Method 1: Try reverse DNS (with timeout)
        val dnsName = withTimeoutOrNull(2000) {
            try {
                val addr = InetAddress.getByName(ipAddress)
                val hostname = addr.canonicalHostName
                // Check if we got a real hostname (not just IP)
                if (hostname != ipAddress && !hostname.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                    // Clean up hostname (remove domain suffix)
                    hostname.split(".").firstOrNull()?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        if (dnsName != null) return dnsName

        // Method 2: Try NetBIOS name resolution (for Windows devices)
        val netbiosName = tryNetBiosResolution(ipAddress)
        if (netbiosName != null) return netbiosName

        // If all methods fail, return null
        return null
    }

    /**
     * Try to resolve NetBIOS name (Windows devices)
     * This is a simplified version - full NetBIOS would require more complex implementation
     */
    private suspend fun tryNetBiosResolution(ipAddress: String): String? {
        // NetBIOS resolution would require sending UDP packets to port 137
        // For simplicity, we skip this for now
        // In a production app, you could use a library like jcifs
        return null
    }

    /**
     * Generate a friendly display name from MAC address
     * Format: "Device-XX:XX" (last 2 octets of MAC)
     */
    fun generateFriendlyName(macAddress: String?): String? {
        if (macAddress == null) return null
        val parts = macAddress.split(":")
        if (parts.size >= 2) {
            val suffix = parts.takeLast(2).joinToString(":")
            return "Device-$suffix"
        }
        return null
    }

    /**
     * Clear hostname cache
     */
    fun clearCache() {
        hostnameCache.clear()
    }
}
