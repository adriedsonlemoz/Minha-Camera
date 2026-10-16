package com.minhacamera

import android.os.SystemClock
import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Uma mídia autorizada e identificada por ID opaco; não recebe caminhos do cliente. */
data class McrpMediaStream(val name: String, val mime: String, val length: Long, val open: () -> InputStream?)

/** Servidor MJPEG local; a Activity ou o serviço foreground controlam sua vida útil. */
class PhoneCameraServer(
    address: Inet4Address,
    token: String,
    private val changeRecording: ((Boolean) -> Boolean)? = null,
    private val recordingStatus: (() -> Boolean)? = null,
    private val changeEco: ((Boolean) -> Boolean)? = null,
    private val ecoStatus: (() -> Boolean)? = null,
    private val changeQuality: ((Int) -> Boolean)? = null,
    private val qualityStatus: (() -> Int)? = null,
    private val changeFps: ((Int) -> Boolean)? = null,
    private val fpsStatus: (() -> Int)? = null,
    private val changeZoom: ((Int) -> Boolean)? = null,
    private val zoomStatus: (() -> Int?)? = null,
    private val zoomRatioStatus: (() -> Int?)? = null,
    private val changeFacing: ((Int) -> Boolean)? = null,
    private val facingStatus: (() -> Int)? = null,
    private val takePhoto: (() -> Boolean)? = null,
    private val recordingSeconds: (() -> Long)? = null,
    private val changeCodec: ((String) -> Boolean)? = null,
    private val codecStatus: (() -> String)? = null,
    private val transmissionSeconds: (() -> Long)? = null,
    private val requestKeyframe: (() -> Unit)? = null,
    private val diagnostics: (() -> String)? = null,
    private val deviceModel: (() -> String)? = null,
    private val resolutionStatus: (() -> String)? = null,
    private val mediaIndex: (() -> String)? = null,
    private val mediaOpen: ((String) -> McrpMediaStream?)? = null
) {
    init { require(token.matches(Regex("[0-9a-f]{32}"))) }
    private val server = ServerSocket(8765, 4, address)
    val url: String = "http://${address.hostAddress}:${server.localPort}/live/$token"
    private val peers = ConcurrentHashMap.newKeySet<Socket>()
    private val peerCount = AtomicInteger()
    private val frameLock = java.lang.Object()
    private var sequence = 0L
    private var latest: ByteArray? = null
    private var latestAvc: ByteArray? = null
    private var avcSequence = 0L
    private val codecLock = java.lang.Object()
    private var latestAtMs = 0L
    private var latestBattery: PhoneBatteryReading? = null
    @Volatile private var running = true

    fun start() {
        Thread({
            while (running) {
                val peer = try { server.accept() } catch (_: SocketException) { break }
                    catch (_: Exception) { break }
                if (!running || peerCount.incrementAndGet() > 5) {
                    peerCount.decrementAndGet()
                    try { peer.close() } catch (_: Exception) { }
                    continue
                }
                peer.tcpNoDelay = true
                peer.sendBufferSize = 32 * 1024
                peers.add(peer)
                Thread({ serve(peer) }, "mjpeg-peer").apply { isDaemon = true; start() }
            }
        }, "mjpeg-accept").apply { isDaemon = true; start() }
    }

    internal fun publish(jpeg: ByteArray, battery: PhoneBatteryReading? = null) {
        if (!running || jpeg.isEmpty() || jpeg.size > 2_500_000) return
        synchronized(frameLock) {
            latest = jpeg
            latestAtMs = SystemClock.elapsedRealtime()
            latestBattery = battery
            sequence++
            frameLock.notifyAll()
        }
    }

    internal fun updateBattery(reading: PhoneBatteryReading) {
        synchronized(frameLock) { latestBattery = reading }
    }

    internal fun publishAvc(accessUnit: ByteArray) {
        if (!running || accessUnit.isEmpty() || accessUnit.size > 2_097_152) return
        synchronized(codecLock) {
            latestAvc = accessUnit
            avcSequence++
            codecLock.notifyAll()
        }
    }

    fun clearAvc() {
        synchronized(codecLock) { latestAvc = null; avcSequence++; codecLock.notifyAll() }
    }

    /** Invalida o último quadro quando a câmera muda de lente ou resolução. */
    fun clearFrame() {
        synchronized(frameLock) {
            latest = null
            latestBattery = null
            latestAtMs = 0L
            sequence++
            frameLock.notifyAll()
        }
    }

    private data class Packet(val jpeg: ByteArray, val atMs: Long, val battery: PhoneBatteryReading?)

    private fun serve(peer: Socket) {
        try {
            peer.soTimeout = 10_000
            val input = BufferedInputStream(peer.getInputStream())
            val line = readLine(input) ?: return
            var size = 0
            var requestedRange: String? = null
            while (true) {
                val header = readLine(input) ?: return
                size += header.length
                if (header.isEmpty()) break
                if (header.startsWith("Range:", ignoreCase = true)) requestedRange = header.substringAfter(':').trim()
                if (size > 8192) return
            }
            val wanted = url.substringAfter(":${server.localPort}")
            // Controle somente com POST + mesmo token aleatório do MJPEG (não há CORS).
            val control = wanted.replaceFirst("/live/", "/control/")
            val mcrpRoot = "$control/mcrp"
            if (line == "GET $mcrpRoot/info HTTP/1.1" || line == "GET $mcrpRoot/info HTTP/1.0") {
                val model = (deviceModel?.invoke() ?: "Android").replace(Regex("[^A-Za-z0-9 ._-]"), "").take(50)
                val body = ("{\"protocol\":\"mcrp\",\"version\":1,\"device\":{\"model\":\"$model\"}," +
                    "\"stream\":{\"codec\":\"${if (codecStatus?.invoke() == "h264") "h264" else "mjpeg"}\",\"mjpeg\":\"$wanted\",\"h264\":\"${wanted.replace("/live/", "/h264/")}\"}," +
                    "\"capabilities\":{\"record\":${changeRecording != null},\"photo\":${takePhoto != null},\"eco\":${changeEco != null},\"facing\":${changeFacing != null},\"quality\":${changeQuality != null},\"fps\":${changeFps != null},\"zoom\":${changeZoom != null && zoomStatus?.invoke() != null},\"codec\":${changeCodec != null},\"media\":${mediaIndex != null}}}")
                writeJson(peer, body)
                return
            }
            if (line == "GET $mcrpRoot/media HTTP/1.1" || line == "GET $mcrpRoot/media HTTP/1.0") {
                val body = try { mediaIndex?.invoke() } catch (_: Exception) { null }
                if (body == null) writeError(peer, 503, "Service Unavailable") else writeJson(peer, body)
                return
            }
            val mediaPrefix = "$mcrpRoot/media/"
            if ((line.endsWith(" HTTP/1.1") || line.endsWith(" HTTP/1.0")) && line.startsWith("GET $mediaPrefix")) {
                val id = line.substringAfter("GET $mediaPrefix").substringBefore(' ')
                val media = if (id.matches(Regex("[A-Za-z0-9._-]{1,180}")))
                    try { mediaOpen?.invoke(id) } catch (_: Exception) { null } else null
                if (media == null) writeError(peer, 404, "Not Found") else serveMedia(peer, media, requestedRange)
                return
            }
            val recordAction = when (line) {
                "POST $control/record/start HTTP/1.1" -> true
                "POST $control/record/stop HTTP/1.1" -> false
                else -> null
            }
            val ecoAction = when (line) {
                "POST $control/eco/enable HTTP/1.1" -> true
                "POST $control/eco/disable HTTP/1.1" -> false
                else -> null
            }
            val qualityAction = listOf(360, 480, 720, 768, 1080).firstOrNull {
                line == "POST $control/quality/$it HTTP/1.1"
            }
            val fpsAction = listOf(5, 10, 15, 24, 30).firstOrNull {
                line == "POST $control/fps/$it HTTP/1.1"
            }
            val cameraAction = when (line) {
                "POST $control/camera/back HTTP/1.1" -> 0
                "POST $control/camera/front HTTP/1.1" -> 1
                else -> null
            }
            // Valor absoluto, 0..100%. Sem URI livre ou números fora do intervalo.
            val zoomPercent = if (line.startsWith("POST $control/zoom/") && line.endsWith(" HTTP/1.1"))
                line.removePrefix("POST $control/zoom/").removeSuffix(" HTTP/1.1")
                    .takeIf { it.matches(Regex("[0-9]{1,3}")) }?.toIntOrNull()?.takeIf { it in 0..100 }
            else null
            val photoAction = line == "POST $control/photo HTTP/1.1"
            val codecAction = when (line) {
                "POST $control/codec/mjpeg HTTP/1.1" -> "mjpeg"
                "POST $control/codec/h264 HTTP/1.1" -> "h264"
                else -> null
            }
            if (codecAction != null) {
                val success = try { changeCodec?.invoke(codecAction) == true } catch (_: Exception) { false }
                peer.getOutputStream().write(("HTTP/1.1 ${if (success) "204 No Content" else "409 Conflict"}\r\n" +
                    "Connection: close\r\nContent-Length: 0\r\n\r\n").toByteArray(Charsets.US_ASCII))
                return
            }
            if (line == "GET $control/status HTTP/1.1" || line == "GET $control/status HTTP/1.0") {
                val status = buildString {
                    append("HTTP/1.1 200 OK\r\nCache-Control: no-store\r\n")
                    append("X-Stream-Codec: ${codecStatus?.invoke() ?: "mjpeg"}\r\n")
                    deviceModel?.invoke()?.replace(Regex("[^A-Za-z0-9 ._\\-]"), "")?.take(48)?.let {
                        append("X-Device-Model: $it\r\n")
                    }
                    resolutionStatus?.invoke()?.let { if (it.matches(Regex("[0-9]{1,5}x[0-9]{1,5}")))
                        append("X-Resolution: $it\r\n") }

                    synchronized(frameLock) {
                        latestBattery?.levelPercent?.let { append("X-Battery-Level: $it\r\n") }
                        latestBattery?.temperatureTenthsC?.let { append("X-Battery-Temp-Tenths: $it\r\n") }
                        latestBattery?.charging?.let { append("X-Battery-Charging: ${if (it) 1 else 0}\r\n") }
                    }
                    append("X-Transmission-Seconds: ${transmissionSeconds?.invoke() ?: 0}\r\n")
                    recordingSeconds?.let { append("X-Recording-Seconds: ${it()}\r\n") }
                    recordingStatus?.let { append("X-Recording: ${if (it()) 1 else 0}\r\n") }
                    ecoStatus?.let { append("X-Eco: ${if (it()) 1 else 0}\r\n") }
                    qualityStatus?.let { append("X-Quality-Profile: ${it()}\r\n") }
                    fpsStatus?.let { append("X-FPS-Target: ${it()}\r\n") }
                    append("X-Zoom-Supported: ${if (changeZoom != null && zoomStatus?.invoke() != null) 1 else 0}\r\n")
                    zoomStatus?.let { it()?.let { percent -> append("X-Zoom-Percent: $percent\r\n") } }
                    zoomRatioStatus?.let { it()?.let { ratio -> append("X-Zoom-Ratio-100: $ratio\r\n") } }
                    facingStatus?.let { append("X-Camera-Facing: ${it()}\r\n") }
                    append("Content-Length: 0\r\nConnection: close\r\n\r\n")
                }
                peer.getOutputStream().write(status.toByteArray(Charsets.US_ASCII))
                return
            }
            if (line == "GET $control/diagnostic HTTP/1.1") {
                val report = (diagnostics?.invoke() ?: "Diagnóstico indisponível").toByteArray(Charsets.UTF_8)
                peer.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                    "Cache-Control: no-store\r\nContent-Length: ${report.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                peer.getOutputStream().write(report)
                return
            }
            val h264 = wanted.replaceFirst("/live/", "/h264/")
            if (line == "GET $h264 HTTP/1.1" || line == "GET $h264 HTTP/1.0") {
                if (codecStatus?.invoke() != "h264") {
                    peer.getOutputStream().write("HTTP/1.1 409 Conflict\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    return
                }
                serveAvc(peer)
                return
            }
            if (qualityAction != null || fpsAction != null || cameraAction != null || photoAction || zoomPercent != null) {
                val success = try {
                    when {
                        qualityAction != null -> changeQuality?.invoke(qualityAction) == true
                        fpsAction != null -> changeFps?.invoke(fpsAction) == true
                        cameraAction != null -> changeFacing?.invoke(cameraAction) == true
                        zoomPercent != null -> changeZoom?.invoke(zoomPercent) == true
                        else -> takePhoto?.invoke() == true
                    }
                } catch (_: Exception) { false }
                val status = if (success) "204 No Content" else "409 Conflict"
                val reason = if (success) "" else if (recordingStatus?.invoke() == true &&
                    (qualityAction != null || cameraAction != null || photoAction)) "recording-active"
                    else if ((qualityAction != null && changeQuality == null) ||
                        (fpsAction != null && changeFps == null) ||
                        (cameraAction != null && changeFacing == null) ||
                        (zoomPercent != null && (changeZoom == null || zoomStatus?.invoke() == null)) ||
                        (photoAction && takePhoto == null)) "unsupported-mode" else "command-rejected"
                peer.getOutputStream().write(("HTTP/1.1 $status\r\nCache-Control: no-store\r\n" +
                    "X-Control-Reason: $reason\r\nConnection: close\r\nContent-Length: 0\r\n\r\n").toByteArray(Charsets.US_ASCII))
                return
            }
            if (ecoAction != null) {
                val success = try { changeEco?.invoke(ecoAction) == true } catch (_: Exception) { false }
                val status = if (success) "204 No Content" else "409 Conflict"
                val reason = if (success) "" else if (changeEco == null) "unsupported-mode" else "command-rejected"
                peer.getOutputStream().write(("HTTP/1.1 $status\r\nCache-Control: no-store\r\n" +
                    "X-Control-Reason: $reason\r\nConnection: close\r\nContent-Length: 0\r\n\r\n").toByteArray(Charsets.US_ASCII))
                return
            }
            if (recordAction != null) {
                val success = try { changeRecording?.invoke(recordAction) == true } catch (_: Exception) { false }
                val status = if (success) "204 No Content" else "409 Conflict"
                peer.getOutputStream().write(("HTTP/1.1 $status\r\nCache-Control: no-store\r\n" +
                    "Connection: close\r\nContent-Length: 0\r\n\r\n").toByteArray(Charsets.US_ASCII))
                return
            }
            if (line != "GET $wanted HTTP/1.1" && line != "GET $wanted HTTP/1.0") {
                peer.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                return
            }
            if (codecStatus?.invoke() == "h264") {
                peer.getOutputStream().write("HTTP/1.1 409 Conflict\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                return
            }
            val out = peer.getOutputStream()
            out.write(("HTTP/1.1 200 OK\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
                "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n" +
                "X-Server-Elapsed-Ms: ${SystemClock.elapsedRealtime()}\r\n" +
                "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
            var last = 0L
            while (running && !peer.isClosed) {
                val next = synchronized(frameLock) {
                    if (running && last == sequence) frameLock.wait(1500)
                    if (sequence == last) null else {
                        last = sequence
                        latest?.let { Packet(it, latestAtMs, latestBattery) }
                    }
                } ?: continue
                val batteryHeaders = buildString {
                    next.battery?.levelPercent?.let { append("X-Battery-Level: $it\r\n") }
                    next.battery?.temperatureTenthsC?.let { append("X-Battery-Temp-Tenths: $it\r\n") }
                    next.battery?.charging?.let { append("X-Battery-Charging: ${if (it) 1 else 0}\r\n") }
                    next.battery?.averageCurrentMaToday?.let { append("X-Battery-Average-Ma: $it\r\n") }
                    recordingStatus?.let { append("X-Recording: ${if (it()) 1 else 0}\r\n") }
                    ecoStatus?.let { append("X-Eco: ${if (it()) 1 else 0}\r\n") }
                    qualityStatus?.let { append("X-Quality-Profile: ${it()}\r\n") }
                    fpsStatus?.let { append("X-FPS-Target: ${it()}\r\n") }
                    facingStatus?.let { append("X-Camera-Facing: ${it()}\r\n") }
                    recordingSeconds?.let { append("X-Recording-Seconds: ${it()}\r\n") }
                    transmissionSeconds?.let { append("X-Transmission-Seconds: ${it()}\r\n") }
                    codecStatus?.let { append("X-Stream-Codec: ${it()}\r\n") }
                }
                out.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${next.jpeg.size}\r\n" +
                    "X-Frame-Elapsed-Ms: ${next.atMs}\r\n$batteryHeaders\r\n").toByteArray(Charsets.US_ASCII))
                out.write(next.jpeg)
                out.write("\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
            }
        } catch (_: Exception) {
            // Desconexão normal do visualizador; não registrar URL/token nos logs.
        } finally {
            peers.remove(peer)
            peerCount.decrementAndGet()
            try { peer.close() } catch (_: Exception) { }
        }
    }

    private fun writeError(peer: Socket, code: Int, reason: String) {
        peer.getOutputStream().write("HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
    }

    private fun writeJson(peer: Socket, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        peer.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n" +
            "X-MCRP-Version: 1\r\nCache-Control: no-store\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
        peer.getOutputStream().write(body)
    }

    private fun serveMedia(peer: Socket, media: McrpMediaStream, range: String?) {
        val size = media.length
        if (size <= 0L || size > 4_294_967_296L) { writeError(peer, 404, "Not Found"); return }
        val parts = range?.let { Regex("bytes=([0-9]+)-([0-9]*)").matchEntire(it) }
        if (range != null && parts == null) { writeError(peer, 416, "Range Not Satisfiable"); return }
        val start = parts?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val end = parts?.groupValues?.get(2)?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: (size - 1L)
        if (start !in 0 until size || end < start || end >= size) {
            writeError(peer, 416, "Range Not Satisfiable"); return
        }
        val length = end - start + 1L
        val input = try { media.open() } catch (_: Exception) { null }
        if (input == null) { writeError(peer, 404, "Not Found"); return }
        input.use { source ->
            var offset = start
            while (offset > 0) {
                val skipped = source.skip(offset)
                if (skipped <= 0) { writeError(peer, 503, "Service Unavailable"); return }
                offset -= skipped
            }
            val mime = if (media.mime.startsWith("video/") || media.mime.startsWith("image/")) media.mime else "application/octet-stream"
            val disposition = media.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
            peer.getOutputStream().write(("HTTP/1.1 ${if (parts == null) "200 OK" else "206 Partial Content"}\r\n" +
                "Content-Type: $mime\r\nContent-Disposition: attachment; filename=\"$disposition\"\r\n" +
                "Accept-Ranges: bytes\r\n" +
                (if (parts == null) "" else "Content-Range: bytes $start-$end/$size\r\n") +
                "Content-Length: $length\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
            val buf = ByteArray(32 * 1024)
            var remaining = length
            while (running && remaining > 0) {
                val count = source.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (count <= 0) break
                peer.getOutputStream().write(buf, 0, count)
                remaining -= count
            }
        }
    }

    private fun serveAvc(peer: Socket) {
        // Fluxo H.264 Annex-B contínuo; LibVLC usa demux=h264 no cliente.
        peer.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: video/h264\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
        requestKeyframe?.invoke()
        var last = 0L
        val out = peer.getOutputStream()
        while (running && !peer.isClosed && codecStatus?.invoke() == "h264") {
            val packet = synchronized(codecLock) {
                if (running && last == avcSequence) codecLock.wait(1200)
                if (last == avcSequence) null else {
                    last = avcSequence
                    latestAvc
                }
            } ?: continue
            out.write(packet)
            out.flush()
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val buf = StringBuilder()
        while (buf.length <= 2048) {
            val ch = input.read()
            if (ch == -1) return null
            if (ch == '\n'.code) return buf.toString().trimEnd('\r')
            if (ch !in 32..126 && ch != '\r'.code) return null
            buf.append(ch.toChar())
        }
        return null
    }

    fun stop() {
        running = false
        try { server.close() } catch (_: Exception) { }
        synchronized(frameLock) { latest = null; latestBattery = null; frameLock.notifyAll() }
        synchronized(codecLock) { latestAvc = null; codecLock.notifyAll() }
        peers.forEach { try { it.close() } catch (_: Exception) { } }
        peers.clear()
    }
}
