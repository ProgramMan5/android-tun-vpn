package com.example.androidtunvpn.network.frameparsers

import com.example.androidtunvpn.network.utils.ByteUtils
import com.example.androidtunvpn.network.utils.ip.v4.IpPacketV4
import com.example.androidtunvpn.network.utils.udp.UdpPacket

class NetworkFrameParser {
    fun buildIpv4UdpPacket(
        srcIp: Int,
        dstIp: Int,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
        buf: ByteArray
    ): Int{
        val ipHeaderLen = 20
        val udpLen = 8 + payload.size
        val totalLen = ipHeaderLen + udpLen

        // IP header
        buf[0] = ((4 shl 4) or (ipHeaderLen / 4)).toByte()
        buf[1] = 0
        ByteUtils.writeUInt16(buf, 2, totalLen)
        ByteUtils.writeUInt16(buf, 4, 0)
        ByteUtils.writeUInt16(buf, 6, 0)
        buf[8] = 64 // TTL
        buf[9] = 17 // protocol = UDP
        ByteUtils.writeUInt16(buf, 10, 0)
        ByteUtils.writeIntBE(buf, 12, srcIp)
        ByteUtils.writeIntBE(buf, 16, dstIp)

        // UDP header
        val udpOff = ipHeaderLen
        ByteUtils.writeUInt16(buf, udpOff, srcPort)
        ByteUtils.writeUInt16(buf, udpOff + 2, dstPort)
        ByteUtils.writeUInt16(buf, udpOff + 4, udpLen)
        ByteUtils.writeUInt16(buf, udpOff + 6, 0)

        // payload
        System.arraycopy(payload, 0, buf, ipHeaderLen + 8, payload.size)

        // checksums
        val ipChecksum = IpPacketV4.checksum(buf)
        ByteUtils.writeUInt16(buf, 10, ipChecksum)

        if (ipChecksum == -1) {
            println("Ip cheksum error")
        }

        val udpChecksum = UdpPacket.udpIPv4Checksum(buf, ipHeaderLen)
        ByteUtils.writeUInt16(buf, udpOff + 6, udpChecksum)

        if (udpChecksum == -1) {
            println("UDP cheksum error")
        }

        return  totalLen

    }
}


