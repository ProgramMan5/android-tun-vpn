package com.example.androidtunvpn.features.vpn.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.androidtunvpn.features.vpn.domain.usecase.StartVpnUseCase
import com.example.androidtunvpn.features.vpn.domain.usecase.StopVpnUseCase

class VpnViewModel(
    private val startTun: StartVpnUseCase,
    private val stopTun: StopVpnUseCase,
) : ViewModel() {

    fun start() {
        startTun.invoke()
        Log.d("VPN","ViewModel start")
    }

    fun stop() {
        stopTun.invoke()
        Log.d("VPN","ViewModel stop")
    }
}