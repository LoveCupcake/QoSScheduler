package com.qos.scheduler.service.dataplane

import com.qos.scheduler.classifier.DpiClassifier
import com.qos.scheduler.model.AppTraffic
import com.qos.scheduler.model.FlowKey
import com.qos.scheduler.model.PacketFlow
import com.qos.scheduler.model.Protocol
import com.qos.scheduler.model.RawPacket
import com.qos.scheduler.model.TrafficClass
import com.qos.scheduler.scheduler.BandwidthScheduler
import com.qos.scheduler.util.AppResolver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DataPlaneProcessor(
    private val classifier: DpiClassifier,
    private val scheduler: BandwidthScheduler,
    private val appResolver: AppResolver,
    private val appTraffic: ConcurrentHashMap<Int, AppTraffic>,
    private val hostBucketKey: String
) {
    companion object {
        /**
         * FIX: flowCache is now an instance field, not a companion object static.
         * Previously it was in companion object so it persisted across service restarts,
         * accumulating stale FlowKey->UID mappings forever (memory leak + misattribution).
         *
         * Moving it here means it is cleared when the service (and DataPlaneProcessor) is recreated.
         */
    }

    // FIX: instance-level cache — cleared on service restart
    private val flowCache = ConcurrentHashMap<FlowKey, Int>()

    data class Result(
        val parsed: Boolean,
        val droppedByScheduler: Boolean,
        val qosKey: String,
        val packet: RawPacket? = null
    )

    /**
     * FIX: Actually evict the flow from the cache so it doesn't grow unboundedly.
     * Previously this was a no-op stub with a comment "Don't clear flowCache".
     */
    fun clearFlow(flowKey: FlowKey) {
        flowCache.remove(flowKey)
    }

    fun process(bytes: ByteArray, length: Int, isOutbound: Boolean): Result {
        val parsedPacket = RawPacket.parse(bytes, length)
            ?: return Result(parsed = false, droppedByScheduler = false, qosKey = hostBucketKey)

        val flowKey = if (isOutbound) {
            FlowKey(parsedPacket.srcIp, parsedPacket.dstIp, parsedPacket.srcPort, parsedPacket.dstPort, parsedPacket.protocol)
        } else {
            FlowKey(parsedPacket.dstIp, parsedPacket.srcIp, parsedPacket.dstPort, parsedPacket.srcPort, parsedPacket.protocol)
        }

        // ============================================================
        // STEP 1: SYNCHRONOUS UID RESOLUTION (fast path, ~1ms)
        // ============================================================
        var finalUid = flowCache[flowKey]

        if (finalUid == null) {
            val protocolNum = if (parsedPacket.protocol == Protocol.TCP) 6 else 17
            val resolvedUid = if (isOutbound) {
                appResolver.getUidForConnection(protocolNum, parsedPacket.srcIp, parsedPacket.srcPort, parsedPacket.dstIp, parsedPacket.dstPort)
            } else {
                appResolver.getUidForConnection(protocolNum, parsedPacket.dstIp, parsedPacket.dstPort, parsedPacket.srcIp, parsedPacket.srcPort)
            }

            if (resolvedUid != null && resolvedUid > 0) {
                flowCache[flowKey] = resolvedUid
                finalUid = resolvedUid

                val appInfo = appResolver.getAppInfo(resolvedUid)
                android.util.Log.d("AppResolver", "[OK] Flow identified: ${parsedPacket.dstIp}:${parsedPacket.dstPort} -> ${appInfo.appName} (UID: $resolvedUid)")

                // RETROACTIVE MIGRATION: move this specific flow from synthetic port bucket to real app.
                // FIX: Only migrate the bytes from the ONE flow being moved, not all bytes from the
                // synthetic bucket (which could represent many different apps on the same port).
                val port = if (isOutbound) parsedPacket.dstPort else parsedPacket.srcPort
                val syntheticUid = -(port + 1)
                val syntheticApp = appTraffic[syntheticUid]
                if (syntheticApp != null) {
                    val realApp = appTraffic.getOrPut(resolvedUid) {
                        AppTraffic(uid = resolvedUid, appName = appInfo.appName, packageName = appInfo.packageName, priorityClass = TrafficClass.MEDIUM)
                    }
                    // FIX: migrate only the byte count of this specific flow, not all of syntheticApp
                    syntheticApp.activeFlows.remove(flowKey)?.let { migratedFlow ->
                        migratedFlow.ownerUid = resolvedUid
                        realApp.activeFlows[flowKey] = migratedFlow
                        // Transfer only the bytes carried by this one flow
                        val flowBytes = migratedFlow.byteCount
                        if (isOutbound) realApp.addBytesOut(flowBytes) else realApp.addBytesIn(flowBytes)
                        android.util.Log.i("AppResolver", "Migrated flow ($flowBytes bytes) from Port $port to ${appInfo.appName}")
                    }
                    if (syntheticApp.activeFlows.isEmpty()) {
                        appTraffic.remove(syntheticUid)
                    }
                }
            } else if (resolvedUid == -1) {
                // FIX: Do NOT permanently cache UID=-1. Kernel socket ownership is transient;
                // the same flow will be owned by a real app shortly after the process starts.
                // We intentionally skip caching here so the next packet retries the lookup.
                finalUid = -1
            }
            // resolvedUid == null → pre-Android Q or resolution pending → leave finalUid null
        }

        // ============================================================
        // STEP 2: CLASSIFY & TRACK
        // ============================================================
        val trafficCategory = classifier.classify(parsedPacket)
        var schedulerKey = hostBucketKey

        if (finalUid != null && finalUid > 0) {
            // Real app identified
            val app = appTraffic.getOrPut(finalUid) {
                val appInfo = appResolver.getAppInfo(finalUid)
                AppTraffic(uid = finalUid, appName = appInfo.appName, packageName = appInfo.packageName, priorityClass = TrafficClass.MEDIUM)
            }
            // FIX: use correct schedulerKey for this app (was using hostBucketKey in else branch)
            schedulerKey = BandwidthScheduler.appBucketKey(finalUid)

            val flow = app.activeFlows.getOrPut(flowKey) {
                PacketFlow(key = flowKey, category = trafficCategory, ownerUid = finalUid)
            }
            flow.category = trafficCategory
            flow.byteCount += length
            flow.lastSeen = System.currentTimeMillis()
            // FIX: thread-safe increments via synchronized AppTraffic helpers
            if (isOutbound) app.addBytesOut(length.toLong()) else app.addBytesIn(length.toLong())
            app.updateLastSeen()
            scheduler.ensureBucket(schedulerKey, app.priorityClass)

        } else {
            // UID unknown — track under synthetic port bucket temporarily
            val port = if (isOutbound) parsedPacket.dstPort else parsedPacket.srcPort
            val syntheticUid = -(port + 1)
            val portLabel = when (port) {
                0    -> "System/ICMP Traffic"
                53   -> "DNS Traffic"
                853  -> "Secure DNS (DoT)"
                80   -> "HTTP Traffic"
                443  -> "HTTPS Traffic"
                else -> if (parsedPacket.isIpv4) "Unknown Traffic (Port $port)" else "System/IPv6 Traffic"
            }

            val app = appTraffic.getOrPut(syntheticUid) {
                AppTraffic(uid = syntheticUid, appName = portLabel, packageName = "port.$port", priorityClass = TrafficClass.MEDIUM)
            }
            // FIX: use synthetic app's scheduler key (not hostBucketKey)
            val syntheticSchedulerKey = BandwidthScheduler.appBucketKey(syntheticUid)
            val flow = app.activeFlows.getOrPut(flowKey) {
                PacketFlow(key = flowKey, category = trafficCategory, ownerUid = syntheticUid)
            }
            flow.category = trafficCategory
            flow.byteCount += length
            flow.lastSeen = System.currentTimeMillis()
            if (isOutbound) app.addBytesOut(length.toLong()) else app.addBytesIn(length.toLong())
            app.updateLastSeen()
            scheduler.ensureBucket(syntheticSchedulerKey, app.priorityClass)
            schedulerKey = syntheticSchedulerKey
        }

        // ============================================================
        // STEP 3: SCHEDULER DECISION
        // ============================================================
        val allowed = scheduler.processPacket(schedulerKey, length)

        if (finalUid != null && finalUid > 0) {
            // FIX: read app once to avoid TOCTOU double-read
            val app = appTraffic[finalUid]
            if (app != null) {
                if (allowed) app.incrementAllowed() else app.incrementDropped()
            }
        }

        return Result(parsed = true, droppedByScheduler = !allowed, qosKey = schedulerKey, packet = parsedPacket)
    }
}
