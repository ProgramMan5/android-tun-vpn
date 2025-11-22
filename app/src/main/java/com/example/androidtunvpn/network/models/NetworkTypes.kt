package com.example.androidtunvpn.network.models

import java.net.DatagramSocket
import java.nio.channels.DatagramChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

typealias ProtectFunc = (DatagramSocket) -> Boolean
typealias FlowTable = ConcurrentHashMap<FlowModels.FlowKey, FlowModels.Flow>
typealias PendingRegistrations = ConcurrentLinkedQueue<Pair<FlowModels.FlowKey, DatagramChannel>>
typealias ChannelToFlowKey = ConcurrentHashMap<DatagramChannel, FlowModels.FlowKey>
