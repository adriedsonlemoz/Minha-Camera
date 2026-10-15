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
    checkOrientationModes()
    checkAvcAnnexB()
    checkPhoneEcoPolicy()
    checkStreamStats()
    checkPhoneCamera()
    checkMcrpV1Media()
    checkRemoteRecordingApi()
    checkVideoGeometry()
    checkOnvifIdentity()
    checkRecordingFiles()
    checkSdpCodec()
    checkRtspSdpProbe()
    checkSubnets()
    checkDigestFallback()
    checkDiscoveryXml()
    checkOnvifAdvertisedEndpoint()
    println("Testes simulados de protocolo concluídos")
    // HttpServer de testes pode manter threads de keep-alive após stop(0).
    // A execução CLI termina somente após todas as verificações aprovadas.
    kotlin.system.exitProcess(0)
}


private fun checkRecordingFiles() {
    val root = java.nio.file.Files.createTempDirectory("camera-recording-test").toFile()
    val outside = java.nio.file.Files.createTempDirectory("outside-recording-test").toFile()
    try {
        val session = java.io.File(root, ".session-test").apply { mkdirs() }
        val body = byteArrayOf(1, 3, 5, 7, 9)
        // Synthetic bytes test filesystem behavior, not decoding a real video.
        java.io.File(session, "rtsp___private_user_private_password_camera.ts").writeBytes(body)
        java.io.File(session, "empty.mp4").writeBytes(byteArrayOf())
        check(RecordingFiles.clips(root).isEmpty())
        val clips = RecordingFiles.finish(root, session)
        check(clips.size == 1 && clips[0].readBytes().contentEquals(body))
        check(clips[0].extension == "ts" && !clips[0].name.contains("private"))
        check(RecordingFiles.clips(root) == clips && !session.exists())
        val foreign = java.io.File(outside, ".session-foreign").apply { mkdirs() }
        java.io.File(foreign, "outside.ts").writeBytes(body)
        check(runCatching { RecordingFiles.finish(root, foreign) }.isFailure)
        check(java.io.File(foreign, "outside.ts").exists())
        println("OK: gravações preservam bytes, removem nomes sensíveis e rejeitam pastas externas/arquivos vazios")
    } finally { root.deleteRecursively(); outside.deleteRecursively() }
}

private fun checkVideoGeometry() {
    for ((vw, vh) in listOf(690 to 1150, 1485 to 450, 320 to 240, 240 to 320)) {
        for (aspect in listOf(640.0/720, 1.0, 1920.0/2160, 0.5, 2.0)) {
            val upper = VideoGeometry.fitHalf(vw, vh, aspect, false)!!
            val lower = VideoGeometry.fitHalf(vw, vh, aspect, true)!!
            val selectedHeight = upper.clipHeight
            check(upper.width <= vw && selectedHeight <= vh)
            check(upper.videoTop == 0f && upper.videoHeight == selectedHeight * 2)
            check(lower.videoTop + selectedHeight == 0f && lower.videoTop + lower.videoHeight == selectedHeight.toFloat())
            check(upper.width == lower.width && upper.videoHeight == lower.videoHeight && upper.clipHeight == lower.clipHeight)
            check(kotlin.math.abs(upper.width.toDouble() / selectedHeight - aspect*2) < 0.03)
        }
    }
    check(VideoGeometry.fitHalf(0, 500, 1.0, false) == null)
    check(VideoGeometry.fitHalf(500, 500, Double.NaN, false) == null)
    println("OK: lente inteira sem perda de bordas em retrato/horizontal, duas metades e proporções diferentes")
}

private fun checkOnvifIdentity() {
    val info = "<s:Envelope xmlns:s=\"urn:soap\" xmlns:d=\"http://www.onvif.org/ver10/device/wsdl\"><s:Body><d:GetDeviceInformationResponse><d:Manufacturer>Fabricante de teste</d:Manufacturer><d:Model>Modelo simulado</d:Model><d:FirmwareVersion>1.0</d:FirmwareVersion></d:GetDeviceInformationResponse></s:Body></s:Envelope>"
    val services = "<Envelope><GetServicesResponse><Service><Namespace>http://www.onvif.org/ver20/ptz/wsdl</Namespace></Service><Service><Namespace>http://www.onvif.org/ver10/replay/wsdl</Namespace></Service></GetServicesResponse></Envelope>"
    val parsed = OnvifIdentity.parse(info, services, "<Envelope/>")!!
    check(parsed.manufacturer == "Fabricante de teste" && parsed.model == "Modelo simulado")
    check(parsed.ptz && parsed.replay && !parsed.recording && !parsed.search)
    check(OnvifIdentity.parse("<Envelope/>", "<Envelope/>", "<Envelope/>") == null)
    check(OnvifIdentity.parse("<!DOCTYPE foo [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><GetDeviceInformationResponse/>", "<Envelope/>", "<Envelope/>") == null)
    check(OnvifIdentity.parse("<Envelope/>", "<Envelope/>", "<GetCapabilitiesResponse><Capabilities><PTZ/></Capabilities></GetCapabilitiesResponse>")!!.ptz)
    val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
    val requests = mutableListOf<String>()
    server.createContext("/identity") { exchange ->
        val request = exchange.requestBody.bufferedReader().readText(); requests.add(request)
        val body = when {
            request.contains("GetDeviceInformation") -> info
            request.contains("GetServices") -> services
            else -> "<Envelope/>"
        }.toByteArray()
        exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
    }
    server.start()
    try {
        val result = OnvifClient.identify("127.0.0.1", "lab", "synthetic", listOf("http://127.0.0.1:${server.address.port}/identity"))
        check(result.contains("Modelo simulado") && result.contains("Movimentação (PTZ): Anunciado"))
        check(result.contains("Busca de histórico: Não anunciado"))
        check(requests.size == 4 && requests.none { it.contains("ContinuousMove") || it.contains("Set") })
    } finally { server.stop(0) }
    println("OK: identificação ONVIF HTTP simulada, serviços reais da resposta, sem mover câmera ou inferir fabricante")
}

private fun checkPhoneCamera() {
    val token = "0123456789abcdef0123456789abcdef"
    val raw = "http://192.168.43.1:9080/live/$token"
    check(PhoneCameraUrls.normalize(raw) == raw)
    check(PhoneCameraUrls.normalize("http://10.0.0.2:8088/live/$token") != null)
    check(PhoneCameraUrls.normalize("http://172.20.10.1:8088/live/$token") != null)
    for (invalid in listOf("http://8.8.8.8:8088/live/$token", "https://192.168.1.2:8088/live/$token",
        "http://192.168.1.2:8088/live/123", "http://192.168.1.2:8088/live/$token?q=1",
        "http://admin@192.168.1.2:8088/live/$token", "http://999.168.1.2:8088/live/$token",
        "https://evil.example/live/$token", "javascript:alert(1)", "http://127.0.0.1:8765/live/$token",
        "http://192.168.1.2:8765/live/$token#fragment", "http://192.168.1.2:8765/live/$token/extra")) {
        check(PhoneCameraUrls.normalize(invalid) == null) { "Aceitou endereço inválido" }
    }
    val sample = ByteArray(4 * 2 * 3 / 2) { it.toByte() }
    val rotated = Nv21Frames.rotated(sample, 4, 2, 90)
    check(Nv21Frames.rotated(rotated, 2, 4, 270).contentEquals(sample))
    check(Nv21Frames.rotated(sample, 4, 2, 180).contentEquals(
        Nv21Frames.rotated(Nv21Frames.rotated(sample, 4, 2, 90), 2, 4, 90)))
    val local = java.net.InetAddress.getByName("127.0.0.1") as java.net.Inet4Address
    val server = PhoneCameraServer(local, token)
    server.start()
    try {
        val fake = ByteArray(16) { 0x42.toByte() }
        val battery = PhoneBatteryReading(73, 326, false, -225)
        check(battery.overlayLabel().contains("73%"))
        check(battery.detailsLabel().contains("-225 mA"))
        server.publish(fake, battery)
        val url = java.net.URL(server.url)
        val good = url.openConnection() as java.net.HttpURLConnection
        good.readTimeout = 3000
        check(good.responseCode == 200)
        check(good.contentType.startsWith("multipart/x-mixed-replace"))
        check((good.getHeaderField("X-Server-Elapsed-Ms")?.toLongOrNull() ?: 0L) > 0L)
        val data = java.io.BufferedInputStream(good.inputStream)
        val header = StringBuilder()
        while (!header.contains("\r\n\r\n")) {
            val c = data.read()
            check(c >= 0 && header.length < 1000)
            header.append(c.toChar())
        }
        check(header.contains("Content-Length: ${fake.size}"))
        check(header.contains("X-Battery-Level: 73\r\n"))
        check(header.contains("X-Battery-Temp-Tenths: 326\r\n"))
        check(header.contains("X-Battery-Charging: 0\r\n"))
        check(header.contains("X-Battery-Average-Ma: -225\r\n"))
        check(Regex("X-Frame-Elapsed-Ms: [0-9]+").containsMatchIn(header))
        val read = ByteArray(fake.size)
        var count = 0
        while (count < read.size) {
            val n = data.read(read, count, read.size - count)
            check(n > 0)
            count += n
        }
        check(read.contentEquals(fake))
        good.disconnect()
        val bad = java.net.URL(server.url.replace("/live/", "/invalid/"))
            .openConnection() as java.net.HttpURLConnection
        bad.readTimeout = 3000
        check(bad.responseCode == 403)
        bad.disconnect()
    } finally { server.stop() }
    println("OK: URL local, rotação NV21 e servidor MJPEG/token com quadro binário")
}

private fun checkMcrpV1Media() {
    val token = "0123456789abcdef0123456789abcdef"
    val local = java.net.InetAddress.getByName("127.0.0.1") as java.net.Inet4Address
    val bytes = ByteArray(256) { it.toByte() }
    val server = PhoneCameraServer(local, token,
        deviceModel = { "Android teste" }, codecStatus = { "mjpeg" },
        mediaIndex = { """{"protocol":"mcrp","version":1,"items":[{"id":"v_7","name":"clipe.mp4","mime":"video/mp4","size":256,"modified":1}]}""" },
        mediaOpen = { id -> if (id == "v_7") McrpMediaStream("clipe.mp4", "video/mp4", bytes.size.toLong()) {
            java.io.ByteArrayInputStream(bytes)
        } else null }
    )
    server.start()
    val root = server.url.substringBefore("/live/")
    try {
        fun request(path: String, range: String? = null): java.net.HttpURLConnection {
            val c = java.net.URL(root + path).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 3000; c.readTimeout = 3000
            if (range != null) c.setRequestProperty("Range", range)
            return c
        }
        request("/control/$token/mcrp/info").useHttp { c ->
            check(c.responseCode == 200)
            val body = c.inputStream.bufferedReader().readText()
            check(body.contains("\"protocol\":\"mcrp\"") && body.contains("\"version\":1"))
            check(body.contains("Android teste") && body.contains("/live/$token"))
        }
        request("/control/$token/mcrp/media").useHttp { c ->
            check(c.responseCode == 200)
            check(c.inputStream.bufferedReader().readText().contains("clipe.mp4"))
        }
        request("/control/$token/mcrp/media/v_7", "bytes=5-9").useHttp { c ->
            check(c.responseCode == 206)
            check(c.getHeaderField("Content-Range") == "bytes 5-9/256")
            check(c.inputStream.readBytes().contentEquals(bytes.sliceArray(5..9)))
        }
        request("/control/$token/mcrp/media/v_7").useHttp { c ->
            check(c.responseCode == 200)
            check(c.inputStream.readBytes().contentEquals(bytes))
        }
        request("/control/$token/mcrp/media/v_8").useHttp { c -> check(c.responseCode == 404) }
        request("/control/$token/mcrp/media/v_7", "bytes=999-1000").useHttp { c -> check(c.responseCode == 416) }
        request("/control/${"0".repeat(32)}/mcrp/media").useHttp { c -> check(c.responseCode == 403) }
        request("/control/${"0".repeat(32)}/mcrp/media/v_7").useHttp { c -> check(c.responseCode == 403) }
        request("/control/$token/mcrp/media/v_7/../../status").useHttp { c -> check(c.responseCode != 200) }
    } finally { server.stop() }
    println("OK: MCRP v1, lista e mídia autenticadas, intervalos HTTP 206 e rejeição de acesso indevido")
}

private inline fun java.net.HttpURLConnection.useHttp(action: (java.net.HttpURLConnection) -> Unit) {
    try { action(this) } finally { disconnect() }
}

private fun checkStreamStats() {
    val stats = PhoneStreamStats()
    stats.reset(1000L)
    repeat(22) { stats.frame() }
    check(stats.sample(2000L) == null)
    check(stats.sample(3000L) == 11)
    check(stats.sample(3100L) == null)
    check(stats.sample(5000L) == 0)
    check((1..6).map { PhoneReconnectPolicy.delayMs(it) } ==
        listOf(1000L, 2000L, 4000L, 8000L, 8000L, 8000L))
    val offset = PhoneDeliveryDelay.offsetMs(500L, 540L, 9000L)
    check(offset == -8480L)
    check(PhoneDeliveryDelay.estimateMs(600L, 9000L, offset) == 80L)
    check(PhoneDeliveryDelay.offsetMs(0L, 9000L, 1L) == null)
    check(PhoneDeliveryDelay.estimateMs(100L, 9000L, offset) == null)
    println("OK: FPS real, reconexão progressiva e atraso estimado com relógios distintos")
}

private fun checkRemoteRecordingApi() {
    val token = "abcdef0123456789abcdef0123456789"
    val privateUrl = "http://192.168.12.2:8765/live/$token"
    check(PhoneCameraUrls.recordingCommandUrl(privateUrl, true) ==
        "http://192.168.12.2:8765/control/$token/record/start")
    check(PhoneCameraUrls.recordingCommandUrl(privateUrl, false) ==
        "http://192.168.12.2:8765/control/$token/record/stop")
    check(PhoneCameraUrls.ecoCommandUrl(privateUrl, true) ==
        "http://192.168.12.2:8765/control/$token/eco/enable")
    check(PhoneCameraUrls.ecoCommandUrl(privateUrl, false) ==
        "http://192.168.12.2:8765/control/$token/eco/disable")
    check(PhoneCameraUrls.ecoCommandUrl("http://8.8.8.8:8765/live/$token", true) == null)
    check(PhoneCameraUrls.recordingCommandUrl("http://8.8.8.8:8765/live/$token", true) == null)
    check(PhoneCameraUrls.recordingCommandUrl("http://192.168.12.2:8765/live/invalid", true) == null)
    check(PhoneCameraUrls.qualityCommandUrl(privateUrl, 720) ==
        "http://192.168.12.2:8765/control/$token/quality/720")
    check(PhoneCameraUrls.fpsCommandUrl(privateUrl, 15) ==
        "http://192.168.12.2:8765/control/$token/fps/15")
    check(PhoneCameraUrls.codecCommandUrl(privateUrl, "h264") ==
        "http://192.168.12.2:8765/control/$token/codec/h264")
    check(PhoneCameraUrls.h264Url(privateUrl) ==
        "http://192.168.12.2:8765/h264/$token")
    check(PhoneCameraUrls.statusUrl(privateUrl) ==
        "http://192.168.12.2:8765/control/$token/status")
    check(PhoneCameraUrls.codecCommandUrl(privateUrl, "hevc") == null)
    check(PhoneCameraUrls.facingCommandUrl(privateUrl, true) ==
        "http://192.168.12.2:8765/control/$token/camera/front")
    check(PhoneCameraUrls.photoCommandUrl(privateUrl) ==
        "http://192.168.12.2:8765/control/$token/photo")
    check(PhoneCameraUrls.qualityCommandUrl(privateUrl, 1080) != null)
    check(PhoneCameraUrls.fpsCommandUrl(privateUrl, 100) == null)
    check(PhoneCameraUrls.photoCommandUrl("http://8.8.8.8:8765/live/$token") == null)
    val localhost = java.net.InetAddress.getByName("127.0.0.1") as java.net.Inet4Address
    var recording = false
    var eco = false
    var quality = 480
    var fps = 10
    var facing = 0
    var photos = 0
    var codec = "mjpeg"
    val server = PhoneCameraServer(localhost, token,
        changeRecording = { wanted -> recording = wanted; true },
        recordingStatus = { recording },
        changeEco = { wanted -> eco = wanted; true },
        ecoStatus = { eco },
        changeQuality = { quality = it; true }, qualityStatus = { quality },
        changeFps = { fps = it; true }, fpsStatus = { fps },
        changeFacing = { facing = it; true }, facingStatus = { facing },
        takePhoto = { photos++; true }, recordingSeconds = { 3L },
        changeCodec = { codec = it; true }, codecStatus = { codec },
        transmissionSeconds = { 42L }, diagnostics = { "Apenas teste local" })
    server.start()
    try {
        fun command(path: String, method: String = "POST"): Int {
            val connection = java.net.URL(server.url.substringBefore("/live/") + path)
                .openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = method
                connection.readTimeout = 3000
                connection.connectTimeout = 3000
                return connection.responseCode
            } finally { connection.disconnect() }
        }
        check(command("/control/$token/record/start") == 204)
        check(recording)
        check(command("/control/$token/record/stop") == 204)
        check(!recording)
        check(command("/control/${"0".repeat(32)}/record/start") == 403)
        check(!recording)
        check(command("/control/$token/record/start", "GET") == 403)
        check(!recording)
        check(command("/control/$token/eco/enable") == 204)
        check(eco)
        check(command("/control/$token/eco/disable") == 204)
        check(!eco)
        check(command("/control/${"0".repeat(32)}/eco/enable") == 403)
        check(!eco)
        check(command("/control/$token/eco/enable", "GET") == 403)
        check(!eco)
        check(command("/control/$token/quality/720") == 204 && quality == 720)
        check(command("/control/$token/fps/15") == 204 && fps == 15)
        check(command("/control/$token/camera/front") == 204 && facing == 1)
        check(command("/control/$token/photo") == 204 && photos == 1)
        check(command("/control/${"0".repeat(32)}/photo") == 403 && photos == 1)
        check(command("/control/$token/photo", "GET") == 403 && photos == 1)
        check(command("/control/$token/quality/1080") == 204 && quality == 1080)
        check(command("/control/$token/codec/h264") == 204 && codec == "h264")
        check(command("/control/${"0".repeat(32)}/codec/mjpeg") == 403 && codec == "h264")
        check(command("/control/$token/codec/mjpeg", "GET") == 403 && codec == "h264")
        val h264 = java.net.URL(server.url.replace("/live/", "/h264/"))
            .openConnection() as java.net.HttpURLConnection
        try {
            h264.connectTimeout = 2000
            h264.readTimeout = 3000
            check(h264.responseCode == 200)
            check(h264.contentType == "video/h264")
        } finally { h264.disconnect() }
        check(command("/control/$token/codec/mjpeg") == 204 && codec == "mjpeg")
        val status = java.net.URL(server.url.replace("/live/", "/control/") + "/status")
            .openConnection() as java.net.HttpURLConnection
        try {
            check(status.responseCode == 200)
            check(status.getHeaderField("X-Stream-Codec") == "mjpeg")
            check(status.getHeaderField("X-Transmission-Seconds") == "42")
        } finally { status.disconnect() }
        check(command("/control/$token/diagnostic", "GET") == 200)
        check(command("/control/${"0".repeat(32)}/diagnostic", "GET") == 403)
    } finally { server.stop() }
    println("OK: REC, Eco, foto, qualidade, FPS e câmera remotos autenticados; token/método inválidos rejeitados")
}
