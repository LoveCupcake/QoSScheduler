import com.qos.scheduler.model.*
import java.nio.ByteBuffer

fun main() {
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
    println("Parsed: $parsed")
    println("Flags: ${parsed?.tcpFlags}")
    println("Payload matches: ${parsed?.payload?.contentEquals(payload)}")
}
