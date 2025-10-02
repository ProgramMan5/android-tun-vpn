package com.example.androidtunvpn.network.ip.v4

/**
 * Представление Ipv4 Пакета.
 *
 * Хранит в себе такие поля как
 * [srcIp]
 * [dstIp]
 * [protocol]
 * [headerLen]
 * [payloadOffset]
 * [payloadLen]
 * [totalLen]
 * Обьект создается через фабричный метод [parseFrom].
 */
data class IpPacketV4(
    val srcIp: Int,
    val dstIp: Int,
    val protocol: Int,
    val headerLen: Int,
    val payloadOffset: Int,
    val payloadLen: Int,
    val totalLen: Int,

    ) {
    companion object {
        fun parseFrom(buf: ByteArray, totalLen: Int, offset: Int = 0): IpPacketV4 {
            val ihl = IpPacketUtils.ihl(buf)
            val protocol = IpPacketUtils.protocol(buf)
            val srcIp = IpPacketUtils.srcIp(buf)
            val dstIp = IpPacketUtils.dstIp(buf)
            val payloadLen = totalLen - ihl
            return IpPacketV4(srcIp, dstIp, protocol, ihl, offset + ihl, payloadLen, totalLen)
        }
    }
}
