package com.example.androidtunvpn.network
import java.nio.channels.DatagramChannel
import android.util.Log
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedQueue

class NioManager() : Runnable {

    // "Диспетчер", который следит за всеми каналами.
    private val selector = Selector.open()
    private val closeables = mutableListOf<AutoCloseable>(selector)

    // Потокобезопасная очередь для регистрации новых каналов.
    // tunLoop будет добавлять в нее, а наш поток - забирать.
    private val pendingRegistrations =
        ConcurrentLinkedQueue<Pair<SimpleVpnService.FlowKey, DatagramChannel>>()

    @Volatile
    private var isRunning = false


    // Карта для связи канала с его ключом потока (flow), чтобы знать, куда отправлять ответ.
    private val channelToFlowKey = mutableMapOf<DatagramChannel, SimpleVpnService.FlowKey>()

    /**
     * Регистрирует новый канал для отслеживания.
     * Вызывается из потока tunLoop.
     */
    fun registerChannel(key: SimpleVpnService.FlowKey, channel: DatagramChannel) {
        pendingRegistrations.add(key to channel)
        // "Будим" селектор, если он спит в select(), чтобы он обработал новую регистрацию.
        selector.wakeup()
    }

    override fun run() {
        Log.i(
            "com.example.androidtunvpn.network.NioUdpManager",
            "NIO Manager started on thread ${Thread.currentThread().name}"
        )
        isRunning = true
        // Используем ByteBuffer для чтения данных. Он переиспользуется.
        val buffer = ByteBuffer.allocate(65535)

        while (isRunning) {
            try {
                // Обрабатываем новые регистрации, пришедшие из tunLoop
                handlePendingRegistrations()

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
        }
        close()
        Log.i("com.example.androidtunvpn.network.NioUdpManager", "NIO Manager stopped.")
    }

    private fun handlePendingRegistrations() {
        var registration: Pair<SimpleVpnService.FlowKey, DatagramChannel>?
        while (pendingRegistrations.poll().also { registration = it } != null) {
            val (flowKey, channel) = registration!!
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

    private fun processIncomingData(flowKey: SimpleVpnService.FlowKey, buffer: ByteBuffer) {
        buffer.flip() // Готовим буфер к чтению
        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)

        // Собираем IP/UDP пакет и пишем в TUN.
        // IP и порты меняются местами, как и раньше.
        val ipPacketBytes = buildIpv4UdpPacket(
            srcIp = flowKey.dstIp,
            dstIp = flowKey.srcIp,
            srcPort = flowKey.dstPort,
            dstPort = flowKey.srcPort,
            payload = payload
        )

        try {
            tunOutput.write(ipPacketBytes)
        } catch (e: Exception) {
            Log.e("com.example.androidtunvpn.network.NioUdpManager", "Failed to write to TUN", e)
        }
    }

    fun stop() {
        isRunning = false
        selector.wakeup() // Разбудить, чтобы цикл завершился
    }

    private fun close() {
        closeables.forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
    }

    // Эта функция-билдер должна быть доступна здесь.
    // В идеале, вынести её в отдельный класс PacketBuilder.
    private fun buildIpv4UdpPacket(/* ... */): ByteArray {
        // ... ваша реализация сборки пакета ...
        return ByteArray(0) // Заглушка
    }
}