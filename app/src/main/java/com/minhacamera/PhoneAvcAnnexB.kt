package com.minhacamera

import java.io.ByteArrayOutputStream

/** Pacotes NAL H.264 de MediaCodec para o fluxo de rede Annex-B. Sem APIs Android. */
internal object PhoneAvcAnnexB {
    fun normalize(data: ByteArray): ByteArray {
        if (data.size < 4) return data
        if (data[0] == 0.toByte() && data[1] == 0.toByte() &&
            (data[2] == 1.toByte() || (data[2] == 0.toByte() && data[3] == 1.toByte()))) return data
        // Alguns encoders usam NALs precedidas de uint32 big-endian em vez de start codes.
        val out = ByteArrayOutputStream(data.size + 16)
        var offset = 0
        while (offset + 4 <= data.size) {
            val size = ((data[offset].toInt() and 255).toLong() shl 24) or
                ((data[offset + 1].toInt() and 255).toLong() shl 16) or
                ((data[offset + 2].toInt() and 255).toLong() shl 8) or
                (data[offset + 3].toInt() and 255).toLong()
            if (size < 1 || size > data.size - offset - 4) return data
            out.write(byteArrayOf(0, 0, 0, 1))
            out.write(data, offset + 4, size.toInt())
            offset += 4 + size.toInt()
        }
        return if (offset == data.size) out.toByteArray() else data
    }
}
