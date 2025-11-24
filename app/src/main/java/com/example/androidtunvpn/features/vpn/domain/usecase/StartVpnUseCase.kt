package com.example.androidtunvpn.features.vpn.domain.usecase

import android.util.Log
import com.example.androidtunvpn.features.vpn.domain.repositories.VpnRepository

class StartVpnUseCase(
    private val tunRepo: VpnRepository,
) {
    operator fun invoke() {
        tunRepo.startVpn()
        Log.d("VPN","UseCase start")
    }

}