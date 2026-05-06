package com.qos.scheduler.service.relay

import com.qos.scheduler.model.TcpControlFlags
import java.nio.ByteBuffer
import kotlin.random.Random

object Ipv4TcpPacketCodec {
    private const val IPV4_HEADER_LENGTH = 20
    private const val TCP_HEADER_LENGTH = 20

    fun randomInitialSequence(): Long = Random.nextInt().toLong() and 0xFFFFFFFFL

    fun buildTcpPacket(
        srcIp: String,
        dstIp: String,
        srcPort: Int,
        dstPort: Int,
        sequenceNumber: Long,
        ackNumber: Long,
        flags: TcpControlFlags,
        payload: ByteArray = ByteArray(0),
        windowSize: Int = 65535
    ): ByteArray {
        val totalLength = IPV4_HEADER_LENGTH + TCP_HEADER_LENGTH + payload.size
        val packet = ByteArray(totalLength)
        val buffer = ByteBuffer.wrap(packet)

        buffer.put(0, 0x45.toByte())
        buffer.put(1, 0.toByte())
        buffer.putShort(2, totalLength.toShort())
        buffer.putShort(4, 0.toShort())
        buffer.putShort(6, 0x4000.toShort())
        buffer.put(8, 64.toByte())
        buffer.put(9, 6.toByte())

        val srcIpBytes = ipv4ToBytes(srcIp)
        val dstIpBytes = ipv4ToBytes(dstIp)
        srcIpBytes.copyInto(packet, 12)
        dstIpBytes.copyInto(packet, 16)

        buffer.putShort(20, srcPort.toShort())
        buffer.putShort(22, dstPort.toShort())
        buffer.putInt(24, sequenceNumber.toInt())
        buffer.putInt(28, ackNumber.toInt())
        buffer.put(32, (5 shl 4).toByte())
        buffer.put(33, flagsToByte(flags))
        buffer.putShort(34, windowSize.toShort())
        buffer.putShort(36, 0.toShort())
        buffer.putShort(38, 0.toShort())

        payload.copyInto(packet, IPV4_HEADER_LENGTH + TCP_HEADER_LENGTH)

        val ipChecksum = checksum(packet, 0, IPV4_HEADER_LENGTH)
        buffer.putShort(10, ipChecksum.toShort())

        val tcpChecksum = tcpChecksum(packet, srcIpBytes, dstIpBytes, TCP_HEADER_LENGTH + payload.size)
        buffer.putShort(36, tcpChecksum.toShort())

        return packet
    }

    private fun flagsToByte(flags: TcpControlFlags): Byte {
        var value = 0
        if (flags.fin) value = value or 0x01
        if (flags.syn) value = value or 0x02
        if (flags.rst) value = value or 0x04
        if (flags.psh) value = value or 0x08
        if (flags.ack) value = value or 0x10
        return value.toByte()
    }

    private fun tcpChecksum(packet: ByteArray, srcIp: ByteArray, dstIp: ByteArray, tcpLength: Int): Int {
        val pseudoHeader = ByteArray(12 + tcpLength)
        srcIp.copyInto(pseudoHeader, 0)
        dstIp.copyInto(pseudoHeader, 4)
        pseudoHeader[8] = 0
        pseudoHeader[9] = 6
        pseudoHeader[10] = (tcpLength ushr 8).toByte()
        pseudoHeader[11] = (tcpLength and 0xFF).toByte()
        packet.copyInto(
            destination = pseudoHeader,
            destinationOffset = 12,
            startIndex = IPV4_HEADER_LENGTH,
            endIndex = IPV4_HEADER_LENGTH + tcpLength
        )
        return checksum(pseudoHeader, 0, pseudoHeader.size)
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
        require(parts.size == 4) { "Only IPv4 is supported by the minimal TCP relay" }
        return ByteArray(4) { idx -> parts[idx].toInt().toByte() }
    }
}
