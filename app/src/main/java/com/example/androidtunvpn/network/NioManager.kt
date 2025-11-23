package com.example.androidtunvpn.network

import java.nio.channels.DatagramChannel
import android.util.Log
import com.example.androidtunvpn.network.frameparsers.NetworkFrameParser
import com.example.androidtunvpn.network.models.ChannelToFlowKey
import com.example.androidtunvpn.network.models.FlowModels
import com.example.androidtunvpn.network.models.PendingRegistrations
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector


class NioManager(private val outputFd: FileOutputStream) {

    private val selector = Selector.open()

    private val closeables = mutableListOf<AutoCloseable>(selector)

    private val channelToFlowKey = ChannelToFlowKey()

    private val networkFrameParser = NetworkFrameParser()

    fun start(
        pendingRegistrations: PendingRegistrations,
    ) {
        Log.i(
            "com.example.androidtunvpn.network.NioUdpManager",
            "NIO Manager started on thread ${Thread.currentThread().name}"
        )

        val buffer = ByteBuffer.allocate(65535)

        while (!Thread.currentThread().isInterrupted) {
            try {
                // Обрабатываем новые регистрации, пришедшие из tunLoop
                handlePendingRegistrations(pendingRegistrations)

                // Ждем событий (или 100 мс) на любом из зарегистрированных каналов.
                // Это - единственный блокирующий вызов.
                val readyChannels = selector.select(100)
                if (readyChannels == 0) {
                    continue
                }

                val keys = selector.selectedKeys()
                val iterator = keys.iterator()

                while (iterator.hasNext()) {
                    val key = iterator.next()
                    if (key.isValid && key.isReadable) {
                        val channel = key.channel() as DatagramChannel
                        buffer.clear()
                        /**??????? */
                        // Читаем данные из канала в буфер. Это неблокирующая операция.
                        val sourceAddress = channel.receive(buffer)

                        if (sourceAddress != null) {
                            // Данные прочитаны, передаем их в TUN
                            val flowKey = channelToFlowKey[channel]
                            if (flowKey != null) {
                                processIncomingData(flowKey, buffer)
                            }
                        }
                    }
                    iterator.remove()
                }
            } catch (e: Exception) {
                Log.e("com.example.androidtunvpn.network.NioUdpManager", "Error in NIO loop", e)
            }
            finally {
                close()
            }
        }
    }

    private fun handlePendingRegistrations(pendingRegistrations: PendingRegistrations) {
        var registration: Pair<FlowModels.FlowKey, DatagramChannel>?

        while (true) {
            registration = pendingRegistrations.poll()
            if (registration == null) break

            val (flowKey, channel) = registration
            try {
                // Переводим канал в неблокирующий режим
                channel.configureBlocking(false)
                // Регистрируем в селекторе, интересуемся только событиями чтения (OP_READ)
                channel.register(selector, SelectionKey.OP_READ)
                channelToFlowKey[channel] = flowKey
                closeables.add(channel)
                Log.d(
                    "com.example.androidtunvpn.network.NioUdpManager",
                    "Registered new channel for flow: $flowKey"
                )
            } catch (e: Exception) {
                Log.e(
                    "com.example.androidtunvpn.network.NioUdpManager",
                    "Failed to register channel for $flowKey",
                    e
                )
                channel.close()
            }
        }
    }


    private fun processIncomingData(
        flowKey: FlowModels.FlowKey,
        buffer: ByteBuffer
    ) {
        buffer.flip() // Готовим буфер к чтению
        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)

        val buf = ByteArray(65535)

        val totalLen = networkFrameParser.buildIpv4UdpPacket(
            srcIp = flowKey.dstIp,
            dstIp = flowKey.srcIp,
            srcPort = flowKey.dstPort,
            dstPort = flowKey.srcPort,
            payload = payload,
            buf = buf
        )

        try {
            outputFd.write(buf,0,totalLen)
        } catch (e: Exception) {
            Log.e("com.example.androidtunvpn.network.NioUdpManager", "Failed to write to TUN", e)
        }
    }


    private fun close() {
        closeables.forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
    }

}