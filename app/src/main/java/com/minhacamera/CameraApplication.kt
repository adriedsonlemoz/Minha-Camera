package com.minhacamera

import android.app.Application
import android.content.Context
import android.os.Process
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Diagnóstico privado de crashes Java/Kotlin. Não captura SIGSEGV de bibliotecas nativas. */
class CameraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val summary = buildString {
                    append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()))
                    append(" | thread=").append(thread.name.take(64))
                    var cause: Throwable? = error
                    var depth = 0
                    while (cause != null && depth++ < 3) {
                        append("\n").append(cause.javaClass.name)
                        for (frame in cause.stackTrace.take(22)) {
                            // Sem mensagens de exceção/argumentos: URLs RTSP podem conter senhas.
                            append("\n  at ").append(frame.className).append(".")
                                .append(frame.methodName).append(":").append(frame.lineNumber)
                        }
                        cause = cause.cause
                    }
                }.take(5500)
                getSharedPreferences("private_crash_diagnostics", Context.MODE_PRIVATE)
                    .edit().putString("last", summary).commit()
            } catch (_: Throwable) { /* não interfere no handler original */ }
            if (previous != null) previous.uncaughtException(thread, error)
            else { Process.killProcess(Process.myPid()); kotlin.system.exitProcess(10) }
        }
    }
}
