package com.minhacamera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import java.nio.ByteBuffer

/** MediaCodec AVC em ByteBuffer, sem criar uma segunda instância de Camera. Uso sequencial no worker de vídeo. */
internal class PhoneH264LiveEncoder(
    private val width: Int, private val height: Int, fps: Int, bitrate: Int,
    private val publish: (ByteArray, Boolean) -> Unit
) {
    private val codec: MediaCodec
    private val planar: Boolean
    private var firstNs = -1L
    private var lastUs = -1L
    private var parameterSets = ByteArray(0)
    private val info = MediaCodec.BufferInfo()
    private var closed = false

    init {
        require(width % 2 == 0 && height % 2 == 0)
        val candidates = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.asSequence()
            .filter { it.isEncoder && it.supportedTypes.any { type -> type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
            .filter { Build.VERSION.SDK_INT < 29 || it.isHardwareAccelerated }
            .mapNotNull { codecInfo ->
                val caps = runCatching { codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC) }.getOrNull()
                    ?: return@mapNotNull null
                val color = when {
                    // Em alguns drivers, SemiPlanar anuncia NV12 mas trata cromas de forma
                    // incompatível; priorizar I420 explícito quando disponível.
                    caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                    caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                    else -> return@mapNotNull null
                }
                if (!caps.videoCapabilities.areSizeAndRateSupported(width, height, fps.toDouble())) return@mapNotNull null
                codecInfo.name to color
            }.toList()
        var encoder: MediaCodec? = null
        var selected = 0
        for ((name, color) in candidates) {
            try {
                val candidate = MediaCodec.createByCodecName(name)
                try {
                    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, color)
                        setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                        // Bitrate padrão para preservar compatibilidade com encoders AVC dos fabricantes.
                    }
                    candidate.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    candidate.start()
                    encoder = candidate
                    selected = color
                    break
                } catch (e: Exception) { candidate.release() }
            } catch (_: Exception) { }
        }
        codec = encoder ?: throw IllegalStateException("Nenhum encoder AVC YUV420 acelerado disponível")
        planar = selected == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
    }

    fun append(nv21: ByteArray, timestampNs: Long) {
        if (closed) return
        if (firstNs < 0) firstNs = timestampNs
        val pts = maxOf(lastUs + 1, (timestampNs - firstNs) / 1000)
        val index = codec.dequeueInputBuffer(0)
        if (index >= 0) {
            val buffer = codec.getInputBuffer(index) ?: return
            val converted = PhoneYuv420.convert(nv21, width, height, planar)
            if (buffer.capacity() < converted.size) throw IllegalStateException("AVC input insuficiente")
            buffer.clear()
            buffer.put(converted)
            codec.queueInputBuffer(index, 0, converted.size, pts, 0)
            lastUs = pts
        }
        drain()
    }

    fun requestKeyframe() {
        if (!closed) runCatching { codec.setParameters(Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        }) }
    }

    private fun drain() {
        repeat(12) {
            val index = codec.dequeueOutputBuffer(info, 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    val sets = ArrayList<Byte>()
                    for (key in arrayOf("csd-0", "csd-1")) {
                        format.getByteBuffer(key)?.let { buf ->
                            val bytes = ByteArray(buf.remaining()); buf.get(bytes)
                            PhoneAvcAnnexB.normalize(bytes).forEach { sets.add(it) }
                        }
                    }
                    parameterSets = sets.toByteArray()
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)
                    val size = info.size
                    if (buffer != null && size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + size)
                        val data = ByteArray(size)
                        buffer.get(data)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            parameterSets = PhoneAvcAnnexB.normalize(data)
                        } else {
                            val key = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                            val au = PhoneAvcAnnexB.normalize(data)
                            publish(if (key && parameterSets.isNotEmpty()) parameterSets + au else au, key)
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                }
            }
        }
    }

    fun close() {
        closed = true
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

}
