package com.example.androidtunvpn.network

import android.util.Log
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedQueue

object SelectorUtils {
    fun registerChannel(
        selector: Selector,
        key: SimpleVpnService.FlowKey,
        channel: DatagramChannel,
        pendingRegistrations: ConcurrentLinkedQueue<Pair<SimpleVpnService.FlowKey, DatagramChannel>>
    ) {
        pendingRegistrations.add(key to channel)
        // "Будим" селектор, если он спит в select(), чтобы он обработал новую регистрацию.
        selector.wakeup()
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
}