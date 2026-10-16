package com.minhacamera

import android.net.Uri
import java.net.InetAddress

/** Recuperação conservadora: só IP privado e só aplica após confirmação do usuário. */
object RtspRecovery {
    data class Match(val url: String, val port: Int, val path: String, val codec: String?)
    data class Outcome(val match: Match?, val report: String)

    fun find(savedUrl: String, progress: (String) -> Unit): Outcome {
        val u = try { Uri.parse(savedUrl) } catch (_: Exception) { return Outcome(null, "Endereço inválido") }
        val host = u.host ?: return Outcome(null, "Câmera sem IP/host válido")
        val privateHost = try { InetAddress.getByName(host).isSiteLocalAddress } catch (_: Exception) { false }
        if (!privateHost) return Outcome(null, "Recuperação automática limitada a câmeras de IP privado na rede local")
        val encodedAuth = u.encodedUserInfo.orEmpty()
        val user = Uri.decode(encodedAuth.substringBefore(':', ""))
        val pass = Uri.decode(encodedAuth.substringAfter(':', ""))
        val storedPath = (u.encodedPath ?: "/") + (u.encodedQuery?.let { "?$it" } ?: "")
        val ports = (listOf(u.port.takeIf { it in 1..65535 } ?: 554, 554, 8554, 10554)).distinct()
        val paths = (listOf(storedPath, "/onvif1", "/onvif2", "/stream1",
            "/Streaming/Channels/101", "/live.sdp", "/11")).distinct()
        val report = StringBuilder("Recuperação RTSP na rede local\n")
        for (port in ports) {
            if (Thread.currentThread().isInterrupted) break
            progress("Verificando serviço na porta $port")
            val service = RtspServiceDetector.inspect(host, port)
            report.append("Porta $port: ${service.detail}\n")
            if (service.kind in listOf(RtspServiceKind.CLOSED, RtspServiceKind.HTTP, RtspServiceKind.OTHER)) continue
            var noResponse = 0
            for (path in paths) {
                if (Thread.currentThread().isInterrupted) break
                progress("Testando porta $port e caminho ${path.substringBefore('?')}")
                val probe = RtspProbe.probe(host, port, user, pass, path)
                report.append("$port${path.substringBefore('?')}: ${probe.detail}\n")
                if (probe.status == ProbeStatus.OK) {
                    val resultUrl = CameraFormUi.buildRtspUrl(host, port, user, pass, path)
                    return Outcome(Match(resultUrl, port, path, probe.videoCodec), report.toString())
                }
                if (probe.status == ProbeStatus.AUTH_FAIL) {
                    return Outcome(null, report.append("Autenticação recusada: confira a senha NVR/RTSP.\n").toString())
                }
                if (probe.status == ProbeStatus.ERROR && probe.detail.startsWith("sem resposta")) {
                    noResponse++
                    if (noResponse >= 2) break
                }
            }
        }
        return Outcome(null, report.append("Nenhum caminho confirmou uma resposta RTSP válida.\nConfira se a câmera ativou NVR/RTSP e a senha específica da câmera.").toString())
    }
}
