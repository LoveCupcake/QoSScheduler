package com.qos.scheduler.service.relay

import java.nio.ByteBuffer

object Ipv4UdpPacketCodec {
    private const val IPV4_HEADER_LENGTH = 20
    private const val UDP_HEADER_LENGTH = 8

    fun buildUdpPacket(
        srcIp: String,
        dstIp: String,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val totalLength = IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH + payload.size
        val udpLength = UDP_HEADER_LENGTH + payload.size
        val packet = ByteArray(totalLength)
        val buffer = ByteBuffer.wrap(packet)

        buffer.put(0, 0x45.toByte())
        buffer.put(1, 0.toByte())
        buffer.putShort(2, totalLength.toShort())
        buffer.putShort(4, 0.toShort())
        buffer.putShort(6, 0x4000.toShort())
        buffer.put(8, 64.toByte())
        buffer.put(9, 17.toByte())

        val srcIpBytes = ipv4ToBytes(srcIp)
        val dstIpBytes = ipv4ToBytes(dstIp)
        srcIpBytes.copyInto(packet, 12)
        dstIpBytes.copyInto(packet, 16)

        buffer.putShort(20, srcPort.toShort())
        buffer.putShort(22, dstPort.toShort())
        buffer.putShort(24, udpLength.toShort())
        buffer.putShort(26, 0.toShort())
        payload.copyInto(packet, IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH)

        val ipChecksum = checksum(packet, 0, IPV4_HEADER_LENGTH)
        buffer.putShort(10, ipChecksum.toShort())

        val udpChecksum = udpChecksum(packet, srcIpBytes, dstIpBytes, udpLength)
        buffer.putShort(26, udpChecksum.toShort())

        return packet
    }

    private fun udpChecksum(packet: ByteArray, srcIp: ByteArray, dstIp: ByteArray, udpLength: Int): Int {
        val pseudoHeader = ByteArray(12 + udpLength)
        srcIp.copyInto(pseudoHeader, 0)
        dstIp.copyInto(pseudoHeader, 4)
        pseudoHeader[8] = 0
        pseudoHeader[9] = 17
        pseudoHeader[10] = (udpLength ushr 8).toByte()
        pseudoHeader[11] = (udpLength and 0xFF).toByte()
        packet.copyInto(pseudoHeader, 12, IPV4_HEADER_LENGTH, IPV4_HEADER_LENGTH + udpLength)
        val checksum = checksum(pseudoHeader, 0, pseudoHeader.size)
        return if (checksum == 0) 0xFFFF else checksum
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var index = offset

        while (index + 1 < offset + length) {
            sum += (((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)).toLong()
            index += 2
        }

        if (index < offset + length) {
            sum += ((data[index].toInt() and 0xFF) shl 8).toLong()
        }

        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }

        return sum.inv().toInt() and 0xFFFF
    }

    private fun ipv4ToBytes(ip: String): ByteArray {
        val parts = ip.split('.')
        require(parts.size == 4) { "Only IPv4 is supported by the DNS relay codec" }
        return ByteArray(4) { idx -> parts[idx].toInt().toByte() }
    }
}
