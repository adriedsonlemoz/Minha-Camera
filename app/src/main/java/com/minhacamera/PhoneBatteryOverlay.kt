package com.minhacamera

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.SystemClock
import java.util.Locale

/** Os valores são da bateria do TRANSMISSOR; temperatura não é do CPU/ambiente. */
internal data class PhoneBatteryReading(val levelPercent: Int?, val temperatureTenthsC: Int?) {
    fun display(): String {
        val level = levelPercent?.let { "$it%" } ?: "--%"
        val temp = temperatureTenthsC?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.1f", it / 10.0) + "°C"
        } ?: "--°C"
        return "Bateria $level  |  Bat. $temp"
    }
}

/** Uma leitura do broadcast sticky a cada 15 s, nunca por quadro. */
internal class PhoneBatteryTelemetry(context: Context) {
    private val appContext = context.applicationContext
    private var lastPoll = Long.MIN_VALUE
    private var latest = PhoneBatteryReading(null, null)

    fun read(nowMs: Long = SystemClock.elapsedRealtime()): PhoneBatteryReading {
        if (lastPoll != Long.MIN_VALUE && nowMs - lastPoll in 0L until 15_000L) return latest
        lastPoll = nowMs
        val intent = try {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: RuntimeException) { null }
        latest = fromIntent(intent)
        return latest
    }

    companion object {
        fun fromValues(level: Int, scale: Int, temperatureTenths: Int?): PhoneBatteryReading {
            val percent = if (level >= 0 && scale > 0 && level <= scale)
                ((level.toLong() * 100 + scale / 2) / scale).toInt().coerceIn(0, 100)
            else null
            // Descartar leituras absurdas e ausentes; nunca apresentá-las como temperatura real.
            val temp = temperatureTenths?.takeIf { it in -200..900 }
            return PhoneBatteryReading(percent, temp)
        }

        fun fromIntent(intent: Intent?): PhoneBatteryReading {
            if (intent == null) return PhoneBatteryReading(null, null)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val temp = if (intent.hasExtra(BatteryManager.EXTRA_TEMPERATURE))
                intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) else null
            return fromValues(level, scale, temp)
        }
    }
}

/** Desenha um badge compacto somente no trecho YUV necessário, evitando JPEG decode/reencode.
 *  Aplica-se à mesma cópia NV21 que alimenta MJPEG e gravação H.264.
 *  Uso sequencial: o worker exclusivo de frames mantém o cache sem sincronização adicional.
 */
internal class PhoneBatteryOverlay {
    private var lastLabel = ""
    private var lastVideoWidth = -1
    private var pixels = IntArray(0)
    private var badgeWidth = 0
    private var badgeHeight = 0

    private fun render(label: String, width: Int) {
        if (label == lastLabel && width == lastVideoWidth && pixels.isNotEmpty()) return
        lastLabel = label
        lastVideoWidth = width
        val fontSize = (width * 0.026f).coerceIn(11f, 19f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val horizontalPadding = (fontSize * 0.65f).toInt().coerceAtLeast(6)
        val verticalPadding = (fontSize * 0.46f).toInt().coerceAtLeast(5)
        badgeWidth = (paint.measureText(label) + horizontalPadding * 2).toInt() + 2
        badgeHeight = (fontSize + verticalPadding * 2 + 4).toInt()
        badgeWidth = badgeWidth.coerceAtMost((width - 12).coerceAtLeast(1))
        val bmp = Bitmap.createBitmap(badgeWidth, badgeHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bmp)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 11, 17, 27) }
            val corner = (fontSize * 0.42f)
            canvas.drawRoundRect(RectF(0f, 0f, badgeWidth.toFloat(), badgeHeight.toFloat()), corner, corner, bg)
            canvas.drawText(label, horizontalPadding.toFloat(),
                verticalPadding + fontSize - paint.fontMetrics.descent, paint)
            pixels = IntArray(badgeWidth * badgeHeight)
            bmp.getPixels(pixels, 0, badgeWidth, 0, 0, badgeWidth, badgeHeight)
        } finally { bmp.recycle() }
    }

    fun burnInto(nv21: ByteArray, width: Int, height: Int, label: String) {
        if (width < 40 || height < 40 || width % 2 != 0 || height % 2 != 0 ||
            nv21.size < width.toLong() * height * 3 / 2) return
        render(label, width)
        val offsetX = (width / 50).coerceAtLeast(6).let { it and -2 }
        val offsetY = (width / 50).coerceAtLeast(6).let { it and -2 }
        val drawWidth = minOf(badgeWidth, width - offsetX)
        val drawHeight = minOf(badgeHeight, height - offsetY)
        if (drawWidth <= 0 || drawHeight <= 0) return
        val ySize = width * height
        for (by in 0 until drawHeight) {
            val frameY = offsetY + by
            val row = frameY * width + offsetX
            for (bx in 0 until drawWidth) {
                val color = pixels[by * badgeWidth + bx]
                val alpha = color ushr 24
                if (alpha == 0) continue
                val gray = ((color ushr 16 and 0xff) * 77 +
                    (color ushr 8 and 0xff) * 150 + (color and 0xff) * 29) shr 8
                val pos = row + bx
                val original = nv21[pos].toInt() and 0xff
                nv21[pos] = ((original * (255 - alpha) + gray * alpha + 127) / 255).toByte()
            }
        }
        // NV21: VU são amostras 2x2; reduzir crominância para cinza sob o selo.
        for (by in 0 until drawHeight step 2) for (bx in 0 until drawWidth step 2) {
            var alphaSum = 0
            var samples = 0
            for (dy in 0..1) for (dx in 0..1) {
                if (by + dy < drawHeight && bx + dx < drawWidth) {
                    alphaSum += pixels[(by + dy) * badgeWidth + bx + dx] ushr 24
                    samples++
                }
            }
            val a = alphaSum / samples
            val uv = ySize + ((offsetY + by) / 2) * width + offsetX + bx
            for (channel in 0..1) {
                val old = nv21[uv + channel].toInt() and 0xff
                nv21[uv + channel] = ((old * (255 - a) + 128 * a + 127) / 255).toByte()
            }
        }
    }
}
