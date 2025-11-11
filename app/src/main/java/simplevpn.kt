

import com.example.androidtunvpn.network.utils.ip.v4.IpPacketV4
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.example.androidtunvpn.network.utils.ByteUtils
import com.example.androidtunvpn.network.utils.udp.UdpPacket
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

        var srcIp: Int
        var dstIp: Int
        var srcPort: Int
        var dstPort: Int
        var offset: Int
        var payloadLen: Int

        while (true) {
            val read = try {
                input.read(buf)
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
            if (read <= 0) continue
            val totalLen = ByteUtils.readUInt16(buf, 2)
            if (totalLen > read) continue

            val version = (buf[0].toInt() ushr 4) and 0xF
            if (version != 4) continue

            val protocol = IpPacketV4.protocol(buf)
            if (protocol != 17) continue

            val ipPacketHeaderLen = IpPacketV4.headerLen(buf)


            offset = (ipPacketHeaderLen + 8)
            payloadLen = totalLen - offset
            srcIp = IpPacketV4.srcIp(buf)
            dstIp = IpPacketV4.dstIp(buf)
            srcPort = UdpPacket.srcPort(buf,ipPacketHeaderLen)
            dstPort = UdpPacket.dstPort(buf,ipPacketHeaderLen)


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
                val pkt = DatagramPacket(buf,offset, payloadLen, inet, dstPort)
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

                val ipPacket = IpPacketV4.buildIpv4Header(key.dstIp, key.srcIp, 20, respLen)

                UdpPacket.buildUdpPacket(
                    ipPacket,
                    20,
                    key.srcPort,
                    key.dstPort,
                    respLen,
                    respData,
                    key.srcIp,
                    key.dstIp,
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
}

