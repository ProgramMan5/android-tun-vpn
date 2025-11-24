package com.example.androidtunvpn.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.androidtunvpn.network.models.FlowTable
import com.example.androidtunvpn.network.models.PendingRegistrations
import com.example.androidtunvpn.network.models.ProtectFunc
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors


class MyVpn : VpnService() {

    // --- КОНСТАНТЫ КОМАНД И УВЕДОМЛЕНИЯ ---
    companion object {
        const val ACTION_CONNECT = "com.example.androidtunvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.androidtunvpn.DISCONNECT"
        const val CHANNEL_ID = "VPN_CHANNEL_ID"
        const val NOTIFICATION_ID = 1
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val executor = Executors.newFixedThreadPool(2)
    private var nioManager: NioManager? = null
    private val pendingRegistrations = PendingRegistrations()
    private val flowTable = FlowTable()
    private val protectFn: ProtectFunc = { socket -> this.protect(socket) }


    // === ГЛАВНЫЙ ВХОД ===
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_CONNECT -> {
                if (!VpnState.isRunning.value) {
                    startForegroundNotification()
                    startVpn()
                    VpnState.setRunning(true)
                }
            }
            ACTION_DISCONNECT -> {
                stopVpn()
                VpnState.setRunning(false)
            }
        }
        return START_STICKY
    }

    // === ЛОГИКА ЗАПУСКА ===
    private fun startVpn() {
        Log.i("MyVpn", "Starting VPN")
        try {
            val builder = Builder()
            builder.addAddress("10.0.0.2", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1400)
            builder.setSession("MyTunSession") // Имя сессии

            vpnInterface = builder.establish()

            vpnInterface?.let { pfd ->
                val outputFd = FileOutputStream(pfd.fileDescriptor)
                nioManager = NioManager(outputFd)

                // Запуск NIO Manager (Чтение из интернета, запись в TUN)
                executor.submit({
                    nioManager!!.start(pendingRegistrations)
                }, "TunNioThread")

                val inputFd = FileInputStream(pfd.fileDescriptor)
                val frameDispatcher = FrameDispatcher(protectFn)

                // Запуск Frame Dispatcher (Чтение из TUN, запись в интернет)
                executor.submit({
                    frameDispatcher.start(inputFd, flowTable, pendingRegistrations)
                }, "TunReaderThread")
            }
        } catch (e: Exception) {
            Log.e("MyVpn", "Error setting up VPN interface", e)
            stopVpn()
        }
    }

    // === ЛОГИКА ОСТАНОВКИ ===
    private fun stopVpn() {
        Log.i("MyVpn", "Stopping VPN")
        try {
            // Закрываем File Descriptors
            vpnInterface?.close()
            vpnInterface = null
            // Принудительно останавливаем все потоки и очищаем очередь
            executor.shutdownNow()
            nioManager?.close()
        } catch (e: Exception) {
            Log.e("MyVpn", "Error during stop VPN", e)
        }
        // Убираем уведомление и убиваем сервис
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // === УВЕДОМЛЕНИЕ (ОБЯЗАТЕЛЬНОЕ ТРЕБОВАНИЕ FOREGROUND SERVICE) ===
    private fun startForegroundNotification() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Connection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // ВАЖНО: setSmallIcon должен указывать на ресурс в R.drawable,
        // а не android.R.drawable в реальном приложении.
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyVPN Active")
            .setContentText("Traffic is secured and routed.")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Неубираемое уведомление
            .build()
    }

    // === ОЧИСТКА ===
    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}