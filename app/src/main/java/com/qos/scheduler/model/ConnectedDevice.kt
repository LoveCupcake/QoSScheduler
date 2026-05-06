package com.qos.scheduler.model

data class ConnectedDevice(
    val ipAddress: String,
    val macAddress: String? = null,
    var hostname: String? = null,
    var priorityClass: TrafficClass = TrafficClass.MEDIUM,
    var bytesIn: Long = 0L,
    var bytesOut: Long = 0L,
    var currentThroughputBps: Long = 0L,
    var lastSeenTimestamp: Long = System.currentTimeMillis(),
    val activeFlows: MutableMap<FlowKey, PacketFlow> = mutableMapOf()
) {
    val displayName: String get() = hostname ?: ipAddress
}
