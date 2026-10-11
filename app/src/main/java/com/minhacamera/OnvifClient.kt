package com.minhacamera

import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Pergunta à própria câmera (ONVIF) qual é o endereço RTSP do vídeo.
object OnvifClient {

    class Result(val uri: String?, val log: String)
    private class Resp(val code: Int, val body: String)

    private val PORTS = listOf(80, 8080, 2020, 8000, 5000, 8899, 8888, 8081)

    private const val NS_DEV = "http://www.onvif.org/ver10/device/wsdl"
    private const val NS_MEDIA = "http://www.onvif.org/ver10/media/wsdl"
    private const val NS_SCHEMA = "http://www.onvif.org/ver10/schema"
    private const val WSSE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
    private const val WSU = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
    private const val PWD_TYPE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest"
    private const val NONCE_ENC = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary"

    fun findStreamUri(ip: String, user: String, pass: String): Result {
        val log = StringBuilder()
        for (port in PORTS) {
            if (!RtspProbe.isPortOpen(ip, port, 600)) continue
            val uri = tryPort(ip, port, user, pass, log)
            if (uri != null) return Result(uri, log.toString().trim())
        }
        if (log.isEmpty()) log.append("nenhuma porta ONVIF aberta")
        return Result(null, log.toString().trim())
    }

    private fun tryPort(ip: String, port: Int, user: String, pass: String, log: StringBuilder): String? {
        val base = "http://$ip:$port"
        val deviceUrl = "$base/onvif/device_service"
        val offset = cameraTimeOffset(deviceUrl)

        val caps = soap(
            deviceUrl, user, pass, offset,
            "<GetCapabilities xmlns=\"$NS_DEV\"><Category>Media</Category></GetCapabilities>"
        )
        log.append("porta $port: capacidades ${caps.code}")
        var mediaUrl: String? = null
        if (caps.code == 200) {
            mediaUrl = Regex(
                "<(?:\\w+:)?Media>.*?<(?:\\w+:)?XAddr>([^<]+)<", RegexOption.DOT_MATCHES_ALL
            ).find(caps.body)?.groupValues?.get(1)?.trim()?.let { fixHost(it, ip) }
        } else if (caps.code == 401 || caps.body.contains("NotAuthorized", ignoreCase = true)) {
            log.append(" (login ONVIF recusado)\n")
            return null
        }

        val candidates = (listOfNotNull(mediaUrl) +
            listOf("/onvif/media_service", "/onvif/Media", "/onvif/media", "/onvif/service", "/onvif/device_service")
                .map { "$base$it" }).distinct()

        for (url in candidates) {
            val prof = soap(url, user, pass, offset, "<GetProfiles xmlns=\"$NS_MEDIA\"/>")
            log.append(", perfis ${prof.code}")
            val token = Regex("<(?:\\w+:)?Profiles[^>]*token=[\"']([^\"']+)[\"']")
                .find(prof.body)?.groupValues?.get(1) ?: continue
            val body = "<GetStreamUri xmlns=\"$NS_MEDIA\"><StreamSetup>" +
                "<Stream xmlns=\"$NS_SCHEMA\">RTP-Unicast</Stream>" +
                "<Transport xmlns=\"$NS_SCHEMA\"><Protocol>RTSP</Protocol></Transport>" +
                "</StreamSetup><ProfileToken>${xml(token)}</ProfileToken></GetStreamUri>"
            val su = soap(url, user, pass, offset, body)
            log.append(", uri ${su.code}")
            val uri = Regex("<(?:\\w+:)?Uri>([^<]+)<").find(su.body)
                ?.groupValues?.get(1)?.trim()?.replace("&amp;", "&")
            if (!uri.isNullOrEmpty()) {
                log.append(" OK\n")
                return uri
            }
        }
        log.append('\n')
        return null
    }

    // Diferença entre o relógio da câmera e o do celular (ONVIF recusa horário muito diferente).
    private fun cameraTimeOffset(url: String): Long {
        val r = soap(url, null, "", 0L, "<GetSystemDateAndTime xmlns=\"$NS_DEV\"/>")
        if (r.code != 200) return 0L
        val utc = Regex(
            "<(?:\\w+:)?UTCDateTime>(.*?)</(?:\\w+:)?UTCDateTime>", RegexOption.DOT_MATCHES_ALL
        ).find(r.body)?.groupValues?.get(1) ?: return 0L

        fun num(tag: String): Int? =
            Regex("<(?:\\w+:)?$tag>\\s*(\\d+)\\s*<").find(utc)?.groupValues?.get(1)?.toIntOrNull()

        val y = num("Year") ?: return 0L
        val mo = num("Month") ?: return 0L
        val d = num("Day") ?: return 0L
        val h = num("Hour") ?: return 0L
        val mi = num("Minute") ?: return 0L
        val s = num("Second") ?: return 0L
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(y, mo - 1, d, h, mi, s)
        return cal.timeInMillis - System.currentTimeMillis()
    }

    private fun soap(url: String, user: String?, pass: String, offsetMs: Long, body: String): Resp {
        val header = if (user != null) securityHeader(user, pass, offsetMs) else ""
        val envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">" +
            (if (header.isNotEmpty()) "<s:Header>$header</s:Header>" else "") +
            "<s:Body>$body</s:Body></s:Envelope>"
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 3000
            c.readTimeout = 5000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
            c.outputStream.use { it.write(envelope.toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            Resp(code, text)
        } catch (e: Exception) {
            Resp(-1, e.message ?: "")
        }
    }

    private fun securityHeader(user: String, pass: String, offsetMs: Long): String {
        val nonce = ByteArray(16)
        SecureRandom().nextBytes(nonce)
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        val created = fmt.format(Date(System.currentTimeMillis() + offsetMs))
        val sha = MessageDigest.getInstance("SHA-1")
        sha.update(nonce)
        sha.update(created.toByteArray())
        sha.update(pass.toByteArray())
        val digest = Base64.encodeToString(sha.digest(), Base64.NO_WRAP)
        val nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
        return "<Security xmlns=\"$WSSE\" s:mustUnderstand=\"1\"><UsernameToken>" +
            "<Username>${xml(user)}</Username>" +
            "<Password Type=\"$PWD_TYPE\">$digest</Password>" +
            "<Nonce EncodingType=\"$NONCE_ENC\">$nonceB64</Nonce>" +
            "<Created xmlns=\"$WSU\">$created</Created>" +
            "</UsernameToken></Security>"
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun fixHost(url: String, ip: String): String = try {
        val u = URL(url)
        URL(u.protocol, ip, u.port, u.file).toString()
    } catch (_: Exception) {
        url
    }
}
