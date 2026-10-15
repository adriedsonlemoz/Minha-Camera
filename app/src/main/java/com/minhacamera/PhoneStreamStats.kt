package com.minhacamera

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/** Contador de FPS sem alocação por quadro; amostragem no máximo a cada 2 s. */
internal class PhoneStreamStats {
    private val count = AtomicInteger()
    private var sampledAtMs = 0L

    fun reset(nowMs: Long) {
        count.set(0)
        sampledAtMs = nowMs
    }

    fun frame() { count.incrementAndGet() }

    /** Só chamar do mesmo thread responsável pela UI/amostragem. */
    fun sample(nowMs: Long): Int? {
        if (sampledAtMs == 0L) {
            sampledAtMs = nowMs
            return null
        }
        val elapsed = nowMs - sampledAtMs
        if (elapsed < 2000L) return null
        sampledAtMs = nowMs
        return (count.getAndSet(0) * 1000.0 / elapsed).roundToInt().coerceAtLeast(0)
    }
}

/** Espera progressiva e limitada para reduzir consumo em Wi-Fi indisponível. */
internal object PhoneReconnectPolicy {
    fun delayMs(failures: Int): Long = when {
        failures <= 1 -> 1000L
        failures == 2 -> 2000L
        failures == 3 -> 4000L
        else -> 8000L
    }
}

/** Estimativa entre publicação JPEG e recepção, com amostra de relógio na conexão. */
internal object PhoneDeliveryDelay {
    fun offsetMs(requestAtMs: Long, responseAtMs: Long, serverAtMs: Long?): Long? {
        if (serverAtMs == null || serverAtMs <= 0L || responseAtMs < requestAtMs ||
            responseAtMs - requestAtMs > 3000L) return null
        return requestAtMs + (responseAtMs - requestAtMs) / 2 - serverAtMs
    }

    fun estimateMs(receivedAtMs: Long, publishedAtMs: Long?, offsetMs: Long?): Long? {
        if (publishedAtMs == null || offsetMs == null || publishedAtMs <= 0L) return null
        val age = receivedAtMs - (publishedAtMs + offsetMs)
        return age.takeIf { it in 0L..5000L }
    }
}
