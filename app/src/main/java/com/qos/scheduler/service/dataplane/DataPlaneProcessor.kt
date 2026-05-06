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

class DataPlaneProcessor(
    private val classifier: DpiClassifier,
    private val scheduler: BandwidthScheduler,
    private val appResolver: AppResolver,
    private val appTraffic: ConcurrentHashMap<Int, AppTraffic>,
    private val hostBucketKey: String
) {
    companion object {
        // Static caches survive service restarts
        private val flowCache = ConcurrentHashMap<FlowKey, Int>()
    }

    data class Result(
        val parsed: Boolean,
        val droppedByScheduler: Boolean,
        val qosKey: String,
        val packet: RawPacket? = null
    )

    fun clearFlow(flowKey: FlowKey) {
        // Don't clear flowCache — maintain memory of app-to-flow mappings
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
            // Try to resolve UID RIGHT NOW, synchronously on this thread.
            // getConnectionOwnerUid is a fast syscall (~1ms), no need for async.
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

                // RETROACTIVE MIGRATION: If this flow was previously tracked under a
                // synthetic port bucket, move its accumulated traffic to the real app.
                val port = if (isOutbound) parsedPacket.dstPort else parsedPacket.srcPort
                val syntheticUid = -(port + 1)
                val syntheticApp = appTraffic[syntheticUid]
                if (syntheticApp != null) {
                    val realApp = appTraffic.getOrPut(resolvedUid) {
                        AppTraffic(uid = resolvedUid, appName = appInfo.appName, packageName = appInfo.packageName, priorityClass = TrafficClass.MEDIUM)
                    }
                    // Migrate the specific flow from synthetic to real app
                    syntheticApp.activeFlows.remove(flowKey)?.let { migratedFlow ->
                        migratedFlow.ownerUid = resolvedUid
                        realApp.activeFlows[flowKey] = migratedFlow
                        realApp.bytesIn += syntheticApp.bytesIn
                        realApp.bytesOut += syntheticApp.bytesOut
                        android.util.Log.i("AppResolver", "Migrated traffic from Port $port to ${appInfo.appName}")
                    }
                    // Remove synthetic entry if it has no more flows
                    if (syntheticApp.activeFlows.isEmpty()) {
                        appTraffic.remove(syntheticUid)
                    }
                }
            }
            // If not found, DON'T cache. Next packet will retry.
        }

        // ============================================================
        // STEP 2: CLASSIFY & TRACK
        // ============================================================
        val trafficCategory = classifier.classify(parsedPacket)
        var schedulerKey = hostBucketKey

        if (finalUid != null && finalUid > 0) {
            // Real App identified — track under its name
            val app = appTraffic.getOrPut(finalUid) {
                val appInfo = appResolver.getAppInfo(finalUid)
                AppTraffic(uid = finalUid, appName = appInfo.appName, packageName = appInfo.packageName, priorityClass = TrafficClass.MEDIUM)
            }
            schedulerKey = BandwidthScheduler.appBucketKey(finalUid)

            val flow = app.activeFlows.getOrPut(flowKey) {
                PacketFlow(key = flowKey, category = trafficCategory, ownerUid = finalUid)
            }
            flow.category = trafficCategory
            flow.byteCount += length
            flow.lastSeen = System.currentTimeMillis()
            if (isOutbound) app.bytesOut += length else app.bytesIn += length
            app.lastSeenTimestamp = System.currentTimeMillis()
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
            val flow = app.activeFlows.getOrPut(flowKey) {
                PacketFlow(key = flowKey, category = trafficCategory, ownerUid = syntheticUid)
            }
            flow.category = trafficCategory
            flow.byteCount += length
            flow.lastSeen = System.currentTimeMillis()
            if (isOutbound) app.bytesOut += length else app.bytesIn += length
            app.lastSeenTimestamp = System.currentTimeMillis()
            scheduler.ensureBucket(schedulerKey, app.priorityClass)
        }

        // ============================================================
        // STEP 3: SCHEDULER DECISION
        // ============================================================
        val allowed = if (isOutbound) {
            scheduler.processPacket(schedulerKey, length)
        } else {
            true
        }

        if (finalUid != null && finalUid > 0) {
            if (allowed) {
                appTraffic[finalUid]?.qosAllowedPackets = (appTraffic[finalUid]?.qosAllowedPackets ?: 0) + 1
            } else {
                appTraffic[finalUid]?.qosDroppedPackets = (appTraffic[finalUid]?.qosDroppedPackets ?: 0) + 1
            }
        }

        return Result(parsed = true, droppedByScheduler = !allowed, qosKey = schedulerKey, packet = parsedPacket)
    }
}
