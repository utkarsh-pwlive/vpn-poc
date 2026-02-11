package com.example.vpnsample.presentation

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.vpnsample.InstagramBlockVpnService
import com.example.vpnsample.presentation.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpn()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainScreen(
                isRunning = viewModel.isVpnRunning,
                onToggle = { handleVpnToggle() }
            )
        }
    }

    private fun handleVpnToggle() {
        if (viewModel.isVpnRunning) {
            stopVpn()
        } else {
            requestVpnPermission()
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpn()
        }
    }

    private fun startVpn() {
        startService(Intent(this, InstagramBlockVpnService::class.java))
        viewModel.toggleVpn()
    }

    private fun stopVpn() {
        stopService(Intent(this, InstagramBlockVpnService::class.java))
        viewModel.toggleVpn()
    }
}