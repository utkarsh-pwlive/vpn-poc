package com.example.vpnsample.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    var isVpnRunning by mutableStateOf(false)
        private set

    fun toggleVpn() {
        isVpnRunning = !isVpnRunning
    }
}