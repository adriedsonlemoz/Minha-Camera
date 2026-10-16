import com.minhacamera.RtspServiceDetector
import com.minhacamera.RtspServiceKind
import java.net.ServerSocket
import kotlin.concurrent.thread

fun main() {
    fun checkResponse(reply: String?, expected: RtspServiceKind) {
        ServerSocket(0).use { server ->
            val task = thread {
                server.accept().use { socket ->
                    socket.soTimeout = 1500
                    val buffer = ByteArray(256)
                    socket.getInputStream().read(buffer)
                    if (reply != null) socket.getOutputStream().write(reply.toByteArray())
                }
            }
            val result = RtspServiceDetector.inspect("127.0.0.1", server.localPort, 350)
            require(result.kind == expected) { "Esperado $expected, veio ${result.kind}" }
            if (expected == RtspServiceKind.RTSP) {
                require(result.statusCode == 401) { "OPTIONS não preservou código 401" }
                require("401" in result.detail) { "Diagnóstico sem resposta RTSP" }
            }
            task.join(2000)
        }
    }
    checkResponse("RTSP/1.0 401 Unauthorized\r\n", RtspServiceKind.RTSP)
    checkResponse("HTTP/1.1 200 OK\r\n", RtspServiceKind.HTTP)
    checkResponse("unsupported-protocol\r\n", RtspServiceKind.OTHER)
    checkResponse(null, RtspServiceKind.SILENT)
    require(RtspServiceDetector.inspect("127.0.0.1", -1).kind == RtspServiceKind.CLOSED)
    println("RTSP service classification: 5 checks OK")
}
