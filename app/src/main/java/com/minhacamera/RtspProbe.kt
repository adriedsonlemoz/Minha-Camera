package com.minhacamera

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

enum class ProbeStatus { OK, AUTH_FAIL, NOT_FOUND, ERROR }

data class ProbeResult(
    val status: ProbeStatus, val detail: String, val debug: String = "", val videoCodec: String? = null
)

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

    private class Reply(val code: Int, val authHeaders: List<String>, val raw: String, val sdp: String)

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

    // Protocolo RTSP usa Content-Length em BYTES, não em caracteres UTF-8.
    private fun readLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() < 8192) {
            val b = input.read()
            if (b < 0) return if (bytes.size() == 0) null else bytes.toString("ISO-8859-1")
            if (b == 10) return bytes.toString("ISO-8859-1").trimEnd('\r')
            bytes.write(b)
        }
        return null
    }

    private fun readReply(input: InputStream): Reply? {
        val statusLine = readLine(input) ?: return null
        if (!statusLine.startsWith("RTSP/", ignoreCase = true)) return null
        val code = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: return null
        val authHeaders = mutableListOf<String>()
        var length = 0
        for (i in 0..100) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            if (line.startsWith("WWW-Authenticate:", ignoreCase = true)) {
                authHeaders.add(line.substringAfter(":").trim())
            }
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                length = line.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }
        // O corpo de respostas ao DESCRIBE pode conter SDP não ASCII.
        // Ler bytes exatos também evita corromper a próxima resposta na mesma conexão.
        if (length !in 0..262144) return null
        var remaining = length
        val body = ByteArrayOutputStream(minOf(length, 8192))
        val buf = ByteArray(4096)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size, remaining))
            if (n < 0) return null
            body.write(buf, 0, n)
            remaining -= n
        }
        return Reply(code, authHeaders, statusLine, body.toString("UTF-8"))
    }

    // Primeiro tenta autenticar na conexão do desafio. Alguns firmwares encerram
    // o socket após 401; nesses casos reabre e reaplica o desafio recebido.
    private fun attempt(
        host: String, port: Int, uri: String, user: String, pass: String, variant: Int
    ): Pair<Reply?, Reply?> {
        var first: Reply? = null
        var second: Reply? = null
        var header: String? = null
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2500)
                socket.soTimeout = 3000
                val out = socket.getOutputStream()
                val input = socket.getInputStream()
                send(out, uri, 1, null)
                first = readReply(input)
                val initial = first
                if (initial != null && initial.code == 401) {
                    header = buildAuth(initial.authHeaders, user, pass, uri, variant)
                    header?.let { auth ->
                        send(out, uri, 2, auth)
                        second = readReply(input)
                    }
                }
            }
        } catch (_: Exception) { /* servidor pode fechar a conexão após o 401 */ }
        // Não reutilizar nonce entre hosts; apenas repetir para o MESMO servidor.
        // Muitas câmeras legadas aceitam autenticação somente em um socket novo.
        if (first?.code == 401 && second == null && header != null) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 2500)
                    socket.soTimeout = 3000
                    val out = socket.getOutputStream()
                    send(out, uri, 3, header)
                    second = readReply(socket.getInputStream())
                }
            } catch (_: Exception) { }
        }
        return Pair(first, second)
    }

    fun probe(host: String, port: Int, user: String, pass: String, path: String): ProbeResult {
        val uri = "rtsp://$host:$port$path"
        val dbg = StringBuilder()
        var loginRefused = false
        var hadChallenge = false
        var challengeWithoutResponse = false
        var lastCode = -1
        var supportedAuth = false

        // Digest qop e legado; fallback URI-base para firmwares RTSP antigos;
        // Basic somente quando o próprio servidor oferece esse esquema.
        for (variant in 0..3) {
            val (r1, r2) = attempt(host, port, uri, user, pass, variant)
            if (r1 == null) {
                if (variant == 0) return ProbeResult(ProbeStatus.ERROR, "sem resposta ao DESCRIBE")
                continue
            }
            if (variant == 0) {
                dbg.append("autenticação anunciada: ")
                    .append(r1.authHeaders.joinToString(" + ") { it.substringBefore(' ') }.ifEmpty { "não anunciada" })
                    .append('\n')
            }
            when (r1.code) {
                200 -> return ProbeResult(ProbeStatus.OK, "DESCRIBE 200; SDP recebido", dbg.toString(), RtspCodec.videoCodec(r1.sdp))
                404, 454 -> return ProbeResult(ProbeStatus.NOT_FOUND, "caminho recusado (${r1.code})", dbg.toString())
                401 -> hadChallenge = true
                403 -> return ProbeResult(ProbeStatus.AUTH_FAIL, "acesso proibido (403)", dbg.toString())
                else -> return ProbeResult(ProbeStatus.ERROR, "resposta DESCRIBE ${r1.code}", dbg.toString())
            }
            val auth = buildAuth(r1.authHeaders, user, pass, uri, variant)
            if (auth != null) supportedAuth = true
            if (r2 == null) {
                if (auth != null) challengeWithoutResponse = true
                continue
            }
            dbg.append("tentativa ${variant+1}: DESCRIBE ").append(r2.code).append('\n')
            when (r2.code) {
                200 -> return ProbeResult(ProbeStatus.OK,
                    "DESCRIBE 200 autenticado (tentativa ${variant+1})", dbg.toString(), RtspCodec.videoCodec(r2.sdp))
                401, 403 -> loginRefused = true
                404, 454 -> return ProbeResult(ProbeStatus.NOT_FOUND,
                    "caminho recusado após autenticação (${r2.code})", dbg.toString())
                else -> lastCode = r2.code
            }
        }
        return when {
            loginRefused -> ProbeResult(ProbeStatus.AUTH_FAIL,
                "credenciais rejeitadas pelo servidor (401/403)", dbg.toString())
            hadChallenge && !supportedAuth -> ProbeResult(ProbeStatus.ERROR,
                "servidor exige autenticação incompatível com os esquemas anunciados", dbg.toString())
            challengeWithoutResponse -> ProbeResult(ProbeStatus.ERROR,
                "servidor enviou desafio, mas não respondeu ao login (mesmo após reconexão)", dbg.toString())
            hadChallenge -> ProbeResult(ProbeStatus.ERROR,
                "desafio de autenticação sem confirmação", dbg.toString())
            else -> ProbeResult(ProbeStatus.ERROR,
                if (lastCode == -1) "sem resposta" else "resposta DESCRIBE $lastCode", dbg.toString())
        }
    }

    private fun buildAuth(
        headers: List<String>, user: String, pass: String, uri: String, variant: Int
    ): String? {
        if (variant == 2) {
            if (headers.none { it.startsWith("Basic", ignoreCase = true) }) return null
            return "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
        }
        val digest = headers.firstOrNull { it.startsWith("Digest", ignoreCase = true) }
        if (digest != null) {
            val p = parseParams(digest.substring(6))
            val realm = p["realm"] ?: return null
            val nonce = p["nonce"] ?: return null
            // Não calcular MD5 quando o servidor anuncia exclusivamente SHA-256.
            if (p["algorithm"]?.uppercase() !in listOf(null, "MD5")) return null
            val qopOffered = p["qop"]?.split(",")?.map { it.trim() }?.contains("auth") == true
            val ha1 = md5("$user:$realm:$pass")
            // Alguns firmwares antigos calculam o Digest apenas com a autoridade.
            // Só experimentar isso depois de falhar com URI completo.
            val digestUri = if (variant == 3) "rtsp://" + uri.substringAfter("://").substringBefore('/') else uri
            val ha2 = md5("DESCRIBE:$digestUri")
            val sb = StringBuilder(
                "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$digestUri\""
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
