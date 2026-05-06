package com.qos.scheduler.service.relay

import android.util.Log
import com.qos.scheduler.model.PacketComposer
import com.qos.scheduler.model.TcpControlFlags
import com.qos.scheduler.model.RawPacket
import com.qos.scheduler.model.Protocol
import com.qos.scheduler.util.AppResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust TCP Relay using Coroutines and Blocking Sockets.
 * Increased flow limits and improved UID resolution for better QoS reporting.
 */
class TcpRelayManager(
    private val appResolver: AppResolver,
    private val protectSocket: (Socket) -> Boolean,
    private val emitToTun: (ByteArray) -> Unit,
    private val onFlowCountChanged: (Int) -> Unit,
    private val onError: () -> Unit
) : Closeable {
    private val relayDispatcher = java.util.concurrent.Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val scope = CoroutineScope(relayDispatcher + SupervisorJob())
    private val flows = ConcurrentHashMap<FlowId, TcpFlow>()
    private val maxFlows = 1000 
    private var cleanupJob: Job? = null

    init {
        startCleanupLoop()
    }

    data class FlowId(
        val srcIp: String,
        val srcPort: Int,
        val dstIp: String,
        val dstPort: Int
    )

    fun handlePacket(packet: RawPacket): Boolean {
        if (packet.protocol != Protocol.TCP) return false
        Log.v("TcpRelay", "Handling TCP packet to ${packet.dstIp}:${packet.dstPort}")

        val flowId = FlowId(packet.srcIp, packet.srcPort, packet.dstIp, packet.dstPort)
        var flow = flows[flowId]

        if (flow == null) {
            val flags = packet.tcpFlags
            if (flags != null && flags.syn) {
                if (flows.size >= maxFlows) {
                    Log.w("TcpRelay", "Max flow limit ($maxFlows) reached! Dropping SYN from ${packet.srcIp}:${packet.srcPort}")
                    return false
                }
                
                // Try to resolve UID asynchronously on the first packet
                val newFlow = TcpFlow(flowId, scope, protectSocket, emitToTun, onError, uid = null) {
                    flows.remove(flowId)
                    onFlowCountChanged(flows.size)
                }
                flows[flowId] = newFlow
                onFlowCountChanged(flows.size)
                
                newFlow.isResolvingUid = true
                scope.launch {
                    val resolvedUid = appResolver.getUidForConnection(6, packet.srcIp, packet.srcPort, packet.dstIp, packet.dstPort)
                    if (resolvedUid != null) {
                        newFlow.uid = resolvedUid
                    } else {
                        newFlow.isResolvingUid = false
                    }
                }
                
                newFlow.onTunPacket(packet)
                return true
            } else if (flags != null && !flags.rst) {
                // Send RST for unknown non-SYN packets to clear stale connections
                Log.w("TcpRelay", "Sending RST for unknown flow: ${packet.srcIp}:${packet.srcPort} -> ${packet.dstIp}:${packet.dstPort}")
                val isIpv6 = packet.srcIp.contains(":")
                val rstPacket = if (isIpv6) {
                    PacketComposer.composeTcpV6(
                        srcIp = packet.dstIp,
                        srcPort = packet.dstPort,
                        dstIp = packet.srcIp,
                        dstPort = packet.srcPort,
                        sequenceNumber = 0,
                        ackNumber = (packet.tcpSequenceNumber ?: 0) + 1,
                        flags = com.qos.scheduler.model.TcpControlFlags(rst = true, ack = true)
                    )
                } else {
                    PacketComposer.composeTcpV4(
                        srcIp = packet.dstIp,
                        srcPort = packet.dstPort,
                        dstIp = packet.srcIp,
                        dstPort = packet.srcPort,
                        sequenceNumber = 0,
                        ackNumber = (packet.tcpSequenceNumber ?: 0) + 1,
                        flags = com.qos.scheduler.model.TcpControlFlags(rst = true, ack = true)
                    )
                }
                emitToTun(rstPacket)
                return true
            }
            return false
        }

        // If flow exists but UID is missing, try resolving again (it might be ESTABLISHED now)
        if (flow.uid == null && !flow.isResolvingUid) {
            flow.isResolvingUid = true
            scope.launch {
                val resolvedUid = appResolver.getUidForConnection(6, packet.srcIp, packet.srcPort, packet.dstIp, packet.dstPort)
                if (resolvedUid != null) {
                    flow.uid = resolvedUid
                } else {
                    flow.isResolvingUid = false
                }
            }
        }

        flow.onTunPacket(packet)
        return true
    }

    fun getFlowCount(): Int = flows.size

    private fun startCleanupLoop() {
        cleanupJob = scope.launch {
            while (isActive) {
                delay(5000) // Fast cleanup for high churn
                val now = System.currentTimeMillis()
                val iterator = flows.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.value.isStale(now)) {
                        entry.value.close()
                        iterator.remove()
                    }
                }
                onFlowCountChanged(flows.size)
            }
        }
    }

    override fun close() {
        cleanupJob?.cancel()
        flows.values.forEach { it.close() }
        flows.clear()
        scope.cancel()
        (relayDispatcher.executor as? java.util.concurrent.ExecutorService)?.shutdown()
    }

    private class TcpFlow(
        val id: FlowId,
        private val scope: CoroutineScope,
        private val protectSocket: (Socket) -> Boolean,
        private val emitToTun: (ByteArray) -> Unit,
        private val onError: () -> Unit,
        var uid: Int?,
        private val onClosed: () -> Unit = {}
    ) : Closeable {
        var isResolvingUid = false
        private var socket: Socket? = null
        private var lastActivity = System.currentTimeMillis()
        private var state = State.SYN_RECEIVED
        
        private var clientSeq: Long = 0
        private var serverSeq: Long = (Math.random() * 0xFFFFFFFFL).toLong()
        private var serverAck: Long = 0
        
        private var clientAck: Long = 0
        private var clientWindow: Int = 65535

        private val writeChannel = Channel<ByteArray>(capacity = 100) 
        private val jobs = mutableListOf<Job>()
        private var isClosed = false
        private val isIpv6 = id.srcIp.contains(":") || id.dstIp.contains(":")

        enum class State { SYN_RECEIVED, SYN_ACK_SENT, ESTABLISHED, CLOSED }

        fun onTunPacket(packet: RawPacket) {
            if (isClosed) return
            lastActivity = System.currentTimeMillis()
            val flags = packet.tcpFlags ?: return
            
            clientSeq = packet.tcpSequenceNumber ?: 0
            if (flags.ack) {
                clientAck = packet.tcpAckNumber ?: 0
                clientWindow = packet.tcpWindowSize ?: 65535
            }

            if (flags.syn) {
                if (state == State.SYN_RECEIVED) {
                    serverAck = clientSeq + 1
                    sendTunPacket(TcpControlFlags(syn = true, ack = true))
                    serverSeq++
                    state = State.SYN_ACK_SENT
                    connectRealSocket()
                } else if (state == State.SYN_ACK_SENT) {
                    sendTunPacket(TcpControlFlags(syn = true, ack = true))
                }
            } else if (flags.ack && state == State.SYN_ACK_SENT) {
                state = State.ESTABLISHED
            }
            
            if (state == State.ESTABLISHED && packet.payloadLength > 0) {
                if (clientSeq == serverAck) {
                    val payloadCopy = packet.payload.copyOf()
                    Log.d("TcpRelay", "Buffering ${payloadCopy.size} bytes from app (Seq $clientSeq)")
                    if (writeChannel.trySend(payloadCopy).isSuccess) {
                        serverAck += packet.payloadLength
                        sendTunPacket(TcpControlFlags(ack = true))
                    } else {
                        Log.w("TcpRelay", "Write channel full, dropping payload for $id")
                    }
                } else {
                    Log.w("TcpRelay", "Out of order packet from app for $id: Expected $serverAck, got $clientSeq. Dropping payload.")
                    sendTunPacket(TcpControlFlags(ack = true))
                }
            }

            if (flags.fin || flags.rst) close()
        }

        private fun connectRealSocket() {
            val connectJob = scope.launch {
                try {
                    val s = Socket()
                    if (!protectSocket(s)) {
                        Log.e("TcpRelay", "Failed to protect socket for $id")
                        close()
                        return@launch
                    }
                    socket = s
                    Log.i("TcpRelay", "Socket connecting to ${id.dstIp}:${id.dstPort}...")
                    s.soTimeout = 30000
                    s.connect(InetSocketAddress(id.dstIp, id.dstPort), 10000)
                    Log.i("TcpRelay", "Socket connected to ${id.dstIp}:${id.dstPort} successfully!")
                    
                    startReadLoop(s)
                    startWriteLoop(s)
                } catch (e: Exception) {
                    Log.w("TcpRelay", "Connection failed for $id: ${e.message}")
                    close()
                }
            }
            jobs.add(connectJob)
        }

        private fun startReadLoop(s: Socket) {
            val readJob = scope.launch {
                val buffer = ByteArray(16384)
                val inputStream = s.getInputStream()
                try {
                    while (isActive && !isClosed) {
                        // Flow control: backpressure if client window is full
                        val serverSeq32 = serverSeq and 0xFFFFFFFFL
                        val unackedBytes = (serverSeq32 - clientAck + 0x100000000L) % 0x100000000L
                        if (unackedBytes >= clientWindow && clientWindow > 0) {
                            delay(50)
                            continue
                        }
                        
                        val read = inputStream.read(buffer)
                        if (read > 0) {
                            Log.d("TcpRelay", "Read $read bytes from real server ${id.dstIp}:${id.dstPort}")
                            val data = buffer.copyOf(read)
                            var offset = 0
                            while (offset < data.size) {
                                val chunkSize = Math.min(1340, data.size - offset)
                                sendTunPacket(
                                    TcpControlFlags(ack = true, psh = (offset + chunkSize == data.size)),
                                    data.copyOfRange(offset, offset + chunkSize)
                                )
                                serverSeq += chunkSize
                                offset += chunkSize
                            }
                        } else if (read == -1) {
                            Log.d("TcpRelay", "Server closed connection for ${id.dstIp}:${id.dstPort}")
                            sendTunPacket(TcpControlFlags(fin = true, ack = true))
                            break
                        }
                    }
                } catch (e: Exception) { } finally { close() }
            }
            jobs.add(readJob)
        }

        private fun startWriteLoop(s: Socket) {
            val writeJob = scope.launch {
                val outputStream = s.getOutputStream()
                try {
                    for (data in writeChannel) {
                        Log.d("TcpRelay", "Writing ${data.size} bytes to real server ${id.dstIp}:${id.dstPort}")
                        outputStream.write(data)
                        outputStream.flush()
                        lastActivity = System.currentTimeMillis()
                    }
                } catch (e: Exception) { 
                    Log.w("TcpRelay", "Write loop error for ${id.dstIp}:${id.dstPort}: ${e.message}")
                } finally { close() }
            }
            jobs.add(writeJob)
        }

        private fun sendTunPacket(flags: TcpControlFlags, payload: ByteArray = byteArrayOf()) {
            val packet = if (isIpv6) {
                PacketComposer.composeTcpV6(
                    srcIp = id.dstIp, srcPort = id.dstPort,
                    dstIp = id.srcIp, dstPort = id.srcPort,
                    sequenceNumber = serverSeq,
                    ackNumber = serverAck,
                    flags = flags,
                    windowSize = 65535,
                    payload = payload
                )
            } else {
                PacketComposer.composeTcpV4(
                    srcIp = id.dstIp, srcPort = id.dstPort,
                    dstIp = id.srcIp, dstPort = id.srcPort,
                    sequenceNumber = serverSeq,
                    ackNumber = serverAck,
                    flags = flags,
                    windowSize = 65535,
                    payload = payload
                )
            }
            emitToTun(packet)
        }

        fun isStale(now: Long): Boolean {
            val timeout = if (state == State.ESTABLISHED) 120000 else 30000
            return now - lastActivity > timeout
        }

        override fun close() {
            if (isClosed) return
            isClosed = true
            state = State.CLOSED
            jobs.forEach { it.cancel() }
            writeChannel.close()
            runCatching { socket?.close() }
            if (socket == null) {
                sendTunPacket(TcpControlFlags(rst = true, ack = true))
            }
            onClosed()
        }
    }
}
