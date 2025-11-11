package com.example.androidtunvpn.network

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.androidtunvpn.network.utils.ByteUtils
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedQueue
import java.io.FileInputStream

import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class NioUdpManager(private val tunOutput: FileOutputStream) : Runnable {

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

class SimpleVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val executor = Executors.newSingleThreadExecutor() // Один поток для NIO-менеджера
    private var nioManager: NioUdpManager? = null


    data class FlowKey(val srcIp: Int, val srcPort: Int, val dstIp: Int, val dstPort: Int)
    data class Flow(val channel: DatagramChannel, val lastSeen: Long)

    /**[flowTable] хранит ключ [FlowKey] и канал [Flow]*/
    private val flowTable = ConcurrentHashMap<FlowKey, Flow>()



    override fun onDestroy() {
        nioManager?.stop()
        executor.shutdownNow()
        vpnInterface?.close()
        super.onDestroy()
    }

    private fun startVpn() {
        val builder = Builder()
        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)
        builder.setMtu(1400)
        vpnInterface = builder.establish()

        vpnInterface?.let { pfd ->
            val tunOutput = FileOutputStream(pfd.fileDescriptor)
            nioManager = NioUdpManager(tunOutput).also {
                // Запускаем NIO-менеджер в его собственном выделенном потоке
                executor.submit(it)
            }
            // Запускаем цикл чтения из TUN в другом потоке (можно использовать тот же executor, если он многопоточный,
            // но для ясности лучше разделить)
            Thread(Runnable { tunLoop(pfd.fileDescriptor) }, "TunReaderThread").start()
        }
    }


    private fun tunLoop(tunFd: java.io.FileDescriptor) {
        val input = FileInputStream(tunFd)
        val buf = ByteArray(65535)

        while (vpnInterface != null) { // Цикл должен иметь условие выхода
            val read = try {
                input.read(buf)
            } catch (e: Exception) {
                break
            }
            if (read <= 0) continue

            // ... ваш код парсинга IP/UDP пакета ...

            val key = FlowKey(srcIp, srcPort, dstIp, dstPort)
            var flow = flowTable[key]

            if (flow == null) {
                try {
                    val channel = DatagramChannel.open()

                    protect(channel.socket()) // Защищаем сокет канала
                    channel.socket().reuseAddress = true

                    flow = Flow(channel, System.currentTimeMillis())
                    flowTable[key] = flow

                    // Вместо создания нового потока, мы регистрируем канал у нашего менеджера
                    nioManager?.registerChannel(key, channel)

                } catch (e: Exception) {
                    Log.e("SimpleVpnService", "Cannot create channel for $key", e)
                    continue
                }
            } else {
                flowTable[key] = flow.copy(lastSeen = System.currentTimeMillis())
            }

            try {
                // Отправка исходящего пакета. Используем DatagramChannel.
                val payloadBuffer = ByteBuffer.wrap(buf, offset, payloadLen)

                val destAddress = InetSocketAddress(
                    InetAddress.getByAddress(ByteUtils.intToBytes(dstIp)),
                    dstPort
                )
                flow.channel.send(payloadBuffer, destAddress)
            } catch (e: Exception) {
                Log.e("SimpleVpnService", "Failed to send outgoing packet for $key", e)
            }
            // gcFlows() // GC нужно тоже переделать для работы с каналами
        }
    }
    // ... gcFlows и другие вспомогательные методы ...
}