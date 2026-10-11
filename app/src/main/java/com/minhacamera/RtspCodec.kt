package com.minhacamera

/** Interpreta só as faixas de vídeo do SDP retornado por RTSP DESCRIBE.
 * O nome da URL (stream1, subtype=0 etc.) NÃO determina o codec.
 */
object RtspCodec {
    fun videoCodec(sdp: String): String? {
        var videoPayloads: Set<String> = emptySet()
        var inVideoSection = false
        val codecs = mutableListOf<String>()
        for (raw in sdp.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("m=")) {
                val parts = line.substring(2).split(Regex("\\s+"))
                inVideoSection = parts.firstOrNull()?.equals("video", ignoreCase = true) == true
                videoPayloads = if (inVideoSection) parts.drop(3).toSet() else emptySet()
            } else if (inVideoSection && line.startsWith("a=rtpmap:", ignoreCase = true)) {
                val value = line.substringAfter(':').trim()
                val payload = value.substringBefore(' ')
                if (payload in videoPayloads) {
                    val enc = value.substringAfter(' ', "").substringBefore('/').uppercase()
                    if (enc.isNotBlank()) codecs.add(enc)
                }
            }
        }
        return when {
            "H264" in codecs -> "H264"
            "H265" in codecs || "HEVC" in codecs -> "H265"
            else -> codecs.firstOrNull()
        }
    }
}
