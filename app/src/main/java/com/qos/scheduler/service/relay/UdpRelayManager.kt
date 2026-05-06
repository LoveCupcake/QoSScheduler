package com.qos.scheduler.service.relay

import android.util.Log
import com.qos.scheduler.model.Protocol
import com.qos.scheduler.model.RawPacket
import com.qos.scheduler.model.PacketComposer
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

/**
 * General UDP Relay to handle DNS, QUIC, and other UDP traffic in Phase 1.
 */
class UdpRelayManager(
    private val protectSocket: (DatagramSocket) -> Boolean,
    private val emitToTun: (ByteArray) -> Unit,
    private val onFlowCountChanged: (Int) -> Unit,
    private val onError: (String, Int) -> Unit
) : Closeable {
    private val relayDispatcher = java.util.concurrent.Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val scope = CoroutineScope(relayDispatcher + SupervisorJob())
    private val flows = ConcurrentHashMap<UdpFlowId, UdpFlow>()
    private val maxFlows = 2000
    private var cleanupJob: Job? = null

    init {
        startCleanupJob()
    }

    private fun startCleanupJob() {
        cleanupJob = scope.launch {
            while (isActive) {
                delay(5000) // Faster cleanup
                val now = System.currentTimeMillis()
                val iterator = flows.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value.lastActivity > 30000) {
                        entry.value.close()
                        iterator.remove()
                    }
                }
                onFlowCountChanged(flows.size)
            }
        }
    }

    fun handlePacket(packet: RawPacket): Boolean {
        if (packet.protocol != Protocol.UDP) return false
        Log.v("UdpRelay", "Handling UDP packet to ${packet.dstIp}:${packet.dstPort}")

        val flowId = UdpFlowId(packet.srcIp, packet.srcPort, packet.dstIp, packet.dstPort)
        
        val flow = flows.getOrPut(flowId) {
            if (flows.size >= maxFlows) {
                Log.w("UdpRelay", "Max UDP flow limit reached ($maxFlows)")
                return false
            }
            UdpFlow(flowId, scope, protectSocket, emitToTun, onError) {
                flows.remove(flowId)
                onFlowCountChanged(flows.size)
            }.also {
                it.start()
            }
        }
        
        flow.send(packet.payload)
        return true
    }

    override fun close() {
        cleanupJob?.cancel()
        flows.values.forEach { it.close() }
        flows.clear()
        onFlowCountChanged(0)
        scope.cancel()
        (relayDispatcher.executor as? java.util.concurrent.ExecutorService)?.shutdown()
    }

    private class UdpFlow(
        val id: UdpFlowId,
        private val scope: CoroutineScope,
        val protectSocket: (DatagramSocket) -> Boolean,
        val emitToTun: (ByteArray) -> Unit,
        val onError: (String, Int) -> Unit,
        val onClosed: () -> Unit = {}
    ) : Closeable {
        private val socketDeferred = CompletableDeferred<DatagramSocket>()
        var lastActivity = System.currentTimeMillis()
        private val jobs = mutableListOf<Job>()
        private var isClosed = false

        fun start() {
            val job = scope.launch {
                try {
                    val s = DatagramSocket()
                    if (!protectSocket(s)) {
                        Log.e("UdpRelay", "Failed to protect socket for $id")
                        close()
                        return@launch
                    }
                    socketDeferred.complete(s)
                    s.soTimeout = 5000
                    
                    val buffer = ByteArray(2048)
                    val packet = DatagramPacket(buffer, buffer.size)
                    
                    val isIpv6 = id.srcIp.contains(":") || id.dstIp.contains(":")
                    
                    while (isActive) {
                        try {
                            packet.length = buffer.size
                            s.receive(packet)
                            lastActivity = System.currentTimeMillis()
                            val responseData = packet.data.copyOf(packet.length)
                            
                            val backPacket = if (isIpv6) {
                                PacketComposer.composeUdpV6(
                                    srcIp = id.dstIp,
                                    srcPort = id.dstPort,
                                    dstIp = id.srcIp,
                                    dstPort = id.srcPort,
                                    payload = responseData
                                )
                            } else {
                                PacketComposer.composeUdpV4(
                                    srcIp = id.dstIp,
                                    srcPort = id.dstPort,
                                    dstIp = id.srcIp,
                                    dstPort = id.srcPort,
                                    payload = responseData
                                )
                            }
                            
                            if (responseData.size > 0 && Math.random() < 0.05) {
                                Log.d("UdpRelay", "Received ${responseData.size} bytes from ${id.dstIp} (${if (isIpv6) "v6" else "v4"}) -> TUN")
                            }
                            emitToTun(backPacket)
                        } catch (e: java.net.SocketTimeoutException) {
                            // Normal
                        } catch (e: Exception) {
                            if (isActive) onError(id.dstIp, id.dstPort)
                            break
                        }
                    }
                } catch (e: Exception) {
                    socketDeferred.cancel()
                    if (isActive) {
                        Log.e("UdpRelay", "Flow error for $id: ${e.message}")
                        onError(id.dstIp, id.dstPort)
                    }
                } finally {
                    close()
                    onClosed()
                }
            }
            jobs.add(job)
        }

        fun send(payload: ByteArray) {
            lastActivity = System.currentTimeMillis()
            scope.launch {
                try {
                    val s = socketDeferred.await()
                    withContext(Dispatchers.IO) {
                        s.send(DatagramPacket(payload, payload.size, InetAddress.getByName(id.dstIp), id.dstPort))
                    }
                } catch (e: Exception) {
                    if (isActive && !socketDeferred.isCancelled) {
                        Log.w("UdpRelay", "Failed to send UDP for $id: ${e.message}")
                    }
                }
            }
        }

        override fun close() {
            jobs.forEach { it.cancel() }
            if (socketDeferred.isCompleted) {
                runCatching { socketDeferred.getCompleted().close() }
            } else {
                socketDeferred.cancel()
            }
        }
    }

    data class UdpFlowId(val srcIp: String, val srcPort: Int, val dstIp: String, val dstPort: Int)
}
