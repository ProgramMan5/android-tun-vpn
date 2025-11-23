package com.example.androidtunvpn.network.utils.tcp

import com.example.androidtunvpn.network.utils.ByteUtils

object TcpUtils {
    fun srcPort(buf: ByteArray) = ByteUtils.readUInt16(buf, 0)
    fun dstPort(buf: ByteArray) = ByteUtils.readUInt16(buf, 2)
    fun sqnNumber(buf: ByteArray) = ByteUtils.readIntBE(buf, 4)
    fun ackNumber(buf: ByteArray) = ByteUtils.readIntBE(buf, 8)
    fun headerLen(buf: ByteArray) = (buf[12].toInt() ushr 4) and 0xF
    fun reserved(buf: ByteArray) =
        ((buf[12].toInt() and 0x0F) shl 2) or ((buf[13].toInt() ushr 6) and 0xF)
    fun flags(buf: ByteArray) = (buf[13].toInt() ushr 2) and 0xFFF
    fun winSize(buf: ByteArray) = ByteUtils.readUInt16(buf, 14)
    fun checksum(buf: ByteArray) = ByteUtils.readUInt16(buf, 16)
    fun urgPointer(buf: ByteArray) = ByteUtils.readUInt16(buf, 18)
    fun calculateTcpIpv4Checksum(
        buf: ByteArray,
        udpOffset: Int,
        udpLen: Int,
        srcIp: Int,
        dstIp: Int
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
        try {
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
        } catch (e: Exception) {
            return -1
        }


        // инверсия (one's complement)
        sum = sum.inv() and 0xFFFF

        if (sum == 0L) return 65535

        return sum.toInt()
    }
}