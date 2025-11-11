package com.example.androidtunvpn.network.vpnservice

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.example.androidtunvpn.network.utils.ByteUtils
import com.example.androidtunvpn.network.models.FlowModels
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.io.FileInputStream

import java.net.InetAddress
import java.net.InetSocketAddress

class VpnService : VpnService() {

    fun startVpn(mtu: Int): Pair<FileInputStream, FileOutputStream> {
        val builder = Builder()
        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)
        builder.setMtu(mtu)

        val vpnInterface = builder.establish()

        val fd = vpnInterface!!.fileDescriptor
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)

        return Pair(input, output)
    }

    fun onDestroy(vpnInterface: ParcelFileDescriptor) {
        vpnInterface.close()
        super.onDestroy()
    }

    fun sendPacket(flowKey: FlowModels.FlowKey, flow: FlowModels.Flow, buf: ByteArray, offset: Int, payloadLen: Int) {
        val payloadBuffer = ByteBuffer.wrap(buf, offset, payloadLen)
        val destAddress = InetSocketAddress(
            InetAddress.getByAddress(ByteUtils.intToBytes(flowKey.dstIp)),
            flowKey.dstPort
        )
        flow.channel.send(payloadBuffer, destAddress)

    }
}

