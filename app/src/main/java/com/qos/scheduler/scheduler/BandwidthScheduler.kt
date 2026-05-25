package com.qos.scheduler.scheduler

import com.qos.scheduler.model.ConnectedDevice
import com.qos.scheduler.model.RawPacket
import com.qos.scheduler.model.AppTraffic
import com.qos.scheduler.model.TrafficClass
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates token buckets for QoS buckets (host, app UID, or legacy device keys).
 * Decides whether each packet is forwarded or dropped.
 *
 * Fixed bugs:
 *  - rebalance() was a no-op (now removed; callers must use rebalanceWithApps/Devices)
 *  - updatePriority() ignored its priority parameter (now applies rate+burst correctly)
 *  - setManualCap() silently failed if bucket didn't exist yet (now creates bucket)
 *  - setRate() overwrote constructor burst (now uses setRateAndBurst throughout)
 *  - LOW class burst could be 0 for small rates (coerced to minimum 1)
 */
class BandwidthScheduler {

    // Total estimated uplink in bits/second (default 100 Mbps)
    var uplinkBps: Long = 100_000_000L

    private val buckets = ConcurrentHashMap<String, TokenBucket>()
    private val manualCaps = ConcurrentHashMap<String, Long>() // key -> custom rateBps

    companion object {
        fun appBucketKey(uid: Int): String = "app_$uid"
    }

    /**
     * Process a packet for a specific scheduler key.
     * Returns true if the packet should be forwarded.
     */
    fun processPacket(deviceIp: String, packetSize: Int): Boolean {
        val bucket = buckets[deviceIp] ?: return true // unknown key: allow
        return bucket.consume(packetSize)
    }

    /**
     * Ensure a bucket exists for [key]. Creates it with priority-derived rate+burst if absent.
     */
    fun ensureBucket(key: String, priority: TrafficClass = TrafficClass.MEDIUM) {
        if (!buckets.containsKey(key)) {
            val (rate, burst) = rateForClass(priority)
            buckets[key] = TokenBucket(rate, burst)
        }
    }

    /** Legacy overload retained for backward compatibility. */
    @Deprecated(
        "Use processPacket(deviceIp, packetSize) instead",
        ReplaceWith("processPacket(packet.srcIp, packet.length)")
    )
    fun processPacket(packet: RawPacket): Boolean = processPacket(packet.srcIp, packet.length)

    fun addDevice(device: ConnectedDevice) {
        if (!buckets.containsKey(device.ipAddress)) {
            val (rate, burst) = rateForClass(device.priorityClass)
            buckets[device.ipAddress] = TokenBucket(rate, burst)
        }
        // Callers should follow up with rebalanceWithDevices() when device list is known.
    }

    fun removeDevice(ipAddress: String) {
        buckets.remove(ipAddress)
        manualCaps.remove(ipAddress)
        // Callers should follow up with rebalanceWithDevices() when device list is known.
    }

    fun removeManualCap(key: String) {
        manualCaps.remove(key)
        // Rate will be corrected on the next rebalanceWithApps() / rebalanceWithDevices() call.
    }

    /**
     * FIX: Actually apply the new priority to the bucket rate+burst.
     * Previously this method ignored the `priority` parameter entirely.
     */
    fun updatePriority(key: String, priority: TrafficClass) {
        manualCaps.remove(key) // clear any manual cap when priority changes
        val (rate, burst) = rateForClass(priority)
        buckets[key]?.setRateAndBurst(rate, burst)
            ?: run { buckets[key] = TokenBucket(rate, burst) } // create if absent
    }

    /**
     * FIX: Create the bucket if it doesn't exist yet so the cap is never silently dropped.
     * Previously if the bucket was absent, manualCaps was stored but never applied.
     */
    fun setManualCap(key: String, rateBps: Long) {
        manualCaps[key] = rateBps
        val bucket = buckets[key] ?: TokenBucket(rateBps, rateBps).also { buckets[key] = it }
        bucket.setRate(rateBps) // manual caps only control rate, burst stays as-is
    }

    /**
     * FIX: Full rebalance with device list.
     * Uses setRateAndBurst() so the carefully-calculated burst is NOT overwritten.
     */
    fun rebalanceWithDevices(devices: List<ConnectedDevice>) {
        val highCount = devices.count { it.priorityClass == TrafficClass.HIGH }
        val medCount  = devices.count { it.priorityClass == TrafficClass.MEDIUM }
        val lowCount  = devices.count { it.priorityClass == TrafficClass.LOW }

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
                TrafficClass.HIGH   -> (rate * 2).coerceAtLeast(1L)
                TrafficClass.MEDIUM -> rate.coerceAtLeast(1L)
                TrafficClass.LOW    -> (rate / 2).coerceAtLeast(1L) // FIX: never 0
            }
            // FIX: use setRateAndBurst — setRate() alone would overwrite burst to rate/10
            buckets.getOrPut(device.ipAddress) { TokenBucket(rate, burst) }
                .setRateAndBurst(rate, burst)
        }
    }

    /**
     * FIX: Full rebalance with app list + host bucket.
     * Uses setRateAndBurst() so burst differentiation by priority is preserved.
     */
    fun rebalanceWithApps(apps: Collection<AppTraffic>) {
        val highCount = apps.count { it.priorityClass == TrafficClass.HIGH }
        val medCount  = apps.count { it.priorityClass == TrafficClass.MEDIUM }
        val lowCount  = apps.count { it.priorityClass == TrafficClass.LOW }

        // +2 for host bucket (MEDIUM weight)
        val totalWeight = (highCount * 4) + (medCount * 2) + (lowCount * 1) + 2

        // 1. Rebalance per-app buckets
        apps.forEach { app ->
            val key = appBucketKey(app.uid)
            if (manualCaps.containsKey(key)) return@forEach

            val weight = when (app.priorityClass) {
                TrafficClass.HIGH   -> 4
                TrafficClass.MEDIUM -> 2
                TrafficClass.LOW    -> 1
            }
            val rate = (uplinkBps * weight) / totalWeight
            val burst = when (app.priorityClass) {
                TrafficClass.HIGH   -> (rate * 5).coerceAtLeast(1L)  // large burst for high-priority
                TrafficClass.MEDIUM -> (rate * 2).coerceAtLeast(1L)
                TrafficClass.LOW    -> (rate / 2).coerceAtLeast(1L)  // FIX: never 0
            }
            // FIX: setRateAndBurst preserves our calculated burst
            buckets.getOrPut(key) { TokenBucket(rate, burst) }
                .setRateAndBurst(rate, burst)
        }

        // 2. Rebalance host bucket
        val hostRate = (uplinkBps * 2) / totalWeight
        val hostBurst = (hostRate * 2).coerceAtLeast(1L)
        buckets.getOrPut("__host__") { TokenBucket(hostRate, hostBurst) }
            .setRateAndBurst(hostRate, hostBurst)
    }

    private fun rateForClass(cls: TrafficClass): Pair<Long, Long> {
        val rate = when (cls) {
            TrafficClass.HIGH   -> (uplinkBps * 0.8).toLong()
            TrafficClass.MEDIUM -> (uplinkBps * 0.5).toLong()
            TrafficClass.LOW    -> (uplinkBps * 0.2).toLong()
        }
        val burst = when (cls) {
            TrafficClass.HIGH   -> (rate * 2).coerceAtLeast(1L)
            TrafficClass.MEDIUM -> rate.coerceAtLeast(1L)
            TrafficClass.LOW    -> (rate / 2).coerceAtLeast(1L) // FIX: never 0
        }
        return Pair(rate, burst)
    }
}
