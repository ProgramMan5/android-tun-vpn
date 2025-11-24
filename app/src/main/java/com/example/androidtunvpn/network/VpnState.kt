package com.example.androidtunvpn.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


object VpnState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning


    internal fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
}