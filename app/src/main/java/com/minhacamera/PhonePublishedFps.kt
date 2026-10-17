package com.minhacamera

/** FPS realmente publicado pelo transmissor; não confundir com meta de captura. */
internal class PhonePublishedFps {
    private var fromMs = 0L
    private var lastFrameMs = 0L
    private var frames = 0
    @Volatile private var value = 0
    @Synchronized fun mark(nowMs: Long) {
        if (fromMs == 0L) fromMs = nowMs
        lastFrameMs = nowMs
        frames++
        val elapsed = nowMs - fromMs
        if (elapsed >= 1500L) {
            value = ((frames * 1000L + elapsed / 2) / elapsed).toInt()
            fromMs = nowMs
            frames = 0
        }
    }
    @Synchronized fun measured(nowMs: Long): Int = if (lastFrameMs == 0L || nowMs - lastFrameMs > 4000) 0 else value
}
