package com.example.androidtunvpn.network

import com.example.androidtunvpn.network.frameparsers.TunFrameParser
import com.example.androidtunvpn.network.models.FlowModels
import com.example.androidtunvpn.network.models.FlowTable
import com.example.androidtunvpn.network.models.PendingRegistrations
import com.example.androidtunvpn.network.models.ProtectFunc
import com.example.androidtunvpn.network.models.parseresult.OK
import com.example.androidtunvpn.network.utils.ByteUtils
import java.io.FileInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.ConcurrentHashMap


class FrameDispatcher(private val protect: ProtectFunc) {
    private val parser = TunFrameParser()

    fun start(
        inputFd: FileInputStream, flowTable: FlowTable,
        pendingRegistrations:
        PendingRegistrations,
    ) {
        var lastGcTime = 0L
        val buf = ByteArray(65535)
        val reusableKey = FlowModels.FlowKey(1, 1, 1, 1)
        val byteBuffer = ByteBuffer.allocate(65535)

        try {
            while (!Thread.currentThread().isInterrupted) {
                val read = try {
                    inputFd.read(buf)
                } catch (e: Exception) {
                    break
                }

                if (read <= 0) break

                if (parser.frameParsing(buf, read, reusableKey, byteBuffer) != OK) continue

                var flow = flowTable[reusableKey]

                if (flow == null) {

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


                val now = System.currentTimeMillis()
                if (now - lastGcTime > 10_000) {
                    gcFlows(flowTable)
                    lastGcTime = now
                }
            }
        } finally {
            closeChannels(flowTable)
        }
    }

    private fun registerChannel(
        pendingRegistrations: PendingRegistrations,
        key: FlowModels.FlowKey,
        channel: DatagramChannel,
    ) {
        pendingRegistrations.add(key to channel)
    }


    private fun closeChannels(flowTable: FlowTable){
        // закрываем все каналы при завершении
        flowTable.values.forEach {
            try { it.channel.close() } catch (_: Exception) {}
        }
        flowTable.clear()
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