package com.minhacamera

import android.content.Context
import android.net.wifi.WifiManager
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Descobre endpoints ONVIF sem credenciais via WS-Discovery (UDP 3702).
 * Não depende de os equipamentos usarem uma porta RTSP conhecida.
 */
object WsDiscovery {
    data class Device(val ip: String, val xaddrs: List<String>)
    private const val GROUP = "239.255.255.250"
    private const val PORT = 3702

    fun search(ctx: Context, durationMs: Long = 3200): List<Device> {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("MinhaCamera-Onvif")?.apply { setReferenceCounted(false) }
        val devices = linkedMapOf<String, MutableSet<String>>()
        try {
            lock?.acquire()
            DatagramSocket().use { socket ->
                socket.soTimeout = 350
                val id = "urn:uuid:${UUID.randomUUID()}"
                val message = """<?xml version="1.0" encoding="UTF-8"?>
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                      xmlns:a="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                      xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery">
                      <s:Header>
                        <a:Action s:mustUnderstand="1">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>
                        <a:MessageID>$id</a:MessageID>
                        <a:To s:mustUnderstand="1">urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>
                      </s:Header>
                      <s:Body><d:Probe/></s:Body>
                    </s:Envelope>""".trimIndent().toByteArray(Charsets.UTF_8)
                val dest = InetAddress.getByName(GROUP)
                val packet = DatagramPacket(message, message.size, dest, PORT)
                val end = System.nanoTime() + durationMs * 1_000_000L
                var nextProbe = System.nanoTime()
                while (!Thread.currentThread().isInterrupted && System.nanoTime() < end) {
                    if (System.nanoTime() >= nextProbe) {
                        socket.send(packet)
                        nextProbe = System.nanoTime() + 1_100_000_000L
                    }
                    val buf = ByteArray(16384)
                    val response = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(response)
                        val address = response.address
                        if (address !is Inet4Address || address.isLoopbackAddress) continue
                        val xml = String(response.data, response.offset, response.length, Charsets.UTF_8)
                        val matches = parseProbeMatches(xml, id)
                        if (matches != null) {
                            val urls = devices.getOrPut(address.hostAddress ?: continue) { linkedSetOf() }
                            urls.addAll(matches)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Continuar escutando respostas até o prazo da descoberta.
                    } catch (_: Exception) {
                        // Uma resposta malformada não pode interromper toda a descoberta.
                    }
                }
            }
        } catch (_: Exception) {
            // Em alguns roteadores o multicast é bloqueado: varredura TCP continua funcionando.
        } finally {
            try { if (lock?.isHeld == true) lock.release() } catch (_: Exception) { }
        }
        return devices.map { Device(it.key, it.value.toList()) }
    }

    internal fun parseProbeMatches(xml: String, messageId: String): List<String>? {
        if (xml.length > 16384) return null
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val matches = doc.getElementsByTagNameNS("*", "ProbeMatch")
        if (matches.length == 0) return null
        val relatesTo = doc.getElementsByTagNameNS("*", "RelatesTo")
        if (relatesTo.length > 0 && relatesTo.item(0).textContent.trim() != messageId) return null
        val urls = linkedSetOf<String>()
        for (index in 0 until matches.length) {
            val entry = matches.item(index) as? Element ?: continue
            val xaddrs = entry.getElementsByTagNameNS("*", "XAddrs")
            if (xaddrs.length == 0) continue
            xaddrs.item(0).textContent.trim().split(Regex("\\s+")).forEach { url ->
                if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                    urls.add(url)
                }
            }
        }
        return urls.toList()
    }
}
