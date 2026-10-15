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
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** Valores de bateria/temperatura/corrente pertencem ao celular TRANSMISSOR. */
internal data class PhoneBatteryReading(
    val levelPercent: Int?,
    val temperatureTenthsC: Int?,
    val charging: Boolean?,
    val averageCurrentMaToday: Int?
) {
    private fun levelText() = levelPercent?.let { "$it%" } ?: "--%"
    private fun tempText() = temperatureTenthsC?.let {
        String.format(Locale.forLanguageTag("pt-BR"), "%.1f°C", it / 10.0)
    } ?: "--°C"
    private fun chargeText() = when (charging) {
        true -> "carregando"
        false -> "sem carga"
        null -> "status --"
    }
    private fun avgText() = averageCurrentMaToday?.let { if (it > 0) "+${it} mA" else "$it mA" } ?: "--"

    /** Selo compacto para gravar diretamente no vídeo transmitido. */
    fun overlayLabel(): String = "${levelText()} · ${chargeText()} · ${tempText()} · média ${avgText()}"

    /** Linha rica para o receptor. */
    fun detailsLabel(): String = "Bateria ${levelText()} · ${chargeText()} · ${tempText()} · consumo médio hoje ${avgText()}"
}

/** Poll leve: reusa ACTION_BATTERY_CHANGED e atualiza um acumulador diário simples. */
internal class PhoneBatteryTelemetry(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("phone_battery_day", Context.MODE_PRIVATE)
    private val batteryManager = appContext.getSystemService(BatteryManager::class.java)
    private var lastPoll = Long.MIN_VALUE
    private var latest = PhoneBatteryReading(null, null, null, null)

    fun read(nowMs: Long = SystemClock.elapsedRealtime()): PhoneBatteryReading {
        if (lastPoll != Long.MIN_VALUE && nowMs - lastPoll in 0L until 15_000L) return latest
        lastPoll = nowMs
        val intent = try {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: RuntimeException) { null }
        latest = fromIntent(intent, readDailyAverageMa(intent))
        return latest
    }

    private fun readDailyAverageMa(intent: Intent?): Int? {
        resetIfDayChanged()
        val sample = normalizeCurrentMa(currentSampleRaw(), chargingFromIntent(intent))
        if (sample != null) {
            val sum = prefs.getLong(KEY_SUM, 0L) + sample
            val count = prefs.getInt(KEY_COUNT, 0) + 1
            prefs.edit().putLong(KEY_SUM, sum).putInt(KEY_COUNT, count).apply()
        }
        val count = prefs.getInt(KEY_COUNT, 0)
        if (count <= 0) return null
        return (prefs.getLong(KEY_SUM, 0L) / count).toInt()
    }

    private fun currentSampleRaw(): Int? {
        val average = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }
        val now = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }
        return average ?: now
    }

    private fun normalizeCurrentMa(raw: Int?, charging: Boolean?): Int? {
        raw ?: return null
        val ma = if (abs(raw) >= 10000) raw / 1000 else raw
        val magnitude = abs(ma)
        return when (charging) {
            true -> magnitude
            false -> -magnitude
            null -> ma
        }
    }

    private fun resetIfDayChanged() {
        val today = dayKey()
        if (prefs.getString(KEY_DAY, null) != today) {
            prefs.edit().putString(KEY_DAY, today).putLong(KEY_SUM, 0L).putInt(KEY_COUNT, 0).apply()
        }
    }

    companion object {
        private const val KEY_DAY = "day"
        private const val KEY_SUM = "sum_ma"
        private const val KEY_COUNT = "count"

        private fun dayKey(): String {
            val c = Calendar.getInstance()
            return "%04d%02d%02d".format(
                Locale.US,
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH)
            )
        }

        private fun chargingFromIntent(intent: Intent?): Boolean? {
            intent ?: return null
            return when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                else -> null
            }
        }

        fun fromValues(level: Int, scale: Int, temperatureTenths: Int?, charging: Boolean?, averageCurrentMaToday: Int?): PhoneBatteryReading {
            val percent = if (level >= 0 && scale > 0 && level <= scale)
                ((level.toLong() * 100 + scale / 2) / scale).toInt().coerceIn(0, 100)
            else null
            val temp = temperatureTenths?.takeIf { it in -200..900 }
            return PhoneBatteryReading(percent, temp, charging, averageCurrentMaToday)
        }

        fun fromIntent(intent: Intent?, averageCurrentMaToday: Int?): PhoneBatteryReading {
            if (intent == null) return PhoneBatteryReading(null, null, null, averageCurrentMaToday)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val temp = if (intent.hasExtra(BatteryManager.EXTRA_TEMPERATURE))
                intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) else null
            return fromValues(level, scale, temp, chargingFromIntent(intent), averageCurrentMaToday)
        }
    }
}

/** Desenha um badge compacto no NV21 antes da compressão JPEG. */
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
        val fontSize = (width * 0.024f).coerceIn(10f, 18f)
        val horizontalPadding = (fontSize * 0.65f).toInt().coerceAtLeast(6)
        val verticalPadding = (fontSize * 0.46f).toInt().coerceAtLeast(5)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
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
                val gray = ((color ushr 16 and 0xff) * 77 + (color ushr 8 and 0xff) * 150 + (color and 0xff) * 29) shr 8
                val pos = row + bx
                val original = nv21[pos].toInt() and 0xff
                nv21[pos] = ((original * (255 - alpha) + gray * alpha + 127) / 255).toByte()
            }
        }
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
