package com.qos.scheduler.classifier

import com.qos.scheduler.model.Protocol
import com.qos.scheduler.model.RawPacket
import com.qos.scheduler.model.TrafficCategory

/**
 * Intelligent Deep Packet Inspection (DPI) Lite.
 * Classifies traffic based on ports, protocols, and simple payload heuristics.
 */
class DpiClassifier {

    fun classify(packet: RawPacket): TrafficCategory {
        val port = packet.dstPort
        val protocol = packet.protocol

        // 1. VOIP / Real-time (High Priority)
        if (protocol == Protocol.UDP) {
            if (port in 5060..5061 || port in 10000..20000) return TrafficCategory.VOIP
        }

        // 2. Gaming (High Priority)
        val gamingPorts = listOf(
            27015, 27016, // Steam
            5000, 5500,   // Mobile Legends
            8001, 8002    // Various mobile games
        )
        if (gamingPorts.any { it is Int && it == port || it is IntRange && port in it }) {
            return TrafficCategory.ONLINE_GAMING
        }

        // 3. DNS (Infrastructure - High Priority)
        if (port == 53) return TrafficCategory.WEB_BROWSING

        // 4. Video Streaming (Medium Priority)
        if (port == 1935 || port == 8080) return TrafficCategory.STREAMING

        // 5. Web / Social (Standard Priority)
        if (port == 80 || port == 443) {
            return TrafficCategory.WEB_BROWSING
        }

        // 6. Downloads (Low Priority)
        val downloadPorts = listOf(21, 22, 143, 993, 110, 995)
        if (downloadPorts.contains(port)) return TrafficCategory.FILE_TRANSFER

        return TrafficCategory.UNKNOWN
    }
}
