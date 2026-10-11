package com.minhacamera

import kotlin.math.min
import kotlin.math.roundToInt

/** Fit the selected half completely; only the other half is outside the viewport. */
object VideoGeometry {
    data class Frame(val width: Int, val clipHeight: Int, val videoHeight: Int, val videoTop: Float)
    fun fitHalf(viewWidth: Int, viewHeight: Int, videoAspect: Double, lower: Boolean): Frame? {
        if (viewWidth <= 0 || viewHeight <= 0 || !videoAspect.isFinite() || videoAspect <= 0) return null
        val selectedAspect = videoAspect * 2
        val selectedHeight = min(viewHeight.toDouble(), viewWidth / selectedAspect).roundToInt().coerceAtLeast(1)
        val width = (selectedHeight * selectedAspect).roundToInt().coerceIn(1, viewWidth)
        val height = selectedHeight * 2
        return Frame(width, selectedHeight, height, if (lower) -selectedHeight.toFloat() else 0f)
    }
}
