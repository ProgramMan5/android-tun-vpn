package com.example.androidtunvpn.network.ip.v4

import com.example.androidtunvpn.network.ByteUtils

/**
 * Утилитарные функции для работы с Ipv4 пакетами.
 */
object IpPacketUtils {
    fun version(buf: ByteArray) = (buf[0].toInt() ushr 4) and 0xF
    fun ihl(buf: ByteArray) = buf[0].toInt() and 0x0F
    fun protocol(buf: ByteArray) = buf[9].toInt() and 0xFF
    fun srcIp(buf: ByteArray) = ByteUtils.readIntBE(buf, 12)
    fun dstIp(buf: ByteArray) = ByteUtils.readIntBE(buf, 16)

    fun ipChecksum(buf: ByteArray, offset: Int, len: Int): Int {
        var sum: Long = 0
        var i = offset
        try {
            while (i < offset + len) {
                val hi = buf[i].toInt() and 0xFF
                val lo = buf[i + 1].toInt() and 0xFF
                sum += (hi shl 8) + lo
                if (sum and 0xFFFF0000L != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
                i += 2
            }
        } catch (e: Exception) {
            return -1
        }
        sum = sum.inv() and 0xFFFF
        return sum.toInt()
    }
}