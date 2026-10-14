package com.minhacamera

/** Rotação de quadros NV21 sem recodificar JPEG duas vezes (largura/altura pares). */
object Nv21Frames {
    fun rotated(source: ByteArray, width: Int, height: Int, degrees: Int): ByteArray {
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0)
        require(source.size >= width * height * 3 / 2)
        require(degrees in listOf(0, 90, 180, 270))
        if (degrees == 0) return source
        val destWidth = if (degrees == 90 || degrees == 270) height else width
        val result = ByteArray(width * height * 3 / 2)
        for (y in 0 until height) for (x in 0 until width) {
            val dx: Int
            val dy: Int
            when (degrees) {
                90 -> { dx = height - 1 - y; dy = x }
                180 -> { dx = width - 1 - x; dy = height - 1 - y }
                else -> { dx = y; dy = width - 1 - x }
            }
            result[dy * destWidth + dx] = source[y * width + x]
        }
        val sw = width / 2
        val sh = height / 2
        for (y in 0 until sh) for (x in 0 until sw) {
            val dx: Int
            val dy: Int
            when (degrees) {
                90 -> { dx = sh - 1 - y; dy = x }
                180 -> { dx = sw - 1 - x; dy = sh - 1 - y }
                else -> { dx = y; dy = sw - 1 - x }
            }
            val src = width * height + y * width + x * 2
            val dst = width * height + dy * destWidth + dx * 2
            result[dst] = source[src]
            result[dst + 1] = source[src + 1]
        }
        return result
    }
}
