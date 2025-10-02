package com.example.androidtunvpn.network.udp

import com.example.androidtunvpn.network.ByteUtils

/**
 * Утилитарные функции для работы с UDP пакетами.
 */
object UdpUtils {
    fun srcPort(buf: ByteArray, udpOffset: Int) = ByteUtils.readUInt16(buf, udpOffset)
    fun dstPort(buf: ByteArray, udpOffset: Int) = ByteUtils.readUInt16(buf, udpOffset + 2)

    fun udpChecksumIPv4(
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