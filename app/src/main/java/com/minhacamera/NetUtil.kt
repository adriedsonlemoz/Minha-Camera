package com.minhacamera

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object NetUtil {
    data class ScanRange(val hosts: List<String>, val description: String, val limited: Boolean)

    private fun wifiNetwork(ctx: Context): Network? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    // Câmeras podem funcionar em Wi-Fi sem acesso à internet.
    fun bindToWifi(ctx: Context) {
        try {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
            cm.bindProcessToNetwork(wifiNetwork(ctx))
        } catch (_: Exception) {
        }
    }

    private fun localAddress(ctx: Context): Pair<Long, Int>? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = wifiNetwork(ctx) ?: return null
        val link = cm.getLinkProperties(net) ?: return null
        val address = link.linkAddresses.firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress
        } ?: return null
        val bytes = address.address.address
        val ip = bytes.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 255L) }
        return ip to address.prefixLength
    }

    private fun ipString(value: Long): String =
        (3 downTo 0).joinToString(".") { ((value ushr (it * 8)) and 255L).toString() }

    /** Usa o IPv4 do Wi-Fi; para ponto de acesso, busca somente interfaces WLAN/AP. */
    fun wifiIpv4(ctx: Context): Inet4Address? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val wifi = wifiNetwork(ctx)
        if (cm != null && wifi != null) {
            cm.getLinkProperties(wifi)?.linkAddresses?.firstOrNull {
                it.address is Inet4Address && !it.address.isLoopbackAddress && !it.address.isLinkLocalAddress
            }?.address?.let { return it as Inet4Address }
        }
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.firstNotNullOfOrNull { device ->
                val name = device.name.lowercase()
                if (!device.isUp || !(name.startsWith("wlan") || name.startsWith("ap") ||
                            name.startsWith("swlan") || name.startsWith("softap"))) null
                else device.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            }
        } catch (_: Exception) { null }
    }

    // Mantido para preencher o formulário manual, não para determinar a rede de varredura.
    fun localPrefix(ctx: Context): String? = localAddress(ctx)?.first?.let {
        ipString(it).substringBeforeLast('.') + "."
    }

    /** Respeita a máscara DHCP. Em redes muito grandes, limita a busca TCP ao /24 local,
     * mantendo WS-Discovery para encontrar equipamentos no restante da rede.
     */
    fun scanRange(ctx: Context, maximumHosts: Int = 1022): ScanRange? {
        val (ip, prefix) = localAddress(ctx) ?: return null
        if (prefix !in 1..30) return null
        val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        val network = ip and mask
        val broadcast = network or (mask.inv() and 0xffffffffL)
        var first = network + 1
        var last = broadcast - 1
        var limited = false
        if (last - first + 1 > maximumHosts) {
            // Não saturar a rede tentando até 65.534 endereços de um /16.
            val local24 = ip and 0xffffff00L
            first = maxOf(first, local24 + 1)
            last = minOf(last, local24 + 254)
            limited = true
        }
        val hosts = if (last < first) emptyList() else (first..last)
            .filter { it != ip }.map(::ipString)
        return ScanRange(hosts, "${ipString(network)}/$prefix" + if (limited) " (TCP limitado ao /24 local)" else "", limited)
    }
}
