package com.example.androidtunvpn.network


import com.example.androidtunvpn.network.frameparser.FrameParser
import com.example.androidtunvpn.network.models.FlowModels
import com.example.androidtunvpn.network.models.parseresult.OK
import com.example.androidtunvpn.network.vpnservice.VpnService
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.ConcurrentHashMap

class FrameDispatcher() {

    fun start(flowTable: ConcurrentHashMap<FlowModels.FlowKey, FlowModels.Flow>) {
        val fileDescriptor = VpnService().startVpn(1400)
        val inputFD = fileDescriptor.first
        val outFD = fileDescriptor.first

        val buf = ByteArray(65535)
        val frameFlowKey = FlowModels.FlowKey(1, 1, 1, 1)
        val byteBuffer = ByteBuffer.allocate(65535)
        while (true) {
            val read = try {
                inputFD.read(buf)
            } catch (e: Exception) {
                break
            }

            if (FrameParser().frameParsing(buf,read,frameFlowKey,byteBuffer) != OK) continue

            if (flowTable[frameFlowKey]==null){
                val channel = DatagramChannel.open()

                protect(channel.socket())
                channel.socket().reuseAddress = true

                flow = FlowModels.Flow(channel, System.currentTimeMillis())
                flowTable[key] = flow
            }



        }
    }
}