package com.minhacamera

import android.os.SystemClock
import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Servidor MJPEG local e efêmero. Só existe enquanto a tela transmissora está ativa. */
class PhoneCameraServer(address: Inet4Address, token: String) {
    init { require(token.matches(Regex("[0-9a-f]{32}"))) }
    private val server = ServerSocket(8765, 4, address)
    val url: String = "http://${address.hostAddress}:${server.localPort}/live/$token"
    private val peers = ConcurrentHashMap.newKeySet<Socket>()
    private val peerCount = AtomicInteger()
    private val frameLock = java.lang.Object()
    private var sequence = 0L
    private var latest: ByteArray? = null
    private var latestAtMs = 0L
    private var latestBattery: PhoneBatteryReading? = null
    @Volatile private var running = true

    fun start() {
        Thread({
            while (running) {
                val peer = try { server.accept() } catch (_: SocketException) { break }
                    catch (_: Exception) { break }
                if (!running || peerCount.incrementAndGet() > 2) {
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

    fun publish(jpeg: ByteArray, battery: PhoneBatteryReading? = null) {
        if (!running || jpeg.isEmpty() || jpeg.size > 1_048_576) return
        synchronized(frameLock) {
            latest = jpeg
            latestAtMs = SystemClock.elapsedRealtime()
            latestBattery = battery
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
            while (true) {
                val header = readLine(input) ?: return
                size += header.length
                if (header.isEmpty()) break
                if (size > 8192) return
            }
            val wanted = url.substringAfter(":${server.localPort}")
            if (line != "GET $wanted HTTP/1.1" && line != "GET $wanted HTTP/1.0") {
                peer.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))
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
        peers.forEach { try { it.close() } catch (_: Exception) { } }
        peers.clear()
    }
}
