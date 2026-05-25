package com.qos.scheduler.scheduler

/**
 * Software token bucket for per-bucket bandwidth enforcement.
 * Thread-safe via synchronized methods.
 *
 * @param rateBps   Sustained refill rate in bits/second
 * @param burstBits Maximum burst capacity in bits
 */
class TokenBucket(
    @Volatile var rateBps: Long,
    @Volatile var burstBits: Long
) {
    private var tokens: Double = burstBits.toDouble()
    private var lastRefillTime: Long = System.nanoTime()

    /**
     * Attempt to consume [bytes] tokens.
     * Returns true if allowed, false if the packet should be dropped.
     */
    @Synchronized
    fun consume(bytes: Int): Boolean {
        refill()
        val bitsNeeded = bytes.toDouble() * 8.0
        return if (tokens >= bitsNeeded) {
            tokens -= bitsNeeded
            true
        } else {
            false
        }
    }

    /**
     * Update rate AND burst together — burst is explicitly provided,
     * not recalculated as a fixed fraction of rate.
     * This replaces the old setRate() which silently overwrote burst to rate/10.
     */
    @Synchronized
    fun setRateAndBurst(newRateBps: Long, newBurstBits: Long) {
        rateBps = newRateBps
        burstBits = newBurstBits.coerceAtLeast(1L) // burst of 0 would starve forever
        tokens = minOf(tokens, burstBits.toDouble())
    }

    /**
     * Legacy single-parameter rate update — keeps burst at its current value.
     * Only used for manual caps where burst is not being rebalanced.
     */
    @Synchronized
    fun setRate(newRateBps: Long) {
        rateBps = newRateBps
        // Do NOT touch burstBits here — caller should use setRateAndBurst() if burst matters.
        tokens = minOf(tokens, burstBits.toDouble())
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsed = (now - lastRefillTime) / 1_000_000_000.0 // seconds
        tokens = minOf(burstBits.toDouble(), tokens + rateBps * elapsed)
        lastRefillTime = now
    }
}
