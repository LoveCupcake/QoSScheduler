package com.qos.scheduler.util

import android.util.Log
import com.qos.scheduler.model.RawPacket

/**
 * Utility for logging packet information (debugging only).
 * Should be disabled in production builds.
 */
object PacketLogger {
    private const val TAG = "PacketLogger"
    private var enabled = false  // Set to true for debugging
    
    private var ipv4Count = 0L
    private var ipv6Count = 0L
    private var totalBytes = 0L
    
    fun logPacket(packet: RawPacket) {
        if (!enabled) return
        
        val isIpv6 = packet.srcIp.contains(':')
        if (isIpv6) {
            ipv6Count++
        } else {
            ipv4Count++
        }
        totalBytes += packet.length
        
        // Log every 100th packet to avoid spam
        if ((ipv4Count + ipv6Count) % 100L == 0L) {
            Log.d(TAG, "Packet Stats - IPv4: $ipv4Count, IPv6: $ipv6Count, Total: ${formatBytes(totalBytes)}")
        }
        
        // Detailed logging for IPv6 packets (first 10 only)
        if (isIpv6 && ipv6Count <= 10) {
            Log.d(TAG, "IPv6 Packet: ${packet.srcIp} -> ${packet.dstIp}, " +
                      "Port: ${packet.srcPort} -> ${packet.dstPort}, " +
                      "Protocol: ${packet.protocol}, " +
                      "Length: ${packet.length} bytes")
        }
    }
    
    fun getStats(): String {
        return "IPv4: $ipv4Count packets, IPv6: $ipv6Count packets, " +
               "Total: ${formatBytes(totalBytes)}, " +
               "IPv6 Ratio: ${if (ipv4Count + ipv6Count > 0) 
                   String.format("%.1f%%", ipv6Count * 100.0 / (ipv4Count + ipv6Count)) 
                   else "0%"}"
    }
    
    fun reset() {
        ipv4Count = 0
        ipv6Count = 0
        totalBytes = 0
    }
    
    fun setEnabled(enable: Boolean) {
        enabled = enable
        if (enable) {
            Log.d(TAG, "Packet logging enabled")
        }
    }
    
    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
