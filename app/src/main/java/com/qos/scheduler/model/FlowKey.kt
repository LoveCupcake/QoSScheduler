package com.qos.scheduler.model

/**
 * Unique identifier for a network flow.
 */
data class FlowKey(
    val srcIp: String,
    val dstIp: String,
    val srcPort: Int,
    val dstPort: Int,
    val protocol: Protocol
)
