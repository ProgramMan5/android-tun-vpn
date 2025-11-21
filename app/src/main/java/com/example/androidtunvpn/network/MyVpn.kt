package com.example.androidtunvpn.network

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.example.androidtunvpn.network.models.FlowModels
import com.example.androidtunvpn.network.models.ProtectFunc
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors



class MyVpn : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    private val executor = Executors.newSingleThreadExecutor()



    private var nioManager: NioManager? = null


    private val pendingRegistrations = PendingRegistrations()

    private val flowTable = ConcurrentHashMap<FlowModels.FlowKey, FlowModels.Flow>()

    val protectFn: ProtectFunc = { socket -> this.protect(socket) }



    private fun startVpn() {
        val builder = Builder()
        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)
        builder.setMtu(1400)
        vpnInterface = builder.establish()

        vpnInterface?.let { pfd ->
            val tunOutput = FileOutputStream(pfd.fileDescriptor)
            nioManager = NioManager(tunOutput).also {
                executor.submit(it)
            }
            val inputFd = FileInputStream(pfd.fileDescriptor)

            val frameDispatcher = FrameDispatcher(protectFn)

            Thread(Runnable { frameDispatcher.start(inputFd, flowTable, pendingRegistrations) }, "TunReaderThread").start()
        }
    }




    override fun onDestroy() {
        nioManager?.stop()
        executor.shutdownNow()
        vpnInterface?.close()
        super.onDestroy()
    }
}

