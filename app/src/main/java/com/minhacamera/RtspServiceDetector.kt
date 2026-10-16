package com.minhacamera

import java.net.InetSocketAddress
import java.net.Socket

/** Porta TCP aberta não é prova de serviço RTSP. Não envia senha nem token. */
enum class RtspServiceKind { RTSP, HTTP, OTHER, SILENT, CLOSED }
data class RtspServiceResult(val kind: RtspServiceKind, val detail: String, val statusCode: Int? = null)

object RtspServiceDetector {
    fun inspect(host: String, port: Int, timeoutMs: Int = 1100): RtspServiceResult {
        if (port !in 1..65535) return RtspServiceResult(RtspServiceKind.CLOSED, "porta inválida")
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                val request = "OPTIONS rtsp://$host:$port/ RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: MinhaCamera\r\n\r\n"
                socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                val input = socket.getInputStream()
                val bytes = ArrayList<Byte>(256)
                while (bytes.size < 256) {
                    val b = try { input.read() } catch (_: java.net.SocketTimeoutException) { break }
                    if (b < 0 || b == 10) break
                    bytes.add(b.toByte())
                }
                val firstLine = String(bytes.toByteArray(), Charsets.ISO_8859_1).trim()
                when {
                    firstLine.startsWith("RTSP/", true) -> {
                        val code = firstLine.split(' ').getOrNull(1)?.toIntOrNull()
                        RtspServiceResult(RtspServiceKind.RTSP,
                            "OPTIONS: ${firstLine.take(72)}", code)
                    }
                    firstLine.startsWith("HTTP/", true) -> RtspServiceResult(RtspServiceKind.HTTP, "serviço HTTP, não RTSP")
                    firstLine.isNotEmpty() -> RtspServiceResult(RtspServiceKind.OTHER, "serviço respondeu com outro protocolo")
                    else -> RtspServiceResult(RtspServiceKind.SILENT, "porta aberta, sem resposta ao OPTIONS RTSP")
                }
            }
        } catch (_: java.net.ConnectException) {
            RtspServiceResult(RtspServiceKind.CLOSED, "conexão recusada")
        } catch (_: java.net.SocketTimeoutException) {
            RtspServiceResult(RtspServiceKind.CLOSED, "conexão expirou")
        } catch (_: Exception) {
            RtspServiceResult(RtspServiceKind.CLOSED, "não foi possível consultar a porta")
        }
    }
}
