package com.example.simplevpn

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.example.androidtunvpn.network.ByteUtils
import com.example.androidtunvpn.network.ip.v4.IpPacketUtils
import com.example.androidtunvpn.network.ip.v4.IpPacketV4
import com.example.androidtunvpn.network.udp.UdpPacket
import com.example.androidtunvpn.network.udp.UdpUtils
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors


class SimpleVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null


    private val executor = Executors.newCachedThreadPool()


    private val flowTable = ConcurrentHashMap<FlowKey, Flow>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        startVpn()
        return Service.START_STICKY
    }

    override fun onDestroy() {

        vpnInterface?.close()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun startVpn() {
        val builder = Builder()

        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)
        builder.setMtu(1400)
        vpnInterface = builder.establish() //


        vpnInterface?.let { pfd ->
            executor.submit { tunLoop(pfd.fileDescriptor) }
        }
    }


    data class FlowKey(val srcIp: Int, val srcPort: Int, val dstIp: Int, val dstPort: Int)


    data class Flow(val socket: DatagramSocket, val lastSeen: Long)


    private fun tunLoop(tunFd: java.io.FileDescriptor) {
        val input = FileInputStream(tunFd)
        val output = FileOutputStream(tunFd)
        val buf = ByteArray(65535)

        while (true) {
            val read = try {
                input.read(buf)
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
            if (read <= 0) continue


            val version = IpPacketUtils.version(buf)
            if (version != 4) continue
            val ipPacketV4 = IpPacketV4.parseFrom(buf, read)
            val ipHeaderLen = ipPacketV4.headerLen
            val protocol = ipPacketV4.protocol
            if (protocol != 17) continue
            val srcIp = ipPacketV4.srcIp
            val dstIp = ipPacketV4.dstIp


            val udp = UdpPacket.parseFrom(buf, ipHeaderLen)
            val srcPort = udp.srcPort
            val dstPort = udp.dstPort
            val payloadLen = udp.payloadLen
            if (payloadLen <= 0 || ipHeaderLen + 8 + payloadLen > read) continue


            val payload = buf.copyOfRange(ipHeaderLen + 8, ipHeaderLen + 8 + payloadLen)


            val key = FlowKey(srcIp, srcPort, dstIp, dstPort)
            var flow = flowTable[key]
            if (flow == null) {

                val socket = DatagramSocket()
                socket.reuseAddress = true

                val ok = protect(socket)
                if (!ok) {
                    socket.close()
                    continue
                }
                flow = Flow(socket, System.currentTimeMillis())
                flowTable[key] = flow

                startReceiverForFlow(key, flow, output)
            } else {

                flowTable[key] = flow.copy(lastSeen = System.currentTimeMillis())
            }


            try {
                val inet = InetAddress.getByAddress(ByteUtils.intToBytes(dstIp))
                val pkt = DatagramPacket(payload, payload.size, inet, dstPort)
                flow.socket.send(pkt)
            } catch (e: Exception) {
                e.printStackTrace()
            }


            gcFlows()
        }
    }


    private fun startReceiverForFlow(key: FlowKey, flow: Flow, tunOut: FileOutputStream) {
        executor.submit {
            val sock = flow.socket
            val recvBuf = ByteArray(65535)
            val packet = DatagramPacket(recvBuf, recvBuf.size)
            while (!sock.isClosed) {
                try {
                    sock.receive(packet)
                } catch (e: Exception) {
                    break
                }
                val respLen = packet.length
                val respData = packet.data.copyOfRange(packet.offset, packet.offset + respLen)


                val ipPacket = buildIpv4UdpPacket(
                    srcIp = key.dstIp, dstIp = key.srcIp,
                    srcPort = key.dstPort, dstPort = key.srcPort,
                    payload = respData
                )
                try {
                    tunOut.write(ipPacket)
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }


                flowTable.computeIfPresent(key) { _, old -> old.copy(lastSeen = System.currentTimeMillis()) }
            }
            try {
                sock.close()
            } catch (_: Exception) {
            }
            flowTable.remove(key)
        }
    }


    private fun gcFlows() {
        val now = System.currentTimeMillis()
        val it = flowTable.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (now - e.value.lastSeen > 30_000) {
                try {
                    e.value.socket.close()
                } catch (_: Exception) {
                }
                it.remove()
            }
        }
    }


    private fun buildIpv4UdpPacket(
        srcIp: Int,
        dstIp: Int,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpLen = 8 + payload.size
        val totalLen = ipHeaderLen + udpLen
        val buf = ByteArray(totalLen)

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
        val ipChecksum = IpPacketUtils.ipChecksum(buf, 0, ipHeaderLen)
        ByteUtils.writeUInt16(buf, 10, ipChecksum)

        if (ipChecksum == -1) {
            println("Ip cheksum error")
        }

        val udpChecksum = UdpUtils.udpChecksumIPv4(buf, ipHeaderLen, udpLen, srcIp, dstIp)
        ByteUtils.writeUInt16(buf, udpOff + 6, udpChecksum)

        if (udpChecksum == -1) {
            println("UDP cheksum error")
        }

        return buf
    }
}
