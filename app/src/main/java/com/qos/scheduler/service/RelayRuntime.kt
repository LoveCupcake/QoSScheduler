package com.qos.scheduler.service

enum class RelayRuntimeMode {
    MONITOR,
    RELAY_EXPERIMENTAL,
    RELAY_STABLE;

    fun displayName(): String = when (this) {
        MONITOR -> "Monitor"
        RELAY_EXPERIMENTAL -> "Relay Experimental"
        RELAY_STABLE -> "Relay Stable"
    }
}

enum class RelayFallbackReason {
    NONE,
    DEFAULT_MONITOR_MODE,
    TCP_ERROR_RATE,
    DNS_ERROR_RATE,
    QUEUE_PRESSURE,
    PROCESSING_STALL,
    UNSUPPORTED_TRAFFIC,
    SYSTEM_PROTECT_FAILED,
    TUN_INTERFACE_FAILED;

    fun userLabel(): String = when (this) {
        NONE -> "None"
        DEFAULT_MONITOR_MODE -> "Default monitor mode"
        TCP_ERROR_RATE -> "TCP relay error rate"
        DNS_ERROR_RATE -> "DNS relay error rate"
        QUEUE_PRESSURE -> "Queue pressure"
        PROCESSING_STALL -> "Processing stall"
        UNSUPPORTED_TRAFFIC -> "Unsupported traffic path"
        SYSTEM_PROTECT_FAILED -> "OS blocked socket protection"
        TUN_INTERFACE_FAILED -> "Failed to create TUN interface"
    }
}

data class RelayHealthSnapshot(
    val mode: RelayRuntimeMode = RelayRuntimeMode.MONITOR,
    val relayErrorRate: Double = 0.0,
    val dnsErrorRate: Double = 0.0,
    val activeFlowCount: Int = 0,
    val queueDepth: Int = 0,
    val fallbackReason: RelayFallbackReason = RelayFallbackReason.DEFAULT_MONITOR_MODE,
    val fallbackCount: Int = 0,
    val qosEnforced: Boolean = false
)
