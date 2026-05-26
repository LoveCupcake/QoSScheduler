package com.qos.scheduler.util

import android.util.Log
import com.qos.scheduler.model.AppTraffic
import java.util.concurrent.ConcurrentHashMap

/**
 * Diagnostic tool for collecting experimental metrics for the thesis.
 */
class DiagnosticTool(
    private val appTraffic: ConcurrentHashMap<Int, AppTraffic>
) {
    private val TAG = "QoS-Stats"
    private val lastByteCounts = mutableMapOf<Int, Long>()
    private var lastCheckTime = System.currentTimeMillis()

    /**
     * Periodically calculates and logs statistics in a CSV-friendly format.
     * Format: DATA_POINT,Timestamp,AppName,Priority,Mbps,DropRate%,TotalPackets
     */
    fun report() {
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastCheckTime) / 1000.0
        if (deltaTime <= 0.1) return

        appTraffic.forEach { (uid, app) ->
            val currentTotalBytes = app.bytesIn + app.bytesOut
            val previousTotalBytes = lastByteCounts[uid] ?: 0L
            
            val diffBytes = currentTotalBytes - previousTotalBytes
            val mbps = (diffBytes * 8.0) / (1_000_000.0 * deltaTime)
            
            // Update model for UI display
            app.currentThroughputBps = (mbps * 1_000_000.0 / 8.0).toLong()
            
            // Log only active traffic to keep Logcat clean
            if (mbps > 0.001 || app.qosDroppedPackets > 0) {
                val dropRate = app.getQosDropRatePercent()
                Log.i(TAG, String.format(
                    "DATA_POINT,%d,%s,%s,%.3f,%.1f,%d",
                    currentTime / 1000,
                    app.getDisplayName(),
                    app.priorityClass.name,
                    mbps,
                    dropRate,
                    app.qosAllowedPackets + app.qosDroppedPackets
                ))
            }
            
            lastByteCounts[uid] = currentTotalBytes
        }
        
        lastCheckTime = currentTime
    }
}
