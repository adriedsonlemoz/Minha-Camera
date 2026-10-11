package com.minhacamera

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

object NetUtil {

    @Suppress("DEPRECATION")
    private fun wifiNetwork(ctx: Context): Network? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    // Força o app a usar o Wi-Fi para falar com a câmera, mesmo que o Wi-Fi
    // esteja "sem internet" e o Android prefira os dados móveis.
    fun bindToWifi(ctx: Context) {
        try {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
            val n = wifiNetwork(ctx) ?: return
            cm.bindProcessToNetwork(n)
        } catch (_: Exception) {
        }
    }

    // Ex.: "192.168.100." (3 primeiros números da rede + ponto), ou null sem Wi-Fi.
    fun localPrefix(ctx: Context): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = wifiNetwork(ctx) ?: cm.activeNetwork ?: return null
        val lp = cm.getLinkProperties(net) ?: return null
        val addr = lp.linkAddresses.map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress } ?: return null
        val parts = addr.hostAddress?.split(".") ?: return null
        return if (parts.size == 4) parts.take(3).joinToString(".") + "." else null
    }
}
