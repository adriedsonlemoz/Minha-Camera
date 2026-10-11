package com.minhacamera

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Reports what the device advertises, never infers functionality from a brand. */
object OnvifIdentity {
    data class Info(val manufacturer: String, val model: String, val firmware: String,
                    val ptz: Boolean, val recording: Boolean, val search: Boolean, val replay: Boolean)
    fun parse(infoXml: String, servicesXml: String, capsXml: String): Info? {
        fun document(xml: String): org.w3c.dom.Document? = if (xml.isBlank()) null else runCatching {
            require(xml.length <= 262144 && !xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true))
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isExpandEntityReferences = false
            }.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        }.getOrNull()
        val info = document(infoXml)
        val services = document(servicesXml)
        val caps = document(capsXml)
        val response = info?.getElementsByTagNameNS("*", "GetDeviceInformationResponse")?.item(0) as? Element
        val hasServices = (services?.getElementsByTagNameNS("*", "GetServicesResponse")?.length ?: 0) > 0
        val hasCaps = (caps?.getElementsByTagNameNS("*", "GetCapabilitiesResponse")?.length ?: 0) > 0
        if (response == null && !hasServices && !hasCaps) return null
        fun text(name: String) = response?.getElementsByTagNameNS("*", name)?.item(0)?.textContent?.trim()?.take(160).orEmpty()
        val namespaces = mutableSetOf<String>()
        val nodes = services?.getElementsByTagNameNS("*", "Namespace")
        if (hasServices && nodes != null) for (i in 0 until nodes.length) namespaces.add(nodes.item(i).textContent.trim())
        fun advertised(namespace: String, cap: String): Boolean = namespace in namespaces ||
            (hasCaps && (caps?.getElementsByTagNameNS("*", cap)?.length ?: 0) > 0)
        return Info(text("Manufacturer"), text("Model"), text("FirmwareVersion"),
            advertised("http://www.onvif.org/ver20/ptz/wsdl", "PTZ"),
            advertised("http://www.onvif.org/ver10/recording/wsdl", "Recording"),
            advertised("http://www.onvif.org/ver10/search/wsdl", "Search"),
            advertised("http://www.onvif.org/ver10/replay/wsdl", "Replay"))
    }
}
