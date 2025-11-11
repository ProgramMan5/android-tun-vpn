package com.example.androidtunvpn.network.utils.ip.v4
import com.example.androidtunvpn.network.utils.ByteUtils


 object IpPacketV4 {

    fun headerLen(buf: ByteArray) = (buf[0].toInt() and 0x0F) * 4
    fun totalLen(buf: ByteArray) = ByteUtils.readUInt16(buf, 2)
    fun protocol(buf: ByteArray) = buf[9].toInt() and 0xFF
    fun checksum(buf: ByteArray) = ByteUtils.readUInt16(buf, 10)
    fun srcIp(buf: ByteArray) = ByteUtils.readIntBE(buf, 12)
    fun dstIp(buf: ByteArray) = ByteUtils.readIntBE(buf, 16)
    private fun calculateIpv4Checksum(buf: ByteArray, offset: Int, len: Int): Int {
        var sum: Long = 0
        var i = offset
        val end = offset + len
        while (i < offset + len) {
            val hi = buf[i].toInt() and 0xFF
            val lo = if (i + 1 < end) buf[i + 1].toInt() and 0xFF else 0
            sum += (hi shl 8) + lo
            if (sum > 0xFFFF) sum = (sum and 0xFFFF) + (sum ushr 16)
            i += 2
        }
        sum = (sum xor 0xFFFF) and 0xFFFF
        return sum.toInt()
    }

    fun buildIpv4Header(
        srcIp: Int,
        dstIp: Int,
        headerLen: Int,
        totalLen: Int,
    ): ByteArray {
        val buf = ByteArray(totalLen)
        buf[0] = ((4 shl 4) or headerLen / 4).toByte() //version + IHL
        buf[1] = 0 // TOS
        ByteUtils.writeUInt16(buf, 2, totalLen) // totalLen
        ByteUtils.writeUInt16(buf, 4, 0) // ID
        ByteUtils.writeUInt16(buf, 6, 0) // Flags + Fragment Offset
        buf[8] = 64 // TTL
        buf[9] = 17 // protocol = UDP
        ByteUtils.writeIntBE(buf, 12, srcIp) // srcIp
        ByteUtils.writeIntBE(buf, 16, dstIp) // dstIp
        val checksum = calculateIpv4Checksum(buf, 0, headerLen)
        ByteUtils.writeUInt16(buf, 10, checksum) // checksum
        return buf
    }
}
