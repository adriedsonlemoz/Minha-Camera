package com.minhacamera

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/** Transforma só a matriz do quadro, sem alocar/copiar um Bitmap a cada frame. */
class PhoneViewerImageView(context: Context, attrs: AttributeSet?) : AppCompatImageView(context, attrs) {
    companion object {
        fun normalizeDegrees(degrees: Int): Int = ((degrees % 360 + 360) % 360 / 90) * 90
    }
    private val transform = Matrix()
    private var videoDegrees = 0
    private var mirror = false
    private var fill = false
    init { scaleType = ScaleType.MATRIX }

    fun setVideoTransform(degrees: Int, mirrorImage: Boolean, fillFrame: Boolean) {
        videoDegrees = normalizeDegrees(degrees)
        // XML e a construção inicial podem definir fitCenter; este componente usa sua própria matriz.
        if (scaleType != ScaleType.MATRIX) scaleType = ScaleType.MATRIX
        mirror = mirrorImage
        fill = fillFrame
        refreshTransform()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        refreshTransform()
    }

    fun refreshTransform() {
        val drawable = drawable ?: return
        val sourceWidth = drawable.intrinsicWidth.toFloat()
        val sourceHeight = drawable.intrinsicHeight.toFloat()
        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val availableHeight = (height - paddingTop - paddingBottom).toFloat()
        if (sourceWidth <= 0f || sourceHeight <= 0f || availableWidth <= 0f || availableHeight <= 0f) return
        val rotated = videoDegrees % 180 != 0
        val rotatedWidth = if (rotated) sourceHeight else sourceWidth
        val rotatedHeight = if (rotated) sourceWidth else sourceHeight
        val scale = if (fill) maxOf(availableWidth / rotatedWidth, availableHeight / rotatedHeight)
            else minOf(availableWidth / rotatedWidth, availableHeight / rotatedHeight)
        transform.reset()
        transform.postTranslate(-sourceWidth / 2f, -sourceHeight / 2f)
        transform.postRotate(videoDegrees.toFloat())
        if (mirror) transform.postScale(-1f, 1f)
        transform.postScale(scale, scale)
        transform.postTranslate(paddingLeft + availableWidth / 2f, paddingTop + availableHeight / 2f)
        imageMatrix = transform
    }
}
