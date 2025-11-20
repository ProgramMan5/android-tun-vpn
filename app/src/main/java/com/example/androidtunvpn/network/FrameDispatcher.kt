package com.example.androidtunvpn.network

import com.example.androidtunvpn.network.frameparser.FrameParser
import com.example.androidtunvpn.network.models.FlowModels
import com.example.androidtunvpn.network.models.parseresult.OK
import com.example.androidtunvpn.network.utils.ByteUtils
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

typealias ProtectFunc = (DatagramSocket) -> Boolean
typealias FlowTable = ConcurrentHashMap<FlowModels.FlowKey, FlowModels.Flow>
typealias PendingRegistrations = ConcurrentLinkedQueue<Pair<SimpleVpnService.FlowKey, DatagramChannel>>

class FrameDispatcher(private val protect: ProtectFunc) {
    private val parser = FrameParser()

    fun start(inputFd: java.io.FileInputStream, flowTable: FlowTable, pendingRegistrations:
    PendingRegistrations) {



        val buf = ByteArray(65535)
        val reusableKey = FlowModels.FlowKey(1, 1, 1, 1)
        val byteBuffer = ByteBuffer.allocate(65535)
        while (true) {
            val read = try {
                inputFd.read(buf)
            } catch (e: Exception) {
                break
            }

            if (read <= 0) break

            if (parser.frameParsing(buf,read,reusableKey,byteBuffer) != OK) continue


            var flow = flowTable[reusableKey]
            if (flow==null){

                val channel = DatagramChannel.open()

                channel.configureBlocking(false)

                protect(channel.socket())

                channel.socket().reuseAddress = true


                val keyForMap = reusableKey.copy()

                flow = FlowModels.Flow(channel, System.currentTimeMillis())

                registerChannel(pendingRegistrations, keyForMap, channel)

                flowTable[keyForMap] = flow


            }

            val targetAddress = InetSocketAddress(
                InetAddress.getByAddress(ByteUtils.intToBytes(reusableKey.dstIp)),
                reusableKey.dstPort
            )
            flow.channel.send(byteBuffer, targetAddress)

            gcFlows(flowTable)

        }
    }

    fun registerChannel(pendingRegistrations: PendingRegistrations, key: FlowModels.FlowKey, channel: DatagramChannel) {
        pendingRegistrations.add(key to channel)
    }

    private fun gcFlows(flowTable: ConcurrentHashMap<FlowModels.FlowKey, FlowModels.Flow>) {
        val now = System.currentTimeMillis()
        val it = flowTable.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (now - e.value.lastSeen > 30_000) {
                try {
                    e.value.channel.close()
                } catch (_: Exception) {
                }
                it.remove()
            }
        }
    }
}