package com.minhacamera

import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.concurrent.thread

private const val USER = "camera-user"
private const val PASSWORD = "test-password-not-for-release"
private const val REALM = "camera-realm"
private const val NONCE = "abcdef987654"

private fun readRequest(s: Socket): String {
    val input = s.getInputStream()
    val bytes = java.io.ByteArrayOutputStream()
    while (bytes.size() < 10000) {
        val x = input.read()
        if (x < 0) break
        bytes.write(x)
        if (bytes.size() >= 4 && bytes.toByteArray().takeLast(4) == listOf(13.toByte(), 10.toByte(), 13.toByte(), 10.toByte())) break
    }
    return bytes.toString("ISO-8859-1")
}
private fun respond(s: Socket, code: Int, cseq: Int, headers: String = "", body: String = "") {
    val label = when (code) { 200 -> "OK"; 401 -> "Unauthorized"; 461 -> "Unsupported Transport"; else -> "Failure" }
    val content = if (body.isNotEmpty()) "Content-Type: application/sdp\r\nContent-Length: ${body.toByteArray().size}\r\n" else ""
    val reply = "RTSP/1.0 $code $label\r\nCSeq: $cseq\r\n$headers$content\r\n$body"
    s.getOutputStream().apply { write(reply.toByteArray()); flush() }
}
private fun md5(input: String) = MessageDigest.getInstance("MD5")
    .digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
private fun authOk(request: String, method: String, expectedUri: String, nc: String): Boolean {
    val header = request.lineSequence().firstOrNull { it.startsWith("Authorization: Digest ") }
        ?: return false
    val paramString = header.substringAfter("Digest ")
    val p = Regex("""(\w+)=(?:"([^"]*)"|([^,\s]*))""").findAll(paramString)
        .associate { it.groupValues[1] to it.groupValues[2].ifEmpty { it.groupValues[3] } }
    if (p["username"] != USER || p["realm"] != REALM || p["nonce"] != NONCE ||
        p["uri"] != expectedUri || p["nc"] != nc || p["qop"] != "auth") return false
    val response = md5("${md5("$USER:$REALM:$PASSWORD")}:$NONCE:$nc:${p["cnonce"]}:auth:${md5("$method:$expectedUri")}")
    return response == p["response"]
}
private fun scenario(setupCode: Int, closeAfter401: Boolean): RtspSessionResult {
    val sdp = "v=0\r\ns=Fake\r\nt=0 0\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 H265/90000\r\na=control:trackID=1\r\n"
    ServerSocket(0).use { server ->
        val serverUri = "rtsp://127.0.0.1:${server.localPort}/"
        val setupUri = "${serverUri}trackID=1"
        val worker = thread(isDaemon = true) {
            server.accept().use { first ->
                first.soTimeout = 2000
                val unauth = readRequest(first)
                check(unauth.startsWith("DESCRIBE "))
                respond(first, 401, 1, "WWW-Authenticate: Digest realm=\"$REALM\", nonce=\"$NONCE\", qop=\"auth\"\r\n")
                if (!closeAfter401) {
                    val desc = readRequest(first)
                    check(authOk(desc, "DESCRIBE", serverUri, "00000001")) { "Invalid same-socket DESCRIBE auth" }
                    respond(first, 200, 2, "Content-Base: $serverUri\r\n", sdp)
                    checkSetupAndPlay(first, serverUri, setupUri, setupCode)
                }
            }
            if (closeAfter401) server.accept().use { second ->
                second.soTimeout = 2000
                val desc = readRequest(second)
                check(authOk(desc, "DESCRIBE", serverUri, "00000001")) { "Invalid reconnect DESCRIBE auth" }
                respond(second, 200, 3, "Content-Base: $serverUri\r\n", sdp)
                checkSetupAndPlay(second, serverUri, setupUri, setupCode)
            }
        }
        val result = RtspProbe.probeSetupPlay("127.0.0.1", server.localPort, USER, PASSWORD, "/")
        worker.join(8000)
        check(!worker.isAlive) { "Server thread stuck" }
        return result
    }
}
private fun checkSetupAndPlay(s: Socket, uri: String, setupUri: String, code: Int) {
    val setup = readRequest(s)
    check(setup.startsWith("SETUP $setupUri")) { "Invalid SETUP target" }
    check(setup.contains("RTP/AVP/TCP"))
    check(authOk(setup, "SETUP", setupUri, "00000002")) { "Invalid SETUP digest" }
    respond(s, code, if (setup.contains("CSeq: 4")) 4 else 3,
        if (code == 200) "Session: random-session;timeout=60\r\n" else "")
    if (code == 200) {
        val play = readRequest(s)
        check(play.startsWith("PLAY $uri"))
        check(play.contains("Session: random-session"))
        check(authOk(play, "PLAY", uri, "00000003")) { "Invalid PLAY digest" }
        respond(s, 200, if (play.contains("CSeq: 5")) 5 else 4)
        s.getOutputStream().apply { write(byteArrayOf(36, 0, 0, 4, 1, 2, 3, 4)); flush() }
    }
}
fun main() {
    for (reconnect in listOf(false, true)) {
        val ok = scenario(200, reconnect)
        check(ok.stage == "RTP/TCP" && ok.code == 200 && ok.detail.contains("detectado")) { "$ok" }
        val unsupported = scenario(461, reconnect)
        check(unsupported.stage == "SETUP" && unsupported.code == 461) { "$unsupported" }
        check(!ok.toString().contains(NONCE) && !ok.toString().contains(PASSWORD))
    }
    println("RTSP Digest DESCRIBE/SETUP/PLAY: 4 cenários reais de socket aprovados")
}
