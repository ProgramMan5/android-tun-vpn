package com.example.androidtunvpn.network

import com.example.androidtunvpn.network.models.FlowModels

import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue


class TunManager() : Runnable {

    private val flowTable = ConcurrentHashMap<FlowModels.FlowKey, FlowModels.Flow>()


    private val selector = Selector.open()
    private val closeables = mutableListOf<AutoCloseable>(selector)
    private val pendingRegistrations =
        ConcurrentLinkedQueue<Pair<SimpleVpnService.FlowKey, DatagramChannel>>()

    @Volatile
    private var isRunning = false

    fun registerChannel(key: SimpleVpnService.FlowKey, channel: DatagramChannel) {
        pendingRegistrations.add(key to channel)
        // "Будим" селектор, если он спит в select(), чтобы он обработал новую регистрацию.
        selector.wakeup()
    }

    override fun run() {
        isRunning = true

    }

}