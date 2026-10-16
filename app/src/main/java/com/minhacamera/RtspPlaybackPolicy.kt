package com.minhacamera

/** Ordem conservadora: TCP e UDP por URL, sem reativar o parser RTSP Media3. */
enum class RtspTransport { TCP, UDP }

data class RtspAttempt(val urlIndex: Int, val transport: RtspTransport)

object RtspPlaybackPolicy {
    fun next(attempt: RtspAttempt, urlCount: Int): RtspAttempt? = when {
        urlCount <= 0 || attempt.urlIndex !in 0 until urlCount -> null
        attempt.transport == RtspTransport.TCP -> attempt.copy(transport = RtspTransport.UDP)
        attempt.urlIndex + 1 < urlCount -> RtspAttempt(attempt.urlIndex + 1, RtspTransport.TCP)
        else -> null
    }
}
