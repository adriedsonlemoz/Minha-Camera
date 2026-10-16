package com.minhacamera

import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

private fun readRequest(s: Socket): String {
    val input = s.getInputStream()
    val b = java.io.ByteArrayOutputStream()
    while (b.size() < 5000) {
        val x = input.read()
        if (x < 0) break
        b.write(x)
        if (b.toString("ISO-8859-1").endsWith("\r\n\r\n")) break
    }
    return b.toString("ISO-8859-1")
}
private fun reply(s: Socket, code: Int, headers: String = "", body: String = "") {
    val label = when (code) { 200 -> "OK"; 401 -> "Unauthorized"; 461 -> "Unsupported Transport"; else -> "Failure" }
    s.getOutputStream().apply {
        write("RTSP/1.0 $code $label\r\nCSeq: 1\r\n$headers".toByteArray())
        if (body.isNotEmpty()) write("Content-Type: application/sdp\r\nContent-Length: ${body.toByteArray().size}\r\n".toByteArray())
        write("\r\n".toByteArray())
        if (body.isNotEmpty()) write(body.toByteArray())
        flush()
    }
}
private fun runScenario(setupCode: Int): RtspSessionResult {
    val sdp = "v=0\r\ns=Fake\r\nt=0 0\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 H265/90000\r\na=control:trackID=1\r\n"
    ServerSocket(0).use { server ->
        val worker = thread(isDaemon = true) {
            server.accept().use { first ->
                first.soTimeout = 2000
                val req = readRequest(first)
                check(req.startsWith("DESCRIBE "))
                reply(first, 401, "WWW-Authenticate: Digest realm=\"cam\", nonce=\"123\"\r\n")
            }
            server.accept().use { second ->
                second.soTimeout = 2000
                val desc = readRequest(second)
                check(desc.startsWith("DESCRIBE "))
                check(desc.contains("Authorization: Digest "))
                reply(second, 200, "Content-Base: rtsp://127.0.0.1:${server.localPort}/\r\n", sdp)
                val setup = readRequest(second)
                check(setup.startsWith("SETUP rtsp://127.0.0.1:${server.localPort}/trackID=1"))
                check(setup.contains("RTP/AVP/TCP"))
                reply(second, setupCode, if (setupCode == 200) "Session: secret-session-id;timeout=60\r\n" else "")
                if (setupCode == 200) {
                    val play = readRequest(second)
                    check(play.startsWith("PLAY rtsp://127.0.0.1:${server.localPort}/"))
                    check(play.contains("Session: secret-session-id"))
                    reply(second, 200)
                    second.getOutputStream().write(byteArrayOf(36, 0, 0, 4, 1, 2, 3, 4))
                    second.getOutputStream().flush()
                }
            }
        }
        val result = RtspProbe.probeSetupPlay("127.0.0.1", server.localPort, "admin", "test-no-secret", "/")
        worker.join(4000)
        check(!worker.isAlive)
        return result
    }
}
fun main() {
    val accepted = runScenario(200)
    check(accepted.stage == "RTP/TCP" && accepted.code == 200 && accepted.detail.contains("recebidos")) { "$accepted" }
    val denied = runScenario(461)
    check(denied.stage == "SETUP" && denied.code == 461 && denied.detail.contains("UDP")) { "$denied" }
    check(!accepted.toString().contains("secret"))
    println("RTSP SETUP/PLAY e RTP/TCP: 2 cenários de sessão aprovados")
}
