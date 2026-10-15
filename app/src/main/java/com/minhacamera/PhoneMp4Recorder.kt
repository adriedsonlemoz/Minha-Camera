package com.minhacamera

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Conversão de NV21 para entrada YUV420 de encoders Android. Sem suposições sobre o codec. */
internal object PhoneYuv420 {
    fun convert(nv21: ByteArray, width: Int, height: Int, planar: Boolean): ByteArray {
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0)
        val size = width * height
        require(nv21.size >= size * 3 / 2)
        val out = ByteArray(size * 3 / 2)
        System.arraycopy(nv21, 0, out, 0, size)
        if (planar) {
            var u = size
            var v = size + size / 4
            for (i in size until size * 3 / 2 step 2) {
                out[u++] = nv21[i + 1]
                out[v++] = nv21[i]
            }
        } else {
            for (i in size until size * 3 / 2 step 2) {
                out[i] = nv21[i + 1]
                out[i + 1] = nv21[i]
            }
        }
        return out
    }
}

/** Grava MP4/H.264 sem áudio. Todos os métodos são invocados sequencialmente no worker de quadros. */
@Suppress("DEPRECATION")
internal class PhoneMp4Recorder(private val context: Context, val width: Int, val height: Int) {
    private val temp = File.createTempFile("phone-camera-", ".mp4", context.cacheDir)
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var track = -1
    private var muxerStarted = false
    private var frames = 0
    private var firstTimestampNs = -1L
    private var lastTimestampUs = -1L
    private var planar = false
    private var stopped = false

    init {
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0)
        try {
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = encoder
            val formats = encoder.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats
            val color = when {
                formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                else -> throw IllegalStateException("Codificador H.264 não aceita entrada YUV420 em ByteBuffer")
            }
            planar = color == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, color)
                setInteger(MediaFormat.KEY_BIT_RATE, 1_500_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 12)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            muxer = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            encoder.start()
        } catch (e: Exception) {
            release(deleteTemp = true)
            throw e
        }
    }

    fun append(nv21: ByteArray, timestampNs: Long) {
        if (stopped) return
        val encoder = codec ?: return
        if (firstTimestampNs < 0L) firstTimestampNs = timestampNs
        val presentationUs = maxOf(lastTimestampUs + 1, (timestampNs - firstTimestampNs) / 1000)
        val index = encoder.dequeueInputBuffer(2_000)
        if (index >= 0) {
            val buffer = encoder.getInputBuffer(index) ?: throw IllegalStateException("Encoder sem buffer")
            val converted = PhoneYuv420.convert(nv21, width, height, planar)
            if (buffer.capacity() < converted.size) throw IllegalStateException("Buffer H.264 insuficiente")
            buffer.clear()
            buffer.put(converted)
            encoder.queueInputBuffer(index, 0, converted.size, presentationUs, 0)
            lastTimestampUs = presentationUs
            frames++
        }
        drain(false)
    }

    /** Retorna URI de Downloads, ou null se não houve quadros válidos. */
    fun finish(): android.net.Uri? {
        if (stopped) return null
        stopped = true
        try {
            if (frames == 0) return null
            val encoder = codec ?: return null
            val eos = encoder.dequeueInputBuffer(300_000)
            if (eos >= 0) encoder.queueInputBuffer(eos, 0, 0, maxOf(0L, lastTimestampUs + 1), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
            if (!muxerStarted || temp.length() < 1024) return null
            muxer?.stop()
            muxerStarted = false
            muxer?.release()
            muxer = null
            return publishToDownloads(context, temp)
        } finally {
            release(deleteTemp = true)
        }
    }

    fun cancel() { stopped = true; release(deleteTemp = true) }

    private fun drain(waitForEos: Boolean) {
        val encoder = codec ?: return
        val info = MediaCodec.BufferInfo()
        val limitNs = System.nanoTime() + 2_000_000_000L
        while (true) {
            val output = encoder.dequeueOutputBuffer(info, if (waitForEos) 10_000 else 0)
            when {
                output == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!waitForEos || System.nanoTime() > limitNs) return
                output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) throw IllegalStateException("Formato H.264 alterado durante gravação")
                    track = (muxer ?: return).addTrack(encoder.outputFormat)
                    muxer?.start()
                    muxerStarted = true
                }
                output >= 0 -> {
                    val data = encoder.getOutputBuffer(output)
                    if (data != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && muxerStarted) {
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer?.writeSampleData(track, data, info)
                    }
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    encoder.releaseOutputBuffer(output, false)
                    if (eos) return
                }
            }
        }
    }

    private fun release(deleteTemp: Boolean) {
        try { codec?.stop() } catch (_: Exception) { }
        try { codec?.release() } catch (_: Exception) { }
        codec = null
        try { if (muxerStarted) muxer?.stop() } catch (_: Exception) { }
        try { muxer?.release() } catch (_: Exception) { }
        muxer = null
        muxerStarted = false
        if (deleteTemp) temp.delete()
    }

    companion object {
        private fun publishToDownloads(context: Context, source: File): android.net.Uri {
            val name = "MinhaCamera-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.mp4"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MinhaCamera")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Downloads indisponível")
                try {
                    resolver.openOutputStream(uri, "w")?.use { output ->
                        FileInputStream(source).use { it.copyTo(output) }
                    } ?: throw IllegalStateException("Não foi possível salvar em Downloads")
                    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    return uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            }
            // Android 7–9 exige WRITE_EXTERNAL_STORAGE concedido pelo usuário antes de gravar.
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MinhaCamera")
            if (!folder.exists() && !folder.mkdirs()) throw IllegalStateException("Downloads indisponível")
            val target = File(folder, name)
            FileInputStream(source).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("video/mp4"), null)
            return android.net.Uri.fromFile(target)
        }
    }
}
