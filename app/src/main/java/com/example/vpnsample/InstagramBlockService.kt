package com.example.vpnsample

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class InstagramBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        val builder = Builder()
            .setSession("DNSBlockVPN")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("8.8.8.8")

            .addRoute("8.8.8.8", 32)
            .addRoute("2001:4860:4860::8888", 128)

        vpnInterface = builder.establish()

        vpnThread = Thread {
            vpnInterface?.let { DnsProcessor.run(it, this) }
        }

        vpnThread?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        DnsProcessor.stop()
        vpnInterface?.close()
    }
}

