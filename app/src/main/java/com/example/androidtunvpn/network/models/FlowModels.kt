package com.example.androidtunvpn.network.models

import java.nio.channels.DatagramChannel

object FlowModels {
    data class FlowKey(
        var srcIp: Int,
        var srcPort: Int,
        var dstIp: Int,
        var dstPort: Int
    )
    data class Flow(val channel: DatagramChannel, val lastSeen: Long)
}