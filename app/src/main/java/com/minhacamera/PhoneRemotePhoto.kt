package com.minhacamera

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** JPEG full-res recebido de Camera.takePicture(), não ampliado a partir do MJPEG. */
internal object PhoneRemotePhoto {
    fun save(context: Context, jpeg: ByteArray): Boolean {
        if (jpeg.size < 256) return false
        val name = "MinhaCamera-${SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())}.jpg"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MinhaCamera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            return try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(jpeg) } ?: error("Arquivo indisponível")
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                true
            } catch (_: Exception) {
                context.contentResolver.delete(uri, null, null)
                false
            }
        }
        // Android 7–9: pasta específica do app dispensa WRITE_EXTERNAL_STORAGE.
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MinhaCamera")
        if (!folder.exists() && !folder.mkdirs()) return false
        val file = File(folder, name)
        return try {
            file.outputStream().use { it.write(jpeg) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
            true
        } catch (_: Exception) { file.delete(); false }
    }
}
