package com.example.androidtunvpn.network.utils.udp

import com.example.androidtunvpn.network.utils.ByteUtils


object UdpPacket {
    fun srcPort(buf: ByteArray, udpOffset: Int) = ByteUtils.readUInt16(buf, udpOffset)
    fun dstPort(buf: ByteArray, udpOffset: Int) = ByteUtils.readUInt16(buf, udpOffset + 2)
    fun udpLen(buf: ByteArray, udpOffset: Int) = ByteUtils.readUInt16(buf, udpOffset + 4)
    fun udpIPv4Checksum(buf: ByteArray, udpOffset: Int) = ByteUtils.readUInt16(buf, udpOffset + 6)
    private fun calculateUdpIPv4Checksum(
        buf: ByteArray,
        udpOffset: Int,
        udpLen: Int,
        srcIp: Int,
        dstIp: Int,
    ): Int {
        var sum: Long = 0

        // --- Псевдо-заголовок (IPv4) ---
        sum += ((srcIp ushr 16) and 0xFFFF).toLong()
        sum += (srcIp and 0xFFFF).toLong()
        sum += ((dstIp ushr 16) and 0xFFFF).toLong()
        sum += (dstIp and 0xFFFF).toLong()
        sum += 17L // protocol (UDP = 17)
        sum += udpLen.toLong() and 0xFFFF

        // --- UDP header + data ---

        var i = udpOffset
        val end = udpOffset + udpLen
        while (i < end) {
            val hi = buf[i].toInt() and 0xFF
            val lo = if (i + 1 < end) (buf[i + 1].toInt() and 0xFF) else 0
            sum += ((hi shl 8) + lo).toLong()
            // складываем перенос (если вылез за 16 бит)
            if ((sum and 0xFFFF_0000L) != 0L) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            i += 2
        }
        // финальный перенос (если ещё остался)
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }

        // инверсия (one's complement)
        sum = (sum xor 0xFFFF) and 0xFFFF
        if (sum == 0L) return 65535
        return sum.toInt()
    }

    fun buildUdpPacket(
        buf: ByteArray,
        ipHeaderLen: Int,
        srcPort: Int,
        dstPort: Int,
        totalLen: Int,
        payload: ByteArray,
        srcIp: Int,
        dstIp: Int,
    ) {
        val udpLen = totalLen - ipHeaderLen

        ByteUtils.writeUInt16(buf, ipHeaderLen, srcPort) // srcPort
        ByteUtils.writeUInt16(buf, ipHeaderLen + 2, dstPort) // dstPort
        ByteUtils.writeUInt16(buf, ipHeaderLen + 4, udpLen) // udpLen
        System.arraycopy(payload, 0, buf, ipHeaderLen + 8, payload.size)

        val checksum = calculateUdpIPv4Checksum(buf, ipHeaderLen, udpLen, srcIp, dstIp)
        ByteUtils.writeUInt16(buf, ipHeaderLen, checksum) //checksum
    }
}