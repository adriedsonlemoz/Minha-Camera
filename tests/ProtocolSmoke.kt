package com.minhacamera

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

private fun Socket.readRequest(): String {
    val bytes = StringBuilder()
    val input = getInputStream()
    while (bytes.length < 8192) {
        val b = input.read()
        if (b < 0) break
        bytes.append(b.toChar())
        if (bytes.endsWith("\r\n\r\n")) break
    }
    return bytes.toString()
}

private fun checkDigestFallback() {
    ServerSocket(0).use { server ->
        val connections = AtomicInteger(0)
        val thread = Thread {
            while (connections.get() < 2) {
                server.accept().use { socket ->
                    val variant = connections.incrementAndGet()
                    socket.soTimeout = 2000
                    socket.readRequest()
                    val utf8Body = "câmera".toByteArray(Charsets.UTF_8)
                    val resp = "RTSP/1.0 401 Unauthorized\r\nCSeq: 1\r\n" +
                        "WWW-Authenticate: Digest realm=\"lab\", nonce=\"123\", qop=\"auth\"\r\n" +
                        "Content-Length: ${utf8Body.size}\r\n\r\n"
                    socket.getOutputStream().write(resp.toByteArray() + utf8Body)
                    val authed = socket.readRequest()
                    check(authed.contains("Authorization: Digest")) { "Digest não foi enviado" }
                    val code = if (variant == 1) 401 else 200
                    socket.getOutputStream().write("RTSP/1.0 $code RESULT\r\nCSeq: 2\r\nContent-Length: 0\r\n\r\n".toByteArray())
                }
            }
        }
        thread.start()
        val outcome = RtspProbe.probe("127.0.0.1", server.localPort, "admin", "teste", "/stream1")
        check(outcome.status == ProbeStatus.OK) { "Fallback Digest falhou: $outcome" }
        thread.join(5000)
        check(!thread.isAlive) { "Servidor simulado ainda está ativo" }
        println("OK: RTSP UTF-8 Content-Length e fallback Digest")
    }
}

private fun checkDiscoveryXml() {
    val id = "urn:uuid:teste"
    val xml = """<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="urn:addressing" xmlns:d="urn:discovery">
      <s:Header><a:RelatesTo>$id</a:RelatesTo></s:Header>
      <s:Body><d:ProbeMatches><d:ProbeMatch><d:XAddrs>http://192.168.1.12:8899/onvif/device_service http://192.168.1.12/onvif/device_service</d:XAddrs></d:ProbeMatch></d:ProbeMatches></s:Body>
    </s:Envelope>"""
    val urls = WsDiscovery.parseProbeMatches(xml, id)
    check(urls?.size == 2) { "Endpoints ONVIF perdidos: $urls" }
    check(WsDiscovery.parseProbeMatches(xml, "urn:uuid:outro") == null)
    println("OK: ProbeMatch, XAddrs e validação de RelatesTo")
}

private fun checkSubnets() {
    class FakeContext(val prefix: Int) : android.content.Context() {
        private val cm = android.net.ConnectivityManager(prefix, "192.168.10.20")
        override fun <T : Any> getSystemService(clazz: Class<T>): T? {
            @Suppress("UNCHECKED_CAST")
            return if (clazz == android.net.ConnectivityManager::class.java) cm as T else null
        }
    }
    val slash24 = NetUtil.scanRange(FakeContext(24))!!
    check(slash24.hosts.size == 253 && !slash24.limited)
    check("192.168.10.20" !in slash24.hosts)
    val slash23 = NetUtil.scanRange(FakeContext(23))!!
    check(slash23.hosts.size == 509 && !slash23.limited)
    check("192.168.11.55" in slash23.hosts)
    val slash16 = NetUtil.scanRange(FakeContext(16))!!
    check(slash16.limited && slash16.hosts.size == 253)
    println("OK: máscaras /24, /23 e limitação segura de /16")
}

private fun checkOnvifAdvertisedEndpoint() {
    val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/real/onvif/device_service") { exchange ->
        val request = exchange.requestBody.bufferedReader().readText()
        val body = when {
            request.contains("GetSystemDateAndTime") -> "<Envelope><Body/></Envelope>"
            request.contains("GetCapabilities") ->
                "<Envelope><Media><XAddr>http://127.0.0.1:${server.address.port}/media</XAddr></Media></Envelope>"
            else -> "<Envelope/>"
        }
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    server.createContext("/media") { exchange ->
        val request = exchange.requestBody.bufferedReader().readText()
        val body = if (request.contains("GetProfiles"))
            "<Envelope><Profiles token=\"perfil1\"><VideoEncoderConfiguration><Encoding>H264</Encoding><Width>640</Width><Height>480</Height></VideoEncoderConfiguration></Profiles></Envelope>"
        else "<Envelope><Uri>rtsp://127.0.0.1:7447/stream1</Uri></Envelope>"
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    try {
        val endpoint = "http://127.0.0.1:${server.address.port}/real/onvif/device_service"
        val result = OnvifClient.findStreamUri("127.0.0.1", "admin", "teste", listOf(endpoint))
        check(result.streams.size == 1) { result.log }
        check(result.streams[0].encoding == "H264")
        check(result.streams[0].uri == "rtsp://127.0.0.1:7447/stream1")
        println("OK: ONVIF aceita XAddr real e lê perfil RTSP H.264")
    } finally { server.stop(0) }
}


private fun checkSdpCodec() {
    val hevc = """v=0
m=audio 0 RTP/AVP 0
m=video 0 RTP/AVP 96
a=rtpmap:96 H265/90000
a=fmtp:96 sprop-vps=AAAA;sprop-sps=BBBB;sprop-pps=CCCC
""".trimIndent()
    val h264 = """v=0
m=video 0 RTP/AVP 98
a=rtpmap:98 H264/90000
m=audio 0 RTP/AVP 97
a=rtpmap:97 MPEG4-GENERIC/48000
""".trimIndent()
    val audioOnly = "m=audio 0 RTP/AVP 96\na=rtpmap:96 H264/90000"
    check(RtspCodec.videoCodec(hevc) == "H265")
    check(RtspCodec.videoCodec(h264) == "H264")
    check(RtspCodec.videoCodec(audioOnly) == null)
    check(RtspCodec.videoCodec("") == null)
    println("OK: SDP identifica somente o codec de vídeo")
}

private fun checkRtspSdpProbe() {
    ServerSocket(0).use { server ->
        val thread = Thread {
            server.accept().use { socket ->
                socket.soTimeout = 3000
                socket.readRequest()
                val sdp = "v=0\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 H265/90000\r\n"
                val bytes = sdp.toByteArray(Charsets.UTF_8)
                val header = "RTSP/1.0 200 OK\r\nCSeq: 1\r\nContent-Type: application/sdp\r\nContent-Length: ${bytes.size}\r\n\r\n"
                socket.getOutputStream().write(header.toByteArray() + bytes)
            }
        }
        thread.start()
        val probe = RtspProbe.probe("127.0.0.1", server.localPort, "admin", "", "/stream1")
        check(probe.status == ProbeStatus.OK && probe.videoCodec == "H265") { "$probe" }
        thread.join(5000)
        check(!thread.isAlive)
        println("OK: RTSP DESCRIBE lê SDP H.265 corretamente")
    }
}

fun main() {
    checkSdpCodec()
    checkRtspSdpProbe()
    checkSubnets()
    checkDigestFallback()
    checkDiscoveryXml()
    checkOnvifAdvertisedEndpoint()
    println("Testes simulados de protocolo concluídos")
}
