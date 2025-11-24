package com.example.androidtunvpn.features.vpn.domain.repositories

import kotlinx.coroutines.flow.StateFlow

interface VpnRepository {
    val isRunning: StateFlow<Boolean>
    fun startVpn(){}
    fun stopVpn(){}
}