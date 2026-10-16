package com.minhacamera

fun main() {
    check(RtspPlaybackPolicy.next(RtspAttempt(0, RtspTransport.TCP), 2) ==
        RtspAttempt(0, RtspTransport.UDP))
    check(RtspPlaybackPolicy.next(RtspAttempt(0, RtspTransport.UDP), 2) ==
        RtspAttempt(1, RtspTransport.TCP))
    check(RtspPlaybackPolicy.next(RtspAttempt(1, RtspTransport.TCP), 2) ==
        RtspAttempt(1, RtspTransport.UDP))
    check(RtspPlaybackPolicy.next(RtspAttempt(1, RtspTransport.UDP), 2) == null)
    check(RtspPlaybackPolicy.next(RtspAttempt(0, RtspTransport.TCP), 1) ==
        RtspAttempt(0, RtspTransport.UDP))
    check(RtspPlaybackPolicy.next(RtspAttempt(0, RtspTransport.UDP), 1) == null)
    check(RtspPlaybackPolicy.next(RtspAttempt(0, RtspTransport.TCP), 0) == null)
    check(RtspPlaybackPolicy.next(RtspAttempt(5, RtspTransport.TCP), 2) == null)
    println("RTSP playback retry policy: 8 checks OK")
}
