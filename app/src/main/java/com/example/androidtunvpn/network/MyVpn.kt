package com.example.androidtunvpn.network

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.example.androidtunvpn.network.models.FlowTable
import com.example.androidtunvpn.network.models.PendingRegistrations
import com.example.androidtunvpn.network.models.ProtectFunc
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors


class MyVpn : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    private val executor = Executors.newFixedThreadPool(2)

    private var nioManager: NioManager? = null

    private val pendingRegistrations = PendingRegistrations()

    private val flowTable = FlowTable()

    private val protectFn: ProtectFunc = { socket -> this.protect(socket) }

    private fun startVpn() {
        val builder = Builder()
        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)
        builder.setMtu(1400)
        vpnInterface = builder.establish()

        vpnInterface?.let { pfd ->
            val outputFd = FileOutputStream(pfd.fileDescriptor)
            nioManager = NioManager(outputFd)
            executor.submit({
                nioManager!!.start(
                    pendingRegistrations,
                )
            },"TunNioThread")
            val inputFd = FileInputStream(pfd.fileDescriptor)

            val frameDispatcher = FrameDispatcher(protectFn)

            executor.submit(
                {
                    frameDispatcher.start(
                        inputFd,
                        flowTable,
                        pendingRegistrations
                    )
                },
                "TunReaderThread"
            )
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        vpnInterface?.close()
        super.onDestroy()
    }
}

