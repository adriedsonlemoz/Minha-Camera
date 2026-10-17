package com.minhacamera

/** Regras determinísticas para a passagem Activity -> serviço de câmera.
 * Não permite reabrir a câmera da Activity enquanto o serviço ainda a controla.
 * Relógio: SystemClock.elapsedRealtime() no chamador, nunca hora do sistema.
 */
internal object PhoneBackgroundHandoffPolicy {
    const val START_TIMEOUT_MS = 30_000L
    const val FIRST_FRAME_TIMEOUT_MS = 45_000L

    enum class Decision { WAIT_START, WAIT_FRAMES, READY, WAIT_RELEASE, FAILED }

    fun evaluate(running: Boolean, starting: Boolean, releasing: Boolean,
                 firstFramePublished: Boolean, hasError: Boolean, elapsedMs: Long): Decision {
        if (releasing) return Decision.WAIT_RELEASE
        if (running) {
            if (firstFramePublished) return Decision.READY
            return if (elapsedMs < FIRST_FRAME_TIMEOUT_MS) Decision.WAIT_FRAMES else Decision.FAILED
        }
        if (hasError) return Decision.FAILED
        if (starting) return if (elapsedMs < START_TIMEOUT_MS) Decision.WAIT_START else Decision.FAILED
        // O START pode ainda estar na fila do Looper principal do serviço.
        return if (elapsedMs < START_TIMEOUT_MS) Decision.WAIT_START else Decision.FAILED
    }

    /** Só é seguro pedir uma prévia local depois que o serviço terminou de liberar a câmera. */
    fun canResumeLocal(starting: Boolean, running: Boolean, releasing: Boolean): Boolean =
        !starting && !running && !releasing
}
