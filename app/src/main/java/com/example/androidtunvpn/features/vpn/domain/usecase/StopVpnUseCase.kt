package com.example.androidtunvpn.features.vpn.domain.usecase

import com.example.androidtunvpn.features.vpn.domain.repositories.VpnRepository

class StopVpnUseCase(
    private val tunRepo: VpnRepository
) {
    operator fun invoke() = tunRepo.stopVpn()
}