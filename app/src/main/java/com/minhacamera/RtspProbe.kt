package com.minhacamera

import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

enum class ProbeStatus { OK, AUTH_FAIL, NOT_FOUND, ERROR }

data class ProbeResult(val status: ProbeStatus, val detail: String)

object RtspProbe {

    // Caminhos mais comuns, do mais provável para o menos provável.
    val PATHS = listOf(
        "/cam/realmonitor?channel=1&subtype=0", // Intelbras, Dahua, Imou
        "/cam/realmonitor?channel=1&subtype=1",
        "/Streaming/Channels/101",              // Hikvision, Ezviz, muitas Tuya
        "/Streaming/Channels/102",
        "/stream1",                             // Tapo e outras
        "/stream2",
        "/stream0",
        "/onvif1",
        "/onvif2",
        "/live/ch00_0",
        "/live/ch0",
        "/live/main",
        "/h264Preview_01_main",                 // Reolink
        "/h264/ch1/main/av_stream",
        "/ch1/main",
        "/ch0_0.h264",
        "/av0_0",
        "/11",
        "/12",
        "/1",
        "/live",
        "/video1",
        "/media/video1",
        "/MediaInput/h264",
        "/cam1/h264",
        "/videoMain",
        "/"
    )

    fun isPortOpen(host: String, port: Int, timeoutMs: Int = 500): Boolean = try {
        Socket().use {
            it.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }

    private class Reply(val code: Int, val authHeaders: List<String>)

    private fun describe(host: String, port: Int, uri: String, auth: String?): Reply? {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 2500)
                s.soTimeout = 4000
                val req = StringBuilder()
                req.append("DESCRIBE $uri RTSP/1.0\r\n")
                req.append("CSeq: 1\r\n")
                req.append("User-Agent: MinhaCamera\r\n")
                req.append("Accept: application/sdp\r\n")
                if (auth != null) req.append("Authorization: $auth\r\n")
                req.append("\r\n")
                val out = s.getOutputStream()
                out.write(req.toString().toByteArray())
                out.flush()

                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val statusLine = reader.readLine() ?: return null
                val code = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: return null
                val authHeaders = mutableListOf<String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("WWW-Authenticate:", ignoreCase = true)) {
                        authHeaders.add(line.substringAfter(":").trim())
                    }
                }
                Reply(code, authHeaders)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun probe(host: String, port: Int, user: String, pass: String, path: String): ProbeResult {
        val uri = "rtsp://$host:$port$path"
        val r1 = describe(host, port, uri, null)
            ?: return ProbeResult(ProbeStatus.ERROR, "sem resposta")
        return when (r1.code) {
            200 -> ProbeResult(ProbeStatus.OK, "OK")
            401 -> {
                val header = buildAuth(r1.authHeaders, user, pass, uri)
                    ?: return ProbeResult(ProbeStatus.ERROR, "401 (login não suportado)")
                val r2 = describe(host, port, uri, header)
                    ?: return ProbeResult(ProbeStatus.ERROR, "401, sem resposta ao login")
                when (r2.code) {
                    200 -> ProbeResult(ProbeStatus.OK, "OK")
                    401, 403 -> ProbeResult(ProbeStatus.AUTH_FAIL, "login recusado (${r2.code})")
                    404, 454 -> ProbeResult(ProbeStatus.NOT_FOUND, "não existe (${r2.code})")
                    else -> ProbeResult(ProbeStatus.ERROR, "401 → ${r2.code}")
                }
            }
            404, 454 -> ProbeResult(ProbeStatus.NOT_FOUND, "não existe (${r1.code})")
            else -> ProbeResult(ProbeStatus.ERROR, "resposta ${r1.code}")
        }
    }

    private fun buildAuth(headers: List<String>, user: String, pass: String, uri: String): String? {
        val digest = headers.firstOrNull { it.startsWith("Digest", ignoreCase = true) }
        if (digest != null) {
            val p = parseParams(digest.substring(6))
            val realm = p["realm"] ?: return null
            val nonce = p["nonce"] ?: return null
            val qop = p["qop"]
            val ha1 = md5("$user:$realm:$pass")
            val ha2 = md5("DESCRIBE:$uri")
            val sb = StringBuilder(
                "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\""
            )
            if (qop != null && qop.split(",").map { it.trim() }.contains("auth")) {
                val nc = "00000001"
                val cnonce = randomHex()
                val resp = md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
                sb.append(", response=\"$resp\", qop=auth, nc=$nc, cnonce=\"$cnonce\"")
            } else {
                sb.append(", response=\"${md5("$ha1:$nonce:$ha2")}\"")
            }
            p["opaque"]?.let { sb.append(", opaque=\"$it\"") }
            return sb.toString()
        }
        if (headers.any { it.startsWith("Basic", ignoreCase = true) }) {
            return "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
        }
        return null
    }

    private fun parseParams(s: String): Map<String, String> {
        val regex = Regex("""(\w+)\s*=\s*(?:"([^"]*)"|([^,\s]*))""")
        return regex.findAll(s).associate {
            it.groupValues[1].lowercase() to it.groupValues[2].ifEmpty { it.groupValues[3] }
        }
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun randomHex(): String {
        val b = ByteArray(8)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}
