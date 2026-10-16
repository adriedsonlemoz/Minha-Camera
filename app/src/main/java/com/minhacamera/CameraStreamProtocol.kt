package com.minhacamera

import java.net.URI
import java.util.Locale

/** Protocolos de mídia com URL direta; ONVIF é descoberta, não um fluxo de vídeo. */
enum class StreamKind(val label: String, val isRtsp: Boolean = false) {
    RTSP("RTSP", true), HTTP_VIDEO("HTTP / MJPEG"), HLS("HLS"), DASH("MPEG-DASH"),
    RTMP("RTMP (experimental)"), RTP("RTP/UDP (experimental)")
}

object CameraStreamProtocol {
    private val allowed = mapOf(
        "rtsp" to StreamKind.RTSP,
        "http" to StreamKind.HTTP_VIDEO,
        "https" to StreamKind.HTTP_VIDEO,
        "rtmp" to StreamKind.RTMP,
        "rtmps" to StreamKind.RTMP,
        "rtmps" to StreamKind.RTMP,
        "rtp" to StreamKind.RTP,
        "udp" to StreamKind.RTP
    )

    fun kind(url: String): StreamKind? {
        if (url.length !in 9..2048) return null
        val uri = try { URI(url.trim()) } catch (_: Exception) { return null }
        val protocol = allowed[uri.scheme?.lowercase(Locale.ROOT)] ?: return null
        if (uri.host.isNullOrBlank() || uri.fragment != null || uri.port > 65535 || uri.port == 0) return null
        if (url.any { it.isWhitespace() || it.isISOControl() }) return null
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        return when {
            protocol == StreamKind.HTTP_VIDEO && path.endsWith(".m3u8") -> StreamKind.HLS
            protocol == StreamKind.HTTP_VIDEO && path.endsWith(".mpd") -> StreamKind.DASH
            else -> protocol
        }
    }

    fun isSupported(url: String): Boolean = kind(url) != null
    fun isRtsp(url: String): Boolean = kind(url)?.isRtsp == true
    fun description(url: String): String = kind(url)?.label ?: "Protocolo não reconhecido"
    fun defaultPort(url: String): Int = when (kind(url)) {
        StreamKind.RTSP -> 554
        StreamKind.HLS, StreamKind.DASH, StreamKind.HTTP_VIDEO ->
            if (url.startsWith("https:", true)) 443 else 80
        StreamKind.RTMP -> 1935
        else -> 0
    }
    fun nextAttempt(currentIndex: Int, transport: RtspTransport, urls: List<String>): RtspAttempt? {
        val current = urls.getOrNull(currentIndex) ?: return null
        return if (isRtsp(current) && transport == RtspTransport.TCP)
            RtspAttempt(currentIndex, RtspTransport.UDP)
        else if (currentIndex + 1 < urls.size) RtspAttempt(currentIndex + 1, RtspTransport.TCP)
        else null
    }
}
