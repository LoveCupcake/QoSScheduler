package com.qos.scheduler.model

import java.nio.ByteBuffer

/**
 * Parsed representation of an IPv4/IPv6 packet header.
 * Payload content is never stored — only header metadata.
 */
data class RawPacket(
    val ipVersion: Int,
    val srcIp: String,
    val dstIp: String,
    val srcPort: Int,
    val dstPort: Int,
    val protocol: Protocol,
    val length: Int,
    val ipHeaderLength: Int,
    val transportHeaderLength: Int,
    val payloadOffset: Int,
    val payloadLength: Int,
    val tcpSequenceNumber: Long? = null,
    val tcpAckNumber: Long? = null,
    val tcpWindowSize: Int? = null,
    val tcpFlags: TcpControlFlags? = null,
    val rawBuffer: ByteArray  // original bytes for forwarding
) {
    val isIpv4: Boolean get() = ipVersion == IPV4_VERSION
    val payload: ByteArray get() = rawBuffer.copyOfRange(payloadOffset, payloadOffset + payloadLength)

    companion object {
        private const val IPV4_VERSION = 4
        private const val IPV6_VERSION = 6

        /**
         * Parse a raw packet from the TUN interface buffer.
         * Returns null if the packet is malformed or unsupported.
         */
        fun parse(buffer: ByteArray, length: Int): RawPacket? {
            if (length < 20) return null
            val buf = ByteBuffer.wrap(buffer, 0, length)
            val versionIhl = buf.get(0).toInt() and 0xFF
            val version = versionIhl shr 4

            return when (version) {
                IPV4_VERSION -> parseIpv4(buf, buffer, length)
                IPV6_VERSION -> parseIpv6(buf, buffer, length)
                else -> null
            }
        }

        private fun parseIpv4(buf: ByteBuffer, raw: ByteArray, length: Int): RawPacket? {
            val ihl = (buf.get(0).toInt() and 0x0F) * 4
            if (length < ihl + 4) return null

            val protocolNum = buf.get(9).toInt() and 0xFF
            val protocol = Protocol.fromNumber(protocolNum)

            val srcIp = buildString {
                for (i in 12..15) {
                    if (i > 12) append('.')
                    append(buf.get(i).toInt() and 0xFF)
                }
            }
            val dstIp = buildString {
                for (i in 16..19) {
                    if (i > 16) append('.')
                    append(buf.get(i).toInt() and 0xFF)
                }
            }

            val (srcPort, dstPort) = extractPorts(buf, ihl, protocol)
            val tcpMetadata = extractTcpMetadata(buf, ihl, protocol, length)
            
            val transportHeaderLength = when (protocol) {
                Protocol.TCP -> tcpMetadata.headerLength
                Protocol.UDP -> 8
                else -> 0
            }
            
            val payloadOffset = (ihl + transportHeaderLength).coerceAtMost(length)
            val payloadLength = (length - payloadOffset).coerceAtLeast(0)

            return RawPacket(
                ipVersion = IPV4_VERSION,
                srcIp = srcIp,
                dstIp = dstIp,
                srcPort = srcPort,
                dstPort = dstPort,
                protocol = protocol,
                length = length,
                ipHeaderLength = ihl,
                transportHeaderLength = transportHeaderLength,
                payloadOffset = payloadOffset,
                payloadLength = payloadLength,
                tcpSequenceNumber = tcpMetadata.sequenceNumber,
                tcpAckNumber = tcpMetadata.ackNumber,
                tcpWindowSize = tcpMetadata.windowSize,
                tcpFlags = tcpMetadata.flags,
                rawBuffer = raw.copyOf(length)
            )
        }

        private fun parseIpv6(buf: ByteBuffer, raw: ByteArray, length: Int): RawPacket? {
            if (length < 40) return null
            
            var nextHeader = buf.get(6).toInt() and 0xFF
            var headerOffset = 40  // Start after fixed IPv6 header
            
            // Process extension headers
            while (isIpv6ExtensionHeader(nextHeader) && headerOffset < length) {
                if (headerOffset + 2 > length) return null
                
                val extNextHeader = buf.get(headerOffset).toInt() and 0xFF
                val extLength = when (nextHeader) {
                    0 -> (buf.get(headerOffset + 1).toInt() and 0xFF) * 8 + 8  // Hop-by-Hop
                    43 -> (buf.get(headerOffset + 1).toInt() and 0xFF) * 8 + 8 // Routing
                    44 -> 8  // Fragment (fixed 8 bytes)
                    60 -> (buf.get(headerOffset + 1).toInt() and 0xFF) * 8 + 8 // Destination Options
                    else -> return null  // Unknown extension header
                }
                
                nextHeader = extNextHeader
                headerOffset += extLength
            }
            
            val protocol = Protocol.fromNumber(nextHeader)
            val srcIp = formatIpv6(buf, 8)
            val dstIp = formatIpv6(buf, 24)
            val (srcPort, dstPort) = extractPorts(buf, headerOffset, protocol)
            val transportHeaderLength = if (protocol == Protocol.TCP && headerOffset + 13 < length) {
                ((buf.get(headerOffset + 12).toInt() ushr 4) and 0x0F) * 4
            } else {
                8
            }
            val payloadOffset = (headerOffset + transportHeaderLength).coerceAtMost(length)
            val payloadLength = (length - payloadOffset).coerceAtLeast(0)

            return RawPacket(
                ipVersion = IPV6_VERSION,
                srcIp = srcIp,
                dstIp = dstIp,
                srcPort = srcPort,
                dstPort = dstPort,
                protocol = protocol,
                length = length,
                ipHeaderLength = headerOffset,
                transportHeaderLength = transportHeaderLength,
                payloadOffset = payloadOffset,
                payloadLength = payloadLength,
                rawBuffer = raw.copyOf(length)
            )
        }
        
        private fun isIpv6ExtensionHeader(nextHeader: Int): Boolean {
            return when (nextHeader) {
                0,   // Hop-by-Hop Options
                43,  // Routing
                44,  // Fragment
                60   // Destination Options
                -> true
                else -> false
            }
        }

        private fun extractPorts(buf: ByteBuffer, headerEnd: Int, protocol: Protocol): Pair<Int, Int> {
            if (protocol == Protocol.OTHER) return Pair(0, 0)
            if (buf.capacity() < headerEnd + 4) return Pair(0, 0)
            val src = ((buf.get(headerEnd).toInt() and 0xFF) shl 8) or (buf.get(headerEnd + 1).toInt() and 0xFF)
            val dst = ((buf.get(headerEnd + 2).toInt() and 0xFF) shl 8) or (buf.get(headerEnd + 3).toInt() and 0xFF)
            return Pair(src, dst)
        }

        private fun extractTcpMetadata(
            buf: ByteBuffer,
            tcpOffset: Int,
            protocol: Protocol,
            length: Int
        ): TcpMetadata {
            if (protocol != Protocol.TCP || tcpOffset + 20 > length) {
                return TcpMetadata(0, null, null, null, null)
            }

            val sequenceNumber = buf.getInt(tcpOffset + 4).toLong() and 0xFFFFFFFFL
            val ackNumber = buf.getInt(tcpOffset + 8).toLong() and 0xFFFFFFFFL
            val dataOffset = ((buf.get(tcpOffset + 12).toInt() ushr 4) and 0x0F) * 4
            val flagsByte = buf.get(tcpOffset + 13).toInt() and 0xFF
            val windowSize = buf.getShort(tcpOffset + 14).toInt() and 0xFFFF
            val flags = TcpControlFlags(
                fin = (flagsByte and 0x01) != 0,
                syn = (flagsByte and 0x02) != 0,
                rst = (flagsByte and 0x04) != 0,
                psh = (flagsByte and 0x08) != 0,
                ack = (flagsByte and 0x10) != 0
            )
            return TcpMetadata(dataOffset, sequenceNumber, ackNumber, windowSize, flags)
        }

        private fun formatIpv6(buf: ByteBuffer, offset: Int): String {
            val segments = IntArray(8)
            for (i in 0 until 8) {
                val high = buf.get(offset + i * 2).toInt() and 0xFF
                val low = buf.get(offset + i * 2 + 1).toInt() and 0xFF
                segments[i] = (high shl 8) or low
            }
            
            // Find longest sequence of zeros for compression
            var maxZeroStart = -1
            var maxZeroLen = 0
            var currentZeroStart = -1
            var currentZeroLen = 0
            
            for (i in segments.indices) {
                if (segments[i] == 0) {
                    if (currentZeroStart == -1) {
                        currentZeroStart = i
                        currentZeroLen = 1
                    } else {
                        currentZeroLen++
                    }
                } else {
                    if (currentZeroLen > maxZeroLen) {
                        maxZeroStart = currentZeroStart
                        maxZeroLen = currentZeroLen
                    }
                    currentZeroStart = -1
                    currentZeroLen = 0
                }
            }
            
            // Check final sequence
            if (currentZeroLen > maxZeroLen) {
                maxZeroStart = currentZeroStart
                maxZeroLen = currentZeroLen
            }
            
            // Build IPv6 string with compression
            return buildString {
                var i = 0
                var justEmittedDoubleColon = false
                while (i < 8) {
                    if (i == maxZeroStart && maxZeroLen > 1) {
                        append("::")
                        i += maxZeroLen
                        justEmittedDoubleColon = true
                        if (i >= 8) break
                    } else {
                        if (i > 0 && !justEmittedDoubleColon) {
                            append(':')
                        }
                        justEmittedDoubleColon = false
                        append(String.format("%x", segments[i]))
                        i++
                    }
                }
            }
        }

        private data class TcpMetadata(
            val headerLength: Int,
            val sequenceNumber: Long?,
            val ackNumber: Long?,
            val windowSize: Int?,
            val flags: TcpControlFlags?
        )
    }
}

data class TcpControlFlags(
    val fin: Boolean = false,
    val syn: Boolean = false,
    val rst: Boolean = false,
    val psh: Boolean = false,
    val ack: Boolean = false
)
