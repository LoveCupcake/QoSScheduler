package com.qos.scheduler.model

/**
 * Represents a single network connection/flow.
 */
data class PacketFlow(
    val key: FlowKey,
    var category: TrafficCategory,
    var byteCount: Long = 0,
    var lastSeen: Long = System.currentTimeMillis(),
    var ownerUid: Int? = null // Cache the UID here for performance
)
