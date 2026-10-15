package com.minhacamera

import java.net.URI

/** Aceita somente a rota temporária publicada pelo modo câmera, em IPv4 de rede local. */
object PhoneCameraUrls {
    private val tokenPattern = Regex("/live/[0-9a-f]{32}")

    /** Endereço de comando derivado exclusivamente da URL privada MJPEG validada. */
    fun recordingCommandUrl(input: String, enabled: Boolean): String? {
        val live = normalize(input) ?: return null
        return live.replace("/live/", "/control/") + if (enabled) "/record/start" else "/record/stop"
    }

    fun normalize(input: String): String? {
        val raw = input.trim()
        if (raw.length !in 35..160 || raw.any { it.isWhitespace() }) return null
        return try {
            val uri = URI(raw)
            if (!uri.scheme.equals("http", true) || uri.userInfo != null ||
                uri.query != null || uri.fragment != null || uri.rawAuthority == null ||
                uri.port !in 1..65535 || !tokenPattern.matches(uri.rawPath.orEmpty())) return null
            val ip = uri.host?.split('.') ?: return null
            if (ip.size != 4 || ip.any { it.isEmpty() || (it.length > 1 && it[0] == '0') || it.toIntOrNull() !in 0..255 }) return null
            val o = ip.map { it.toInt() }
            val local = o[0] == 10 || (o[0] == 172 && o[1] in 16..31) ||
                (o[0] == 192 && o[1] == 168) || (o[0] == 169 && o[1] == 254) ||
                (o[0] == 100 && o[1] in 64..127)
            if (!local) return null
            "http://${ip.joinToString(".")}:${uri.port}${uri.rawPath}"
        } catch (_: Exception) { null }
    }
}
