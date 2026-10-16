package com.minhacamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import java.util.concurrent.Executors

/** Decodificação fora da UI, cache limitado e proteção contra reciclagem de células. */
object GalleryThumbnails {
    private val executor = Executors.newFixedThreadPool(2)
    private val ui = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun load(context: Context, item: GalleryItem, target: ImageView, size: Int = 320) {
        val key = "${item.key}:${item.dateSeconds}:$size"
        target.tag = key
        cache.get(key)?.let { target.setImageBitmap(it); return }
        target.setImageDrawable(null)
        val app = context.applicationContext
        executor.execute {
            val bitmap = runCatching { decode(app, item, size) }.getOrNull()
            if (bitmap != null) {
                synchronized(cache) { cache.put(key, bitmap) }
                ui.post { if (target.tag == key) target.setImageBitmap(bitmap) }
            }
        }
    }

    fun decode(context: Context, item: GalleryItem, size: Int): Bitmap? {
        if (item.isVideo) {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, item.uri)
                if (Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size, size)
                } else retriever.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                    scaled(it, size)
                }
            } finally { runCatching { retriever.release() } }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { context.contentResolver.loadThumbnail(item.uri, Size(size, size), null) }
                .getOrNull()?.let { return it }
        }
        // ImageDecoder aplica automaticamente a orientação EXIF e reduz antes de
        // alocar a imagem completa, inclusive no visualizador de fotos.
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, item.uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width
                    val height = info.size.height
                    if (width > 0 && height > 0 && maxOf(width, height) > size) {
                        val ratio = size.toDouble() / maxOf(width, height)
                        decoder.setTargetSize((width * ratio).toInt().coerceAtLeast(1),
                            (height * ratio).toInt().coerceAtLeast(1))
                    }
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()?.let { return it }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val longest = maxOf(options.outWidth, options.outHeight)
        if (longest <= 0) return null
        val sample = (longest / size).coerceAtLeast(1)
        return context.contentResolver.openInputStream(item.uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }?.let { scaled(it, size) }
    }

    private fun scaled(original: Bitmap, size: Int): Bitmap {
        if (maxOf(original.width, original.height) <= size) return original
        val ratio = size.toFloat() / maxOf(original.width, original.height)
        val resized = Bitmap.createScaledBitmap(original,
            (original.width * ratio).toInt().coerceAtLeast(1),
            (original.height * ratio).toInt().coerceAtLeast(1), true)
        if (resized != original) original.recycle()
        return resized
    }
}
