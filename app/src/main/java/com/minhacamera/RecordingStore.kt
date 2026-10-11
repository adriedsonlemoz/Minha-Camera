package com.minhacamera

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

object RecordingStore {
    private val observers = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()
    fun observe(callback: () -> Unit) { observers.add(callback) }
    fun unobserve(callback: () -> Unit) { observers.remove(callback) }
    fun notifyChanged() { android.os.Handler(android.os.Looper.getMainLooper()).post { observers.forEach { it() } } }
    // File copy/export never blocks the player/UI thread.
    val io = Executors.newSingleThreadExecutor()
    fun root(context: Context): File {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return (if (external != null) File(external, "MinhaCamera") else File(context.filesDir, "recordings")).apply {
            check(isDirectory || mkdirs()) { "Armazenamento indisponível" }
        }
    }
    fun newSession(context: Context): File = File(root(context), ".session-${UUID.randomUUID()}").apply {
        check(mkdirs()) { "Não foi possível preparar a gravação" }
    }
}
