package com.example.androidtunvpn.network.utils

/**
 * Утилитарные функции для чтения и записи целых чисел в байтовых массивах.
 */
object ByteUtils {
    /** Читает 16-битное беззнаковое число из буфера по смещению. */
    fun readUInt16(buf: ByteArray, offset: Int): Int {
        val hi = buf[offset].toInt() and 0xFF
        val lo = buf[offset + 1].toInt() and 0xFF
        return (hi shl 8) or lo
    }

    /** Читает 32-битное целое (Int) в big-endian порядке из буфера по смещению. */
    fun readIntBE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF shl 24) or
                (buf[offset + 1].toInt() and 0xFF shl 16) or
                (buf[offset + 2].toInt() and 0xFF shl 8) or
                (buf[offset + 3].toInt() and 0xFF)
    }

    /** Записывает 16-битное беззнаковое число в буфер в big-endian порядке. */
    fun writeUInt16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }

    /** Записывает 32-битное беззнаковое число в буфер в big-endian порядке. */
    fun writeIntBE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    /** Конвертирует 32-битный IPv4-адрес в массив из 4 байт (big-endian). */
    fun intToBytes(ip: Int): ByteArray {
        return byteArrayOf(
            ((ip ushr 24) and 0xFF).toByte(),
            ((ip ushr 16) and 0xFF).toByte(),
            ((ip ushr 8) and 0xFF).toByte(),
            (ip and 0xFF).toByte()
        )
    }
}