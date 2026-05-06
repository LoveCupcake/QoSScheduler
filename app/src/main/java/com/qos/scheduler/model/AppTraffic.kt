package com.qos.scheduler.model

import java.util.concurrent.ConcurrentHashMap

/**
 * Represents traffic from a specific application on this device
 */
data class AppTraffic(
    val uid: Int,                                    // Android UID of the app
    var appName: String,                             // Human-readable app name
    var packageName: String,                         // Package name (e.g., com.android.chrome)
    var priorityClass: TrafficClass = TrafficClass.MEDIUM,
    var bytesIn: Long = 0L,
    var bytesOut: Long = 0L,
    var currentThroughputBps: Long = 0L,
    var qosAllowedPackets: Long = 0L,
    var qosDroppedPackets: Long = 0L,
    var lastSeenTimestamp: Long = System.currentTimeMillis(),
    val activeFlows: ConcurrentHashMap<FlowKey, PacketFlow> = ConcurrentHashMap()
) {
    /**
     * Get display name for UI
     * Shows app name if available, otherwise package name
     */
    fun getDisplayName(): String {
        return if (appName.isNotEmpty() && appName != packageName) {
            appName
        } else {
            // Simplify package name for display
            packageName.split(".").lastOrNull() ?: packageName
        }
    }
    
    /**
     * Get icon resource name (can be used to load app icon)
     */
    fun getIconIdentifier(): String {
        return packageName
    }
    
    /**
     * Get summary of active connections
     */
    fun getConnectionSummary(): String {
        val destinations = activeFlows.values
            .map { flow: PacketFlow -> flow.key.dstIp }
            .distinct()
            .take(3)
        
        return when {
            destinations.isEmpty() -> "No active connections"
            destinations.size == 1 -> "Connected to ${destinations[0]}"
            else -> "Connected to ${destinations.size} destinations"
        }
    }
    
    /**
     * Get dominant traffic category
     */
    fun getDominantCategory(): TrafficCategory {
        val categories = activeFlows.values
            .groupBy { flow -> flow.category }
            .mapValues { (_, flows) -> flows.sumOf { flow -> flow.byteCount.toLong() } }
        
        return categories.maxByOrNull { entry -> entry.value }?.key ?: TrafficCategory.UNKNOWN
    }

    fun getQosDropRatePercent(): Double {
        val total = qosAllowedPackets + qosDroppedPackets
        if (total == 0L) return 0.0
        return (qosDroppedPackets * 100.0) / total
    }
}
