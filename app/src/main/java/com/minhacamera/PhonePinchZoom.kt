package com.minhacamera

import kotlin.math.ln
import kotlin.math.roundToInt

/** Conversão pura do gesto de pinça em zoom, independente de câmera/Android. */
internal object PhonePinchZoom {
    /** Duplicar a distância dos dedos aumenta até 80 pontos percentuais do zoom nativo. */
    fun remotePercent(startPercent: Int, scale: Float): Int {
        if (!scale.isFinite() || scale <= 0f) return startPercent.coerceIn(0, 100)
        val delta = (ln(scale.toDouble()) / ln(2.0) * 80.0).roundToInt()
        return (startPercent + delta).coerceIn(0, 100)
    }

    /** Alternativa local, limitada a 1×–3×; não altera o stream remoto. */
    fun localFactor(startFactor: Float, scale: Float): Float {
        if (!scale.isFinite() || scale <= 0f) return startFactor.coerceIn(1f, 3f)
        return (startFactor * scale).coerceIn(1f, 3f)
    }
}
