package com.minhacamera

import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

enum class ProbeStatus { OK, AUTH_FAIL, NOT_FOUND, ERROR }

data class ProbeResult(val status: ProbeStatus, val detail: String, val debug: String = "")

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
        "/live/0/MAIN",
        "/h264Preview_01_main",                 // Reolink
        "/h264/ch1/main/av_stream",
        "/ch1/main",
        "/ch01.264",
        "/ch0_0.h264",
        "/av0_0",
        "/udp/av0_0",
        "/ucast/11",
        "/0",
        "/11",
        "/12",
        "/1",
        "/live",
        "/live.sdp",
        "/stream",
        "/h264",
        "/profile1",
        "/profile2",
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

    private class Reply(val code: Int, val authHeaders: List<String>, val raw: String)

    private fun send(out: OutputStream, uri: String, cseq: Int, auth: String?) {
        val req = StringBuilder()
        req.append("DESCRIBE $uri RTSP/1.0\r\n")
        req.append("CSeq: $cseq\r\n")
        req.append("User-Agent: MinhaCamera\r\n")
        req.append("Accept: application/sdp\r\n")
        if (auth != null) req.append("Authorization: $auth\r\n")
        req.append("\r\n")
        out.write(req.toString().toByteArray())
        out.flush()
    }

    private fun readReply(reader: BufferedReader): Reply? {
        val statusLine = reader.readLine() ?: return null
        val code = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: return null
        val authHeaders = mutableListOf<String>()
        val raw = StringBuilder(statusLine)
        var length = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            raw.append('\n').append(line)
            if (line.startsWith("WWW-Authenticate:", ignoreCase = true)) {
                authHeaders.add(line.substringAfter(":").trim())
            }
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                length = line.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }
        // Consome o corpo para manter a conexão alinhada.
        if (length in 1..8192) {
            val buf = CharArray(length)
            var n = 0
            while (n < length) {
                val r = reader.read(buf, n, length - n)
                if (r < 0) break
                n += r
            }
        }
        return Reply(code, authHeaders, raw.toString())
    }

    // Uma tentativa completa em UMA conexão: DESCRIBE sem login -> desafio -> DESCRIBE com login.
    // Algumas câmeras ligam o "nonce" à conexão e recusam o login em outra conexão.
    private fun attempt(
        host: String, port: Int, uri: String, user: String, pass: String, variant: Int
    ): Pair<Reply?, Reply?> {
        var first: Reply? = null
        var second: Reply? = null
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 3000)
                s.soTimeout = 4000
                val out = s.getOutputStream()
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                send(out, uri, 1, null)
                val r1 = readReply(reader)
                first = r1
                if (r1 != null && r1.code == 401) {
                    val header = buildAuth(r1.authHeaders, user, pass, uri, variant)
                    if (header != null) {
                        send(out, uri, 2, header)
                        second = readReply(reader)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return Pair(first, second)
    }

    fun probe(host: String, port: Int, user: String, pass: String, path: String): ProbeResult {
        val uri = "rtsp://$host:$port$path"
        val dbg = StringBuilder()
        var lastCode = -1

        // Variante 0: Digest padrão · 1: Digest sem qop/com algorithm · 2: Basic
        for (variant in 0..2) {
            val (r1, r2) = attempt(host, port, uri, user, pass, variant)
            if (r1 == null) {
                if (variant == 0) return ProbeResult(ProbeStatus.ERROR, "sem resposta")
                break
            }
            if (variant == 0) {
                dbg.append("desafio: ")
                    .append(r1.authHeaders.joinToString(" | ").ifEmpty { r1.raw.replace("\n", " | ") }.take(300))
                    .append('\n')
            }
            when (r1.code) {
                200 -> return ProbeResult(ProbeStatus.OK, "OK")
                404, 454 -> return ProbeResult(ProbeStatus.NOT_FOUND, "não existe (${r1.code})", dbg.toString())
                401 -> {}
                else -> return ProbeResult(ProbeStatus.ERROR, "resposta ${r1.code}", dbg.toString())
            }
            if (r2 == null) {
                dbg.append("v$variant: sem resposta ao login\n")
                continue
            }
            dbg.append("v$variant → ").append(r2.raw.replace("\n", " | ").take(250)).append('\n')
            when (r2.code) {
                200 -> return ProbeResult(ProbeStatus.OK, "OK")
                401, 403 -> return ProbeResult(ProbeStatus.AUTH_FAIL, "login recusado (${r2.code})", dbg.toString())
                404, 454 -> return ProbeResult(ProbeStatus.NOT_FOUND, "não existe (${r2.code})", dbg.toString())
                else -> lastCode = r2.code
            }
        }
        val detail = if (lastCode == -1) "401 (login sem resposta)" else "401 → $lastCode"
        return ProbeResult(ProbeStatus.ERROR, detail, dbg.toString())
    }

    private fun buildAuth(
        headers: List<String>, user: String, pass: String, uri: String, variant: Int
    ): String? {
        if (variant == 2) {
            return "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
        }
        val digest = headers.firstOrNull { it.startsWith("Digest", ignoreCase = true) }
        if (digest != null) {
            val p = parseParams(digest.substring(6))
            val realm = p["realm"] ?: return null
            val nonce = p["nonce"] ?: return null
            val qopOffered = p["qop"]?.split(",")?.map { it.trim() }?.contains("auth") == true
            val ha1 = md5("$user:$realm:$pass")
            val ha2 = md5("DESCRIBE:$uri")
            val sb = StringBuilder(
                "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\""
            )
            if (qopOffered && variant == 0) {
                val nc = "00000001"
                val cnonce = randomHex()
                val resp = md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
                sb.append(", response=\"$resp\", qop=auth, nc=$nc, cnonce=\"$cnonce\"")
            } else {
                sb.append(", response=\"${md5("$ha1:$nonce:$ha2")}\"")
                if (variant == 1) sb.append(", algorithm=\"MD5\"")
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
