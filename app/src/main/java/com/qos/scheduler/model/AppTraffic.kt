package com.qos.scheduler.model

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents traffic from a specific application on this device.
 *
 * FIX: Mutable counters (bytesIn, bytesOut, qosAllowedPackets, qosDroppedPackets)
 * are now backed by AtomicLong to allow safe concurrent increment from multiple
 * coroutines/threads (packet processing loop + relay manager callbacks).
 *
 * FIX: priorityClass and lastSeenTimestamp are @Volatile for cross-thread visibility.
 *
 * NOTE: This is NOT a data class anymore — we removed the data class modifier because:
 *  1. data class generates hashCode() from all constructor params; mutable fields would
 *     corrupt any HashMap/HashSet that holds this object.
 *  2. data class copy() shares the same ConcurrentHashMap reference — not safe for snapshots.
 */
class AppTraffic(
    val uid: Int,                                    // Android UID of the app
    var appName: String,                             // Human-readable app name
    var packageName: String,                         // Package name (e.g., com.android.chrome)
    @Volatile var priorityClass: TrafficClass = TrafficClass.MEDIUM,
    @Volatile var currentThroughputBps: Long = 0L,
    @Volatile var currentRequestedBps: Long = 0L,
    @Volatile var currentAllowedBps: Long = 0L,
    @Volatile var lastSeenTimestamp: Long = System.currentTimeMillis(),
    val activeFlows: ConcurrentHashMap<FlowKey, PacketFlow> = ConcurrentHashMap()
) {
    private val _bytesIn = AtomicLong(0L)
    private val _bytesOut = AtomicLong(0L)
    private val _qosAllowedPackets = AtomicLong(0L)
    private val _qosDroppedPackets = AtomicLong(0L)

    // Public read accessors
    val bytesIn:  Long get() = _bytesIn.get()
    val bytesOut: Long get() = _bytesOut.get()
    val qosAllowedPackets: Long get() = _qosAllowedPackets.get()
    val qosDroppedPackets: Long get() = _qosDroppedPackets.get()

    // Atomic mutators called from DataPlaneProcessor (may run on multiple threads)
    fun addBytesIn(bytes: Long)  { _bytesIn.addAndGet(bytes) }
    fun addBytesOut(bytes: Long) { _bytesOut.addAndGet(bytes) }
    fun incrementAllowed()       { _qosAllowedPackets.incrementAndGet() }
    fun incrementDropped()       { _qosDroppedPackets.incrementAndGet() }
    fun updateLastSeen()         { lastSeenTimestamp = System.currentTimeMillis() }

    // Snapshot totals for throughput calculation (read from single-threaded observer)
    fun totalBytes(): Long = _bytesIn.get() + _bytesOut.get()

    /**
     * Get display name for UI.
     * Shows app name if available, otherwise simplifies package name.
     */
    fun getDisplayName(): String {
        return if (appName.isNotEmpty() && appName != packageName) {
            appName
        } else {
            packageName.split(".").lastOrNull() ?: packageName
        }
    }

    fun getIconIdentifier(): String = packageName

    /**
     * FIX: getConnectionSummary() no longer misleadingly uses .take(3).
     * If there are more than 3 destinations, it says "N destinations" accurately.
     */
    fun getConnectionSummary(): String {
        val destinations = activeFlows.values
            .map { flow: PacketFlow -> flow.key.dstIp }
            .distinct()
        return when {
            destinations.isEmpty() -> "No active connections"
            destinations.size == 1 -> "Connected to ${destinations[0]}"
            else -> "Connected to ${destinations.size} destinations"
        }
    }

    /**
     * Get dominant traffic category by byte volume.
     */
    fun getDominantCategory(): TrafficCategory {
        val categories = activeFlows.values
            .groupBy { flow -> flow.category }
            .mapValues { (_, flows) -> flows.sumOf { flow -> flow.byteCount } }
        return categories.maxByOrNull { entry -> entry.value }?.key ?: TrafficCategory.UNKNOWN
    }

    fun getQosDropRatePercent(): Double {
        val total = _qosAllowedPackets.get() + _qosDroppedPackets.get()
        if (total == 0L) return 0.0
        return (_qosDroppedPackets.get() * 100.0) / total
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppTraffic) return false
        return uid == other.uid
    }

    override fun hashCode(): Int = uid.hashCode()

    override fun toString(): String = "AppTraffic(uid=$uid, appName=$appName)"
}
