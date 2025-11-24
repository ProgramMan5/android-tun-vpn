package com.example.androidtunvpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.androidtunvpn.features.vpn.data.VpnRepositoryImpl
import com.example.androidtunvpn.features.vpn.domain.usecase.StartVpnUseCase
import com.example.androidtunvpn.features.vpn.domain.usecase.StopVpnUseCase
import com.example.androidtunvpn.features.vpn.presentation.VpnScreen
import com.example.androidtunvpn.features.vpn.presentation.VpnViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = VpnRepositoryImpl(applicationContext)
        val start = StartVpnUseCase(repo)
        val stop = StopVpnUseCase(repo)
        val vm = VpnViewModel(start, stop)

        setContent {
            VpnScreen(vm)
        }
    }
}