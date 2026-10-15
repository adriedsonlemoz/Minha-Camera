package com.minhacamera

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/** Somente arquivos criados pelo Minha Câmera: nunca expõe a galeria ou armazenamento geral. */
internal class PhoneRemoteLibrary(private val context: Context) {
    private data class Item(
        val id: String,
        val name: String,
        val mime: String,
        val size: Long,
        val modified: Long,
        val open: () -> InputStream?
    )

    private fun eligibleFile(f: File): Boolean = f.isFile && f.name.startsWith("Camera_") &&
        f.length() > 0 && f.extension.lowercase() in setOf("mp4", "mkv", "ts", "webm")

    private fun entries(): List<Item> {
        val items = mutableListOf<Item>()
        val root = RecordingStore.root(context)
        // Prefixo e raiz privada: nenhum caminho remoto é interpretado como nome de arquivo.
        RecordingFiles.clips(root).take(100).filter(::eligibleFile).forEach { file ->
            items += Item("local_${file.name}", file.name, "video/${if (file.extension.lowercase() == "mp4") "mp4" else file.extension.lowercase()}",
                file.length(), file.lastModified()) {
                if (eligibleFile(file) && file.canonicalFile.parentFile == root.canonicalFile) file.inputStream() else null
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Foto nativa em pasta específica do app (Android 7-9).
            val photos = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "MinhaCamera")
            photos.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("MinhaCamera-") &&
                it.extension.lowercase() in setOf("jpg", "jpeg") && it.length() > 0 }.take(50).forEach { file ->
                items += Item("legacy_${file.name}", file.name, "image/jpeg", file.length(), file.lastModified()) {
                    if (file.canonicalFile.parentFile == photos.canonicalFile && file.isFile) file.inputStream() else null
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for ((kind, collection, prefix, mimePrefix) in listOf(
                listOf("v", MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/MinhaCamera/", "video/"),
                listOf("p", MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Pictures/MinhaCamera/", "image/")
            )) {
                val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
                runCatching {
                    context.contentResolver.query(collection as Uri, columns,
                        "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.IS_PENDING}=0",
                        arrayOf(prefix as String), "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                        val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                        val ownerCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
                        while (cursor.moveToNext() && items.size < 200) {
                            if (cursor.getString(ownerCol) != context.packageName) continue
                            val mime = cursor.getString(mimeCol) ?: continue
                            if (!mime.startsWith(mimePrefix as String)) continue
                            val id = cursor.getLong(idCol)
                            val name = cursor.getString(nameCol) ?: continue
                            val size = cursor.getLong(sizeCol)
                            if (size <= 0L) continue
                            val fileUri = ContentUris.withAppendedId(collection as Uri, id)
                            items += Item("${kind}_$id", name, mime, size,
                                cursor.getLong(modCol) * 1000L) {
                                context.contentResolver.openInputStream(fileUri)
                            }
                        }
                    }
                }
            }
        }
        return items.sortedByDescending { it.modified }.take(100)
    }

    fun json(): String {
        val array = JSONArray()
        for (item in entries()) {
            array.put(JSONObject().put("id", item.id).put("name", item.name.take(150))
                .put("mime", item.mime).put("size", item.size).put("modified", item.modified))
        }
        return JSONObject().put("protocol", "mcrp").put("version", 1).put("items", array).toString()
    }

    fun open(id: String): McrpMediaStream? {
        if (!id.matches(Regex("(?:local_Camera_[A-Za-z0-9._-]{1,140}|legacy_MinhaCamera-[A-Za-z0-9._-]{1,140}|[vp]_[0-9]{1,20})"))) return null
        val item = entries().firstOrNull { it.id == id } ?: return null
        return McrpMediaStream(item.name, item.mime, item.size, item.open)
    }
}
