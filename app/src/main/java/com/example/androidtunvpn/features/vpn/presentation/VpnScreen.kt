package com.example.androidtunvpn.features.vpn.presentation

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun VpnScreen(viewModel: VpnViewModel) {
    val context = LocalContext.current
    var flag = false

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.start()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = {
            if (flag) {
                viewModel.stop()
                flag = false
            } else {
                val activity = context as? Activity ?: return@Button
                val intent = VpnService.prepare(activity)
                if (intent != null) {
                    launcher.launch(intent)
                } else {
                    viewModel.start()
                    flag = true
                }
            }
        }) {
            Text(text = if (flag) "Выключить VPN" else "Включить VPN")
        }
    }

}