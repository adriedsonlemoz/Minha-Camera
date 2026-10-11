package android.net
import java.net.InetAddress
class Network
class NetworkCapabilities {
    fun hasTransport(transportType: Int): Boolean = true
    companion object { const val TRANSPORT_WIFI = 1 }
}
class LinkAddress(val address: InetAddress, val prefixLength: Int)
class LinkProperties(val linkAddresses: List<LinkAddress>)
open class ConnectivityManager(val prefix: Int = 24, val localIp: String = "192.168.10.20") {
    val allNetworks = arrayOf(Network())
    fun getNetworkCapabilities(network: Network): NetworkCapabilities? = NetworkCapabilities()
    fun getLinkProperties(network: Network): LinkProperties? =
        LinkProperties(listOf(LinkAddress(InetAddress.getByName(localIp), prefix)))
    fun bindProcessToNetwork(network: Network?): Boolean = true
}
