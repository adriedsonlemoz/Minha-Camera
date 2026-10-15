package com.minhacamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Guia visual não interativo: a leitura real é feita pelo BarcodeView abaixo. */
class PhoneScanOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(95, 230, 168); strokeWidth = 6f * resources.displayMetrics.density
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(95, 230, 168); strokeWidth = 2f * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(9f, 7f), 0f)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val len = 19f * d
        val half = minOf(width, height) * 0.32f
        val left = width / 2f - half
        val right = width / 2f + half
        val top = height / 2f - half
        val bottom = height / 2f + half
        canvas.drawLine(left, top + len, left, top, accent)
        canvas.drawLine(left, top, left + len, top, accent)
        canvas.drawLine(right - len, top, right, top, accent)
        canvas.drawLine(right, top, right, top + len, accent)
        canvas.drawLine(left, bottom - len, left, bottom, accent)
        canvas.drawLine(left, bottom, left + len, bottom, accent)
        canvas.drawLine(right - len, bottom, right, bottom, accent)
        canvas.drawLine(right, bottom - len, right, bottom, accent)
        canvas.drawLine(left + 12f*d, height / 2f, right - 12f*d, height / 2f, line)
    }
}
