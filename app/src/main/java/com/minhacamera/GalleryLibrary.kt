package com.minhacamera

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Apenas arquivos que o Android autorizou ou gravações privadas deste aplicativo. */
data class GalleryItem(
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
    val dateSeconds: Long,
    val privateFile: File? = null,
    val durationMs: Long = 0L
) {
    val isVideo: Boolean get() = mime.startsWith("video/")
    val isPhoto: Boolean get() = mime.startsWith("image/")
    val key: String get() = uri.toString()
}

object GalleryLibrary {
    private const val MAX_PER_COLLECTION = 2500
    private fun media(context: Context, collection: Uri, video: Boolean): List<GalleryItem> {
        val result = ArrayList<GalleryItem>()
        val columns = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        // MediaStore retorna somente itens acessíveis à identidade/permissões atuais.
        runCatching {
            context.contentResolver.query(collection, columns, null, null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mime = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val date = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (cursor.moveToNext() && result.size < MAX_PER_COLLECTION) {
                    val idValue = cursor.getLong(id)
                    val actual = cursor.getString(mime) ?: (if (video) "video/*" else "image/*")
                    if (!(if (video) actual.startsWith("video/") else actual.startsWith("image/"))) continue
                    result.add(GalleryItem(ContentUris.withAppendedId(collection, idValue),
                        cursor.getString(name) ?: "Arquivo $idValue", actual,
                        cursor.getLong(size).coerceAtLeast(0), cursor.getLong(date).coerceAtLeast(0)))
                }
            }
        }
        return result
    }

    fun scan(context: Context): List<GalleryItem> {
        val external = if (Build.VERSION.SDK_INT >= 29) MediaStore.VOLUME_EXTERNAL else "external"
        val images = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Images.Media.getContentUri(external)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val videos = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Video.Media.getContentUri(external)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val local = ArrayList<GalleryItem>()
        local.addAll(media(context, images, false))
        local.addAll(media(context, videos, true))
        // O gravador MP4 próprio publica arquivos em Downloads/MinhaCamera via
        // MediaStore.Downloads, não na coleção de Vídeos em todos os aparelhos.
        if (Build.VERSION.SDK_INT >= 29) {
            val downloads = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            local.addAll(media(context, downloads, true))
            local.addAll(media(context, downloads, false))
        }
        runCatching {
            RecordingFiles.clips(RecordingStore.root(context)).forEach { file ->
                val ext = file.extension.lowercase()
                val mime = when (ext) {
                    "mp4", "m4v", "mov" -> "video/mp4"
                    "mkv" -> "video/x-matroska"
                    "webm" -> "video/webm"
                    "ts" -> "video/mp2t"
                    "avi" -> "video/x-msvideo"
                    else -> "video/*"
                }
                local.add(GalleryItem(FileProvider.getUriForFile(context,
                    "${context.packageName}.recordings", file), file.name, mime,
                    file.length(), file.lastModified() / 1000L, file))
            }
        }
        return local.distinctBy { it.key }.sortedByDescending { it.dateSeconds }
    }
}

/** Lista temporária para avançar entre mídias sem transportar milhares de URIs pelo Intent. */
object GallerySession {
    @Volatile var items: List<GalleryItem> = emptyList()
}
