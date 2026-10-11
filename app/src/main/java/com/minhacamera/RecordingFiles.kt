package com.minhacamera

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Filesystem-only policy. Native RTSP filenames never leave the private session. */
object RecordingFiles {
    fun clips(root: File): List<File> = root.listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("Camera_") && it.length() > 0 && it.canonicalFile.parentFile == root.canonicalFile }
        .sortedByDescending { it.lastModified() }

    fun finish(root: File, session: File): List<File> {
        require(session.canonicalFile.parentFile == root.canonicalFile && session.name.startsWith(".session-"))
        val saved = mutableListOf<File>()
        session.listFiles().orEmpty().filter { it.isFile && it.canonicalFile.parentFile == session.canonicalFile }.forEach { source ->
            if (source.length() == 0L) { source.delete(); return@forEach }
            val ext = source.extension.lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "bin"
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
            val target = File(root, "Camera_${stamp}_${UUID.randomUUID().toString().take(8)}.$ext")
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = false)
                check(source.delete()) { "Não foi possível finalizar o arquivo privado" }
            }
            saved.add(target)
        }
        if (session.listFiles().isNullOrEmpty()) session.delete()
        return saved
    }
}
