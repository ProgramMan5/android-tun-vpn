package com.example.androidtunvpn.network.udp

import com.example.androidtunvpn.network.ip.v4.IpPacketV4.Companion.parseFrom

/**
 * Представление UDP Пакета.
 *
 * Хранит в себе такие поля как
 * [udpOffset]
 * [srcPort]
 * [dstPort]
 * [udpLen]
 * [payloadLen]
 * Обьект создается через фабричный метод [parseFrom].
 */
data class UdpPacket(
    val udpOffset: Int,
    val srcPort: Int,
    val dstPort: Int,
    val udpLen: Int,
    val payloadLen: Int,
) {
    companion object {
        fun parseFrom(buf: ByteArray, offset: Int): UdpPacket {
            val srcPort = UdpUtils.srcPort(buf, offset)
            val dstPort = UdpUtils.dstPort(buf, offset + 2)
            val udpLen = UdpUtils.dstPort(buf, offset + 4)
            val payloadLen = udpLen - 4
            return UdpPacket(offset, srcPort, dstPort, udpLen, payloadLen)
        }
    }
}