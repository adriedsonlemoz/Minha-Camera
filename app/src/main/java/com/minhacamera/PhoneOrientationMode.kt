package com.minhacamera

/** Orientação independente da rotação de tela; útil para câmera fixa e retrovisor. */
object PhoneOrientationMode {
    const val PREF_KEY = "orientation_mode"
    const val AUTO = "auto"
    const val PORTRAIT = "portrait"
    const val LANDSCAPE = "landscape"
    fun normalize(mode: String?): String = if (mode in listOf(AUTO, PORTRAIT, LANDSCAPE)) mode!! else AUTO
    fun snap(angle: Int): Int = if (angle < 0) -1 else ((angle + 45) / 90 * 90) % 360
    fun deviceDegrees(mode: String?, lastSensorDegrees: Int): Int = when (normalize(mode)) {
        PORTRAIT -> 0
        LANDSCAPE -> 90
        else -> lastSensorDegrees.takeIf { it in listOf(0, 90, 180, 270) } ?: 0
    }
    fun frameDegrees(sensorMount: Int, frontFacing: Boolean, deviceDegrees: Int): Int =
        if (frontFacing) (sensorMount + deviceDegrees) % 360 else
            (sensorMount - deviceDegrees + 360) % 360
}

/** Ignora oscilações de curta duração e mantém a última orientação em inclinações. */
class PhoneOrientationStabilizer(private val settleMs: Long = 800L) {
    var accepted: Int = -1
        private set
    private var candidate = -1
    private var since = 0L
    fun observe(angle: Int, nowMs: Long): Int? {
        val snapped = PhoneOrientationMode.snap(angle)
        if (snapped < 0) return null
        if (snapped != candidate) { candidate = snapped; since = nowMs }
        if (accepted != candidate && nowMs - since >= settleMs) {
            accepted = candidate
            return candidate
        }
        return null
    }
}
