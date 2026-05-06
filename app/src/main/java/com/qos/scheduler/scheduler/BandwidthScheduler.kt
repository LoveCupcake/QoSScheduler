package com.qos.scheduler.scheduler

import com.qos.scheduler.model.ConnectedDevice
import com.qos.scheduler.model.RawPacket
import com.qos.scheduler.model.AppTraffic
import com.qos.scheduler.model.TrafficClass
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates token buckets for QoS buckets (host, app UID, or legacy device keys).
 * Decides whether each packet is forwarded or dropped.
 */
class BandwidthScheduler {

    // Total estimated uplink in bytes/second (default 50 Mbps for modern networks)
    var uplinkBps: Long = 50_000_000L

    private val buckets = ConcurrentHashMap<String, TokenBucket>()
    private val manualCaps = ConcurrentHashMap<String, Long>() // ip -> custom rateBps

    companion object {
        fun appBucketKey(uid: Int): String = "app_$uid"
    }

    /**
     * Process a packet for a specific scheduler key. Returns true if the packet should be forwarded.
     * @param deviceIp Scheduler bucket key (legacy name kept for compatibility)
     * @param packetSize The size of the packet in bytes
     */
    fun processPacket(deviceIp: String, packetSize: Int): Boolean {
        val bucket = buckets[deviceIp] ?: return true // unknown device: allow
        return bucket.consume(packetSize)
    }

    fun ensureBucket(key: String, priority: TrafficClass = TrafficClass.MEDIUM) {
        if (!buckets.containsKey(key)) {
            val (rate, burst) = rateForClass(priority)
            buckets[key] = TokenBucket(rate, burst)
        }
    }
    
    /**
     * Process a packet using packet's source IP (legacy method for backward compatibility).
     * Returns true if the packet should be forwarded.
     */
    @Deprecated("Use processPacket(deviceIp, packetSize) instead", ReplaceWith("processPacket(packet.srcIp, packet.length)"))
    fun processPacket(packet: RawPacket): Boolean {
        return processPacket(packet.srcIp, packet.length)
    }

    fun addDevice(device: ConnectedDevice) {
        if (!buckets.containsKey(device.ipAddress)) {
            val (rate, burst) = rateForClass(device.priorityClass)
            buckets[device.ipAddress] = TokenBucket(rate, burst)
        }
        rebalance()
    }

    fun removeDevice(ipAddress: String) {
        buckets.remove(ipAddress)
        manualCaps.remove(ipAddress)
        rebalance()
    }

    fun updatePriority(ipAddress: String, priority: TrafficClass) {
        manualCaps.remove(ipAddress) // clear manual cap when priority changes
        rebalance()
    }

    fun setManualCap(ipAddress: String, rateBps: Long) {
        manualCaps[ipAddress] = rateBps
        buckets[ipAddress]?.setRate(rateBps)
    }

    /**
     * Recalculate all bucket rates based on current device priorities and uplink.
     */
    fun rebalance() {
        // Rates are proportional to priority weight
        // HIGH=4, MEDIUM=2, LOW=1 (weighted fair queuing approximation)
        // This is called after any topology change
        buckets.keys.forEach { ip ->
            if (!manualCaps.containsKey(ip)) {
                // rate will be set properly when we have device info
                // BandwidthScheduler is called with device list from DeviceRegistry
            }
        }
    }

    /**
     * Full rebalance with device list — called by QosVpnService after any change.
     */
    fun rebalanceWithDevices(devices: List<ConnectedDevice>) {
        val highCount = devices.count { it.priorityClass == TrafficClass.HIGH }
        val medCount  = devices.count { it.priorityClass == TrafficClass.MEDIUM }
        val lowCount  = devices.count { it.priorityClass == TrafficClass.LOW }

        // Total weight units
        val totalWeight = highCount * 4 + medCount * 2 + lowCount * 1
        if (totalWeight == 0) return

        devices.forEach { device ->
            if (manualCaps.containsKey(device.ipAddress)) return@forEach
            val weight = when (device.priorityClass) {
                TrafficClass.HIGH   -> 4
                TrafficClass.MEDIUM -> 2
                TrafficClass.LOW    -> 1
            }
            val rate = (uplinkBps * weight) / totalWeight
            val burst = when (device.priorityClass) {
                TrafficClass.HIGH   -> rate * 2
                TrafficClass.MEDIUM -> rate
                TrafficClass.LOW    -> rate / 2
            }
            buckets.getOrPut(device.ipAddress) { TokenBucket(rate, burst) }
                .setRate(rate)
        }
    }

    /**
     * Full rebalance with apps and host bucket.
     */
    fun rebalanceWithApps(apps: Collection<AppTraffic>) {
        val highCount = apps.count { it.priorityClass == TrafficClass.HIGH }
        val medCount = apps.count { it.priorityClass == TrafficClass.MEDIUM }
        val lowCount = apps.count { it.priorityClass == TrafficClass.LOW }
        
        // Include "__host__" as a MEDIUM priority bucket in the calculation
        val totalWeight = (highCount * 4) + (medCount * 2) + (lowCount * 1) + 2 // +2 for host
        if (totalWeight <= 0) return

        // 1. Rebalance apps
        apps.forEach { app ->
            val key = appBucketKey(app.uid)
            if (manualCaps.containsKey(key)) return@forEach

            val weight = when (app.priorityClass) {
                TrafficClass.HIGH -> 4
                TrafficClass.MEDIUM -> 2
                TrafficClass.LOW -> 1
            }
            val rate = (uplinkBps * weight) / totalWeight
            buckets.getOrPut(key) { TokenBucket(rate, rate * 5) }
                .setRate(rate)
        }

        // 2. Rebalance host bucket
        val hostRate = (uplinkBps * 2) / totalWeight
        buckets.getOrPut("__host__") { TokenBucket(hostRate, hostRate * 5) }
            .setRate(hostRate)
    }

    private fun rateForClass(cls: TrafficClass): Pair<Long, Long> {
        val rate = when (cls) {
            TrafficClass.HIGH   -> (uplinkBps * 0.8).toLong()
            TrafficClass.MEDIUM -> (uplinkBps * 0.5).toLong()
            TrafficClass.LOW    -> (uplinkBps * 0.2).toLong()
        }
        val burst = when (cls) {
            TrafficClass.HIGH   -> rate * 2
            TrafficClass.MEDIUM -> rate
            TrafficClass.LOW    -> rate / 2
        }
        return Pair(rate, burst)
    }
}
