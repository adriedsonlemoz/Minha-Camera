package com.minhacamera

fun main() {
    check(CameraStreamProtocol.kind("rtsp://admin:senha@192.168.1.5:554/onvif1") == StreamKind.RTSP)
    check(CameraStreamProtocol.kind("http://192.168.1.5:8080/video.mjpg") == StreamKind.HTTP_VIDEO)
    check(CameraStreamProtocol.kind("https://example.test/live/camera.m3u8?token=segredo") == StreamKind.HLS)
    check(CameraStreamProtocol.kind("https://example.test/live/camera.mpd") == StreamKind.DASH)
    check(CameraStreamProtocol.kind("rtmp://192.168.1.5/live/cam") == StreamKind.RTMP)
    check(CameraStreamProtocol.kind("rtmps://192.168.1.5/live/cam") == StreamKind.RTMP)
    check(CameraStreamProtocol.kind("rtp://239.1.1.1:5004") == StreamKind.RTP)
    check(CameraStreamProtocol.kind("udp://239.1.1.1:5000") == StreamKind.RTP)
    check(CameraStreamProtocol.kind("webrtc://192.168.1.5") == null)
    check(CameraStreamProtocol.kind("p2p://192.168.1.5") == null)
    check(CameraStreamProtocol.kind("http://192.168.1.1/a b") == null)
    check(CameraStreamProtocol.kind("not-a-url") == null)
    check(CameraStreamProtocol.kind("https://example.test/live.m3u8#fragment") == null)
    check(CameraStreamProtocol.kind("rtsp://192.168.1.5:0/live") == null)
    check(CameraStreamProtocol.kind("http://evil.test:0/live") == null)
    check(CameraStreamProtocol.nextAttempt(0, RtspTransport.TCP,
        listOf("rtsp://192.168.1.5/live", "http://192.168.1.5/video")) ==
            RtspAttempt(0, RtspTransport.UDP))
    check(CameraStreamProtocol.nextAttempt(0, RtspTransport.UDP,
        listOf("rtsp://192.168.1.5/live", "http://192.168.1.5/video")) ==
            RtspAttempt(1, RtspTransport.TCP))
    check(CameraStreamProtocol.nextAttempt(0, RtspTransport.TCP,
        listOf("http://192.168.1.5/video", "rtsp://192.168.1.5/live")) ==
            RtspAttempt(1, RtspTransport.TCP))
    check(CameraStreamProtocol.nextAttempt(0, RtspTransport.TCP,
        listOf("http://192.168.1.5/video")) == null)
    println("CameraStreamProtocol: 17 casos aprovados")
}
