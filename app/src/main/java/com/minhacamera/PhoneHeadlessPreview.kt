package com.minhacamera

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.SystemClock
import android.os.Handler
import android.os.SystemClock
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Consumidor EGL mínimo para o preview da câmera com tela apagada.
 * Sem updateTexImage() o BufferQueue do SurfaceTexture pode encher e parar os frames NV21.
 * Não desenha pixels: apenas devolve os buffers ao produtor da câmera.
 */
internal class PhoneHeadlessPreview private constructor(
    private val thread: HandlerThread,
    private val handler: Handler,
    val surfaceTexture: SurfaceTexture,
    private val display: EGLDisplay,
    private val context: EGLContext,
    private val surface: EGLSurface,
    private val textureId: Int
) {
    @Volatile private var closed = false
    @Volatile var receivedFrames = 0L
        private set
    @Volatile var drainedFrames = 0L
        private set
    @Volatile var eglErrors = 0L
        private set
    @Volatile var lastDrainMs = 0L
        private set

    fun health(): String = "surfaceRecebidos=$receivedFrames, consumidos=$drainedFrames, " +
        "errosEGL=$eglErrors, ultimoConsumoMs=${SystemClock.elapsedRealtime()-lastDrainMs}"

    init {
        surfaceTexture.setOnFrameAvailableListener({ frame ->
            if (!closed) {
                receivedFrames++
                try {
                    frame.updateTexImage()
                    drainedFrames++
                    lastDrainMs = SystemClock.elapsedRealtime()
                } catch (error: RuntimeException) {
                    eglErrors++
                    if (eglErrors <= 2 || eglErrors % 30L == 0L)
                        PhoneStreamDiagnostics.add("SurfaceTexture updateTexImage: ${error.javaClass.simpleName}; falha #$eglErrors")
                }
            }
        }, handler)
    }

    fun close() {
        if (closed) return
        closed = true
        surfaceTexture.setOnFrameAvailableListener(null)
        val done = CountDownLatch(1)
        handler.post {
            try {
                surfaceTexture.release()
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            } finally { done.countDown() }
        }
        if (Thread.currentThread() !== thread) done.await(1500, TimeUnit.MILLISECONDS)
        thread.quitSafely()
    }

    companion object {
        fun create(width: Int, height: Int): PhoneHeadlessPreview {
            val thread = HandlerThread("phone-preview-drain").apply { start() }
            val handler = Handler(thread.looper)
            val latch = CountDownLatch(1)
            var result: PhoneHeadlessPreview? = null
            var failure: Throwable? = null
            handler.post {
                var display = EGL14.EGL_NO_DISPLAY
                var surface = EGL14.EGL_NO_SURFACE
                var context = EGL14.EGL_NO_CONTEXT
                try {
                    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                    check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, intArrayOf(0, 0), 0, intArrayOf(0, 0), 0))
                    val configAttrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 4,
                        EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
                    val configs = arrayOfNulls<EGLConfig>(1)
                    val count = intArrayOf(0)
                    check(EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, count, 0) && count[0] > 0)
                    val config = requireNotNull(configs[0])
                    context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
                    check(context != EGL14.EGL_NO_CONTEXT)
                    surface = EGL14.eglCreatePbufferSurface(display, config,
                        intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
                    check(surface != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display, surface, surface, context))
                    val ids = IntArray(1)
                    GLES20.glGenTextures(1, ids, 0)
                    check(ids[0] != 0)
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                    val texture = SurfaceTexture(ids[0]).apply { setDefaultBufferSize(width, height) }
                    result = PhoneHeadlessPreview(thread, handler, texture, display, context, surface, ids[0])
                } catch (e: Throwable) {
                    failure = e
                    if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    if (display != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(display)
                } finally { latch.countDown() }
            }
            if (!latch.await(4, TimeUnit.SECONDS) || result == null) {
                thread.quitSafely()
                throw IllegalStateException("Não foi possível consumir a superfície da câmera", failure)
            }
            return requireNotNull(result)
        }
    }
}
