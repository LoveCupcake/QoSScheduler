package com.qos.scheduler.scheduler

/**
 * Software token bucket for per-bucket bandwidth enforcement.
 * Thread-safe via synchronized methods.
 *
 * @param rateBps   Sustained refill rate in bytes/second
 * @param burstBytes Maximum burst capacity in bytes
 */
class TokenBucket(
    @Volatile var rateBps: Long,
    @Volatile var burstBytes: Long
) {
    private var tokens: Double = burstBytes.toDouble()
    private var lastRefillTime: Long = System.nanoTime()

    /**
     * Attempt to consume [bytes] tokens.
     * Returns true if allowed, false if the packet should be dropped.
     */
    @Synchronized
    fun consume(bytes: Int): Boolean {
        refill()
        return if (tokens >= bytes) {
            tokens -= bytes
            true
        } else {
            false
        }
    }

    @Synchronized
    fun setRate(newRateBps: Long) {
        rateBps = newRateBps
        // Recalculate burst proportionally
        burstBytes = newRateBps * 2
        tokens = minOf(tokens, burstBytes.toDouble())
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsed = (now - lastRefillTime) / 1_000_000_000.0 // seconds
        tokens = minOf(burstBytes.toDouble(), tokens + rateBps * elapsed)
        lastRefillTime = now
    }
}
