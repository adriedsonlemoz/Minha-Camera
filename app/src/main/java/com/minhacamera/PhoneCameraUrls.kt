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

    fun ecoCommandUrl(input: String, enabled: Boolean): String? {
        val live = normalize(input) ?: return null
        return live.replace("/live/", "/control/") + if (enabled) "/eco/enable" else "/eco/disable"
    }

    /** Parâmetros de controle são enumerações fechadas para impedir requisições arbitrárias. */
    fun qualityCommandUrl(input: String, quality: Int): String? =
        if (quality in listOf(360, 480, 720, 768, 1080)) controlUrl(input, "quality/$quality") else null

    fun fpsCommandUrl(input: String, fps: Int): String? =
        if (fps in listOf(5, 10, 15, 24, 30)) controlUrl(input, "fps/$fps") else null

    fun facingCommandUrl(input: String, front: Boolean): String? =
        controlUrl(input, if (front) "camera/front" else "camera/back")

    fun photoCommandUrl(input: String): String? = controlUrl(input, "photo")

    /** Zoom absoluto normalizado; nunca adiciona um sufixo fora de 0..100 à URL autenticada. */
    fun zoomCommandUrl(input: String, percent: Int): String? =
        if (percent in 0..100) controlUrl(input, "zoom/$percent") else null

    fun codecCommandUrl(input: String, codec: String): String? =
        if (codec == "h264" || codec == "mjpeg") controlUrl(input, "codec/$codec") else null

    fun statusUrl(input: String): String? = controlUrl(input, "status")

    fun diagnosticUrl(input: String): String? = controlUrl(input, "diagnostic")

    fun mcrpInfoUrl(input: String): String? = controlUrl(input, "mcrp/info")
    fun mcrpMediaUrl(input: String): String? = controlUrl(input, "mcrp/media")
    fun mcrpMediaFileUrl(input: String, id: String): String? =
        if (id.matches(Regex("[A-Za-z0-9._-]{1,180}"))) controlUrl(input, "mcrp/media/$id") else null

    fun h264Url(input: String): String? = normalize(input)?.replace("/live/", "/h264/")

    private fun controlUrl(input: String, suffix: String): String? =
        normalize(input)?.replace("/live/", "/control/")?.plus("/$suffix")

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
