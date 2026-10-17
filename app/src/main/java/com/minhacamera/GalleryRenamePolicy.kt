package com.minhacamera

/** Mantém extensão e diretório; preserva o prefixo de gravações privadas usado pela biblioteca. */
object GalleryRenamePolicy {
    fun buildName(raw: String, extension: String, privateRecording: Boolean): String? {
        if (!extension.matches(Regex("[A-Za-z0-9]{1,8}"))) return null
        var stem = raw.trim().filter { it != '/' && it != '\\' && !Character.isISOControl(it) }
        if (stem.endsWith(".$extension", ignoreCase = true)) stem = stem.dropLast(extension.length + 1)
        stem = stem.trim().trim('.').trim().take(85)
        if (stem.isBlank() || stem == "." || stem == "..") return null
        if (privateRecording && stem.startsWith("Camera_")) stem = stem.removePrefix("Camera_")
        if (stem.isBlank()) return null
        val prefix = if (privateRecording) "Camera_" else ""
        return "$prefix$stem.$extension"
    }
}
