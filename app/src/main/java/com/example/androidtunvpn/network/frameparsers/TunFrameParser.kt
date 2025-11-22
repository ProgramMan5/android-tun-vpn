package com.example.androidtunvpn.network.frameparsers

import com.example.androidtunvpn.network.models.FlowModels
import com.example.androidtunvpn.network.models.parseresult.*
import com.example.androidtunvpn.network.utils.ByteUtils
import com.example.androidtunvpn.network.utils.ip.v4.IpPacketV4
import com.example.androidtunvpn.network.utils.udp.UdpPacket
import java.nio.ByteBuffer

class TunFrameParser {


    fun frameParsing(
        buf: ByteArray,
        readLen: Int,
        flowKey: FlowModels.FlowKey,
        byteBuffer: ByteBuffer,
    ): Int {

        if (readLen <= 0) return -1
        val totalLen = ByteUtils.readUInt16(buf, 2)
        if (totalLen > readLen) return INVALID_LENGTH

        val version = (buf[0].toInt() ushr 4) and 0xF
        if (version != 4) return NOT_IPV4

        val protocol = IpPacketV4.protocol(buf)
        if (protocol != 17) return NOT_UDP

        val ipPacketHeaderLen = IpPacketV4.headerLen(buf)

        val offset = (ipPacketHeaderLen + 8)
        val payloadLen = totalLen - offset
        val srcIp = IpPacketV4.srcIp(buf)
        val dstIp = IpPacketV4.dstIp(buf)
        val srcPort = UdpPacket.srcPort(buf, ipPacketHeaderLen)
        val dstPort = UdpPacket.dstPort(buf, ipPacketHeaderLen)

        flowKey.srcIp = srcIp
        flowKey.srcPort = srcPort
        flowKey.dstIp = dstIp
        flowKey.dstPort = dstPort

        byteBuffer.clear()
        byteBuffer.put(buf, offset, payloadLen)
        byteBuffer.flip()

        return OK
    }
}