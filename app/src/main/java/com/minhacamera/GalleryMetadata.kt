package com.minhacamera

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import java.util.LinkedHashMap
import java.util.concurrent.Executors

/** Metadados reais dos arquivos, consultados fora da UI e em cache limitado. */
data class GalleryMediaDetails(val width: Int = 0, val height: Int = 0, val durationMs: Long = 0)

object GalleryMetadata {
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LinkedHashMap<String, GalleryMediaDetails>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GalleryMediaDetails>?): Boolean = size > 256
    }

    fun formatDuration(durationMs: Long): String {
        val seconds = durationMs.coerceAtLeast(0L) / 1000L
        return if (seconds >= 3600L) "%d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
    }

    fun load(context: Context, item: GalleryItem, callback: (GalleryMediaDetails) -> Unit) {
        val key = "${item.key}:${item.dateSeconds}:${item.size}"
        val cached = synchronized(cache) { cache[key] }
        if (cached != null) { callback(cached); return }
        val appContext = context.applicationContext
        executor.execute {
            val data = runCatching { read(appContext, item) }.getOrDefault(GalleryMediaDetails())
            synchronized(cache) { cache[key] = data }
            main.post { callback(data) }
        }
    }

    private fun read(context: Context, item: GalleryItem): GalleryMediaDetails {
        if (item.isVideo) {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, item.uri)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                GalleryMediaDetails(width, height, duration)
            } finally { runCatching { retriever.release() } }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        return GalleryMediaDetails(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
    }
}
