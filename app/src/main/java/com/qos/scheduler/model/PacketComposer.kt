package com.qos.scheduler.model

import java.nio.ByteBuffer

/**
 * Utility to compose raw IP packets for emitting back to the TUN interface.
 * Hardened for Phase 1 Production-Candidate.
 */
object PacketComposer {

    fun composeUdpV4(
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val buffer = ByteBuffer.allocate(totalLength)

        // --- IPv4 Header ---
        buffer.put(0, (0x45).toByte())
        buffer.put(1, 0)
        buffer.putShort(2, totalLength.toShort())
        buffer.putShort(4, (Math.random() * 65535).toInt().toShort()) // Random ID
        buffer.putShort(6, 0)
        buffer.put(8, 64)
        buffer.put(9, 17) // UDP
        buffer.putShort(10, 0)
        
        val srcBytes = ipToBytes(srcIp)
        val dstBytes = ipToBytes(dstIp)
        buffer.position(12)
        buffer.put(srcBytes)
        buffer.put(dstBytes)

        buffer.putShort(10, computeIpChecksum(buffer, 0, 20))

        // --- UDP Header ---
        val udpStart = 20
        buffer.position(udpStart)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLength.toShort())
        buffer.putShort(0) // Placeholder

        // --- Payload ---
        buffer.position(udpStart + 8)
        buffer.put(payload)

        // --- UDP Checksum (Pseudo-header + UDP) ---
        val checksum = computeUdpChecksum(buffer, udpStart, udpLength, srcBytes, dstBytes)
        buffer.putShort(udpStart + 6, checksum)

        return buffer.array()
    }

    private fun computeUdpChecksum(buffer: ByteBuffer, udpOffset: Int, udpLength: Int, srcIp: ByteArray, dstIp: ByteArray): Short {
        var sum = 0L
        
        // Pseudo-header
        sum += ((srcIp[0].toInt() and 0xFF) shl 8 or (srcIp[1].toInt() and 0xFF)).toLong()
        sum += ((srcIp[2].toInt() and 0xFF) shl 8 or (srcIp[3].toInt() and 0xFF)).toLong()
        sum += ((dstIp[0].toInt() and 0xFF) shl 8 or (dstIp[1].toInt() and 0xFF)).toLong()
        sum += ((dstIp[2].toInt() and 0xFF) shl 8 or (dstIp[3].toInt() and 0xFF)).toLong()
        sum += 17L // Protocol UDP
        sum += udpLength.toLong()
        
        // UDP Header and Payload
        for (i in 0 until udpLength / 2) {
            sum += (buffer.getShort(udpOffset + i * 2).toInt() and 0xFFFF).toLong()
        }
        if (udpLength % 2 != 0) {
            sum += ((buffer.get(udpOffset + udpLength - 1).toInt() and 0xFF) shl 8).toLong()
        }
        
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = (sum.inv() and 0xFFFF).toShort()
        return if (result == (0).toShort()) (0xFFFF).toShort() else result
    }

    fun composeTcpV4(
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int,
        sequenceNumber: Long,
        ackNumber: Long,
        flags: TcpControlFlags,
        windowSize: Int = 65535,
        payload: ByteArray = byteArrayOf()
    ): ByteArray {
        val tcpHeaderLength = 20
        val totalLength = 20 + tcpHeaderLength + payload.size
        val buffer = ByteBuffer.allocate(totalLength)

        // --- IPv4 Header ---
        buffer.put(0, (0x45).toByte())
        buffer.put(1, 0)
        buffer.putShort(2, totalLength.toShort())
        buffer.putShort(4, (Math.random() * 65535).toInt().toShort())
        buffer.putShort(6, 0)
        buffer.put(8, 64)
        buffer.put(9, 6) // TCP
        buffer.putShort(10, 0)
        
        val srcBytes = ipToBytes(srcIp)
        val dstBytes = ipToBytes(dstIp)
        buffer.position(12)
        buffer.put(srcBytes)
        buffer.put(dstBytes)
        
        buffer.putShort(10, computeIpChecksum(buffer, 0, 20))

        // --- TCP Header ---
        val tcpStart = 20
        buffer.position(tcpStart)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putInt(sequenceNumber.toInt())
        buffer.putInt(ackNumber.toInt())
        
        var flagsBits = (tcpHeaderLength / 4) shl 12
        if (flags.fin) flagsBits = flagsBits or 0x01
        if (flags.syn) flagsBits = flagsBits or 0x02
        if (flags.rst) flagsBits = flagsBits or 0x04
        if (flags.psh) flagsBits = flagsBits or 0x08
        if (flags.ack) flagsBits = flagsBits or 0x10
        buffer.putShort(flagsBits.toShort())
        
        buffer.putShort(windowSize.toShort())
        // Position is now at TCP Checksum (offset 16)
        buffer.putShort(0) // Placeholder for TCP Checksum
        buffer.putShort(0) // Urgent pointer

        if (payload.isNotEmpty()) {
            buffer.position(tcpStart + tcpHeaderLength)
            buffer.put(payload)
        }

        // --- CRITICAL: TCP Pseudo-header Checksum ---
        val tcpChecksum = computeTcpChecksum(buffer, tcpStart, tcpHeaderLength + payload.size, srcBytes, dstBytes)
        buffer.putShort(tcpStart + 16, tcpChecksum)

        return buffer.array()
    }

    fun composeUdpV6(
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 40 + udpLength // IPv6 fixed header is 40 bytes
        val buffer = ByteBuffer.allocate(totalLength)

        // --- IPv6 Header ---
        // Version 6, Traffic Class 0, Flow Label 0
        buffer.putInt(0, 0x60000000)
        buffer.putShort(4, udpLength.toShort()) // Payload length
        buffer.put(6, 17.toByte()) // Next Header: UDP
        buffer.put(7, 64.toByte()) // Hop Limit

        val srcBytes = java.net.InetAddress.getByName(srcIp).address
        val dstBytes = java.net.InetAddress.getByName(dstIp).address
        buffer.position(8)
        buffer.put(srcBytes)
        buffer.put(dstBytes)

        // --- UDP Header ---
        val udpStart = 40
        buffer.position(udpStart)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLength.toShort())
        buffer.putShort(0) // Placeholder

        // --- Payload ---
        buffer.position(udpStart + 8)
        buffer.put(payload)

        // --- UDP Checksum (IPv6 Pseudo-header + UDP) ---
        val checksum = computeUdpV6Checksum(buffer, udpStart, udpLength, srcBytes, dstBytes)
        buffer.putShort(udpStart + 6, checksum)

        return buffer.array()
    }

    private fun computeUdpV6Checksum(buffer: ByteBuffer, udpOffset: Int, udpLength: Int, srcIp: ByteArray, dstIp: ByteArray): Short {
        var sum = 0L
        
        // IPv6 Pseudo-header
        for (i in 0 until 8) {
            sum += ((srcIp[i*2].toInt() and 0xFF) shl 8 or (srcIp[i*2+1].toInt() and 0xFF)).toLong()
            sum += ((dstIp[i*2].toInt() and 0xFF) shl 8 or (dstIp[i*2+1].toInt() and 0xFF)).toLong()
        }
        sum += udpLength.toLong()
        sum += 17L // Next Header: UDP
        
        // UDP Header and Payload
        for (i in 0 until udpLength / 2) {
            sum += (buffer.getShort(udpOffset + i * 2).toInt() and 0xFFFF).toLong()
        }
        if (udpLength % 2 != 0) {
            sum += ((buffer.get(udpOffset + udpLength - 1).toInt() and 0xFF) shl 8).toLong()
        }
        
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = (sum.inv() and 0xFFFF).toShort()
        return if (result == (0).toShort()) (0xFFFF).toShort() else result
    }

    fun composeTcpV6(
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int,
        sequenceNumber: Long,
        ackNumber: Long,
        flags: TcpControlFlags,
        windowSize: Int = 65535,
        payload: ByteArray = byteArrayOf()
    ): ByteArray {
        val tcpHeaderLength = 20
        val totalLength = 40 + tcpHeaderLength + payload.size
        val buffer = ByteBuffer.allocate(totalLength)

        // --- IPv6 Header ---
        buffer.putInt(0, 0x60000000)
        buffer.putShort(4, (tcpHeaderLength + payload.size).toShort())
        buffer.put(6, 6.toByte()) // Next Header: TCP
        buffer.put(7, 64.toByte())

        val srcBytes = java.net.InetAddress.getByName(srcIp).address
        val dstBytes = java.net.InetAddress.getByName(dstIp).address
        buffer.position(8)
        buffer.put(srcBytes)
        buffer.put(dstBytes)

        // --- TCP Header ---
        val tcpStart = 40
        buffer.position(tcpStart)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putInt(sequenceNumber.toInt())
        buffer.putInt(ackNumber.toInt())
        
        var flagsBits = (tcpHeaderLength / 4) shl 12
        if (flags.fin) flagsBits = flagsBits or 0x01
        if (flags.syn) flagsBits = flagsBits or 0x02
        if (flags.rst) flagsBits = flagsBits or 0x04
        if (flags.psh) flagsBits = flagsBits or 0x08
        if (flags.ack) flagsBits = flagsBits or 0x10
        buffer.putShort(flagsBits.toShort())
        buffer.putShort(windowSize.toShort())
        buffer.putShort(0) // Checksum
        buffer.putShort(0) // Urgent

        if (payload.isNotEmpty()) {
            buffer.position(tcpStart + tcpHeaderLength)
            buffer.put(payload)
        }

        // --- TCP Checksum (IPv6 Pseudo-header + TCP) ---
        val tcpChecksum = computeTcpV6Checksum(buffer, tcpStart, tcpHeaderLength + payload.size, srcBytes, dstBytes)
        buffer.putShort(tcpStart + 16, tcpChecksum)

        return buffer.array()
    }

    private fun computeTcpV6Checksum(buffer: ByteBuffer, tcpOffset: Int, tcpLength: Int, srcIp: ByteArray, dstIp: ByteArray): Short {
        var sum = 0L
        for (i in 0 until 8) {
            sum += ((srcIp[i*2].toInt() and 0xFF) shl 8 or (srcIp[i*2+1].toInt() and 0xFF)).toLong()
            sum += ((dstIp[i*2].toInt() and 0xFF) shl 8 or (dstIp[i*2+1].toInt() and 0xFF)).toLong()
        }
        sum += 6L // Protocol TCP
        sum += tcpLength.toLong()
        
        for (i in 0 until tcpLength / 2) {
            sum += (buffer.getShort(tcpOffset + i * 2).toInt() and 0xFFFF).toLong()
        }
        if (tcpLength % 2 != 0) {
            sum += ((buffer.get(tcpOffset + tcpLength - 1).toInt() and 0xFF) shl 8).toLong()
        }
        
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = (sum.inv() and 0xFFFF).toShort()
        return if (result == (0).toShort()) (0xFFFF).toShort() else result
    }

    private fun ipToBytes(ip: String): ByteArray {
        return try {
            java.net.InetAddress.getByName(ip).address
        } catch (e: Exception) {
            if (ip.contains(":")) ByteArray(16) else ByteArray(4)
        }
    }

    private fun computeIpChecksum(buffer: ByteBuffer, offset: Int, length: Int): Short {
        var sum = 0L
        for (i in 0 until length / 2) {
            sum += (buffer.getShort(offset + i * 2).toInt() and 0xFFFF).toLong()
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }

    private fun computeTcpChecksum(buffer: ByteBuffer, tcpOffset: Int, tcpLength: Int, srcIp: ByteArray, dstIp: ByteArray): Short {
        var sum = 0L
        if (srcIp.size == 4) {
            sum += ((srcIp[0].toInt() and 0xFF) shl 8 or (srcIp[1].toInt() and 0xFF)).toLong()
            sum += ((srcIp[2].toInt() and 0xFF) shl 8 or (srcIp[3].toInt() and 0xFF)).toLong()
            sum += ((dstIp[0].toInt() and 0xFF) shl 8 or (dstIp[1].toInt() and 0xFF)).toLong()
            sum += ((dstIp[2].toInt() and 0xFF) shl 8 or (dstIp[3].toInt() and 0xFF)).toLong()
        } else {
            for (i in 0 until 8) {
                sum += ((srcIp[i*2].toInt() and 0xFF) shl 8 or (srcIp[i*2+1].toInt() and 0xFF)).toLong()
                sum += ((dstIp[i*2].toInt() and 0xFF) shl 8 or (dstIp[i*2+1].toInt() and 0xFF)).toLong()
            }
        }
        sum += 6L
        sum += tcpLength.toLong()
        for (i in 0 until tcpLength / 2) {
            sum += (buffer.getShort(tcpOffset + i * 2).toInt() and 0xFFFF).toLong()
        }
        if (tcpLength % 2 != 0) {
            sum += ((buffer.get(tcpOffset + tcpLength - 1).toInt() and 0xFF) shl 8).toLong()
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = (sum.inv() and 0xFFFF).toShort()
        return if (result == (0).toShort()) (0xFFFF).toShort() else result
    }
}
