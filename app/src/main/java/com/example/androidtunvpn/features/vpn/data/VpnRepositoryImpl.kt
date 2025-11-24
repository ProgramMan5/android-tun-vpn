package com.example.androidtunvpn.features.vpn.data

import com.example.androidtunvpn.features.vpn.domain.repositories.VpnRepository


import android.content.Context
import android.content.Intent
import com.example.androidtunvpn.network.MyVpn
import com.example.androidtunvpn.network.VpnState
import kotlinx.coroutines.flow.StateFlow

class VpnRepositoryImpl(private val context: Context) : VpnRepository {


     override val isRunning: StateFlow<Boolean> = VpnState.isRunning

    override fun startVpn() {
        val intent = Intent(context, MyVpn::class.java).apply {
            action = MyVpn.ACTION_CONNECT
        }

        context.startService(intent)
    }

    override fun stopVpn() {
        val intent = Intent(context, MyVpn::class.java).apply {
            action = MyVpn.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
}