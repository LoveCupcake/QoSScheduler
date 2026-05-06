package com.qos.scheduler

import org.junit.Test
import org.junit.Assert.*
import com.qos.scheduler.model.*

class PacketTest {
    @Test
    fun testPacketComposer() {
        val payload = "Hello World".toByteArray()
        val packet = PacketComposer.composeTcpV4(
            srcIp = "8.8.8.8",
            srcPort = 443,
            dstIp = "10.0.0.2",
            dstPort = 12345,
            sequenceNumber = 1000L,
            ackNumber = 2000L,
            flags = TcpControlFlags(syn = true, ack = true),
            windowSize = 65535,
            payload = payload
        )
        
        val parsed = RawPacket.parse(packet, packet.size)
        assertNotNull(parsed)
        assertEquals(443, parsed!!.srcPort)
        assertEquals(12345, parsed.dstPort)
        assertEquals(true, parsed.tcpFlags?.syn)
        assertEquals(true, parsed.tcpFlags?.ack)
        assertArrayEquals(payload, parsed.payload)
        
        println("SUCCESS!")
    }
}
