package com.minhacamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/** Une dois quadros NV21 já orientados em imagem vertical, sem recompactação intermediária. */
internal object PhoneDualFrames {
    fun stack(top: ByteArray, bottom: ByteArray, width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0)
        val pixels = width * height
        require(top.size >= pixels * 3 / 2 && bottom.size >= pixels * 3 / 2)
        val out = ByteArray(pixels * 3)
        System.arraycopy(top, 0, out, 0, pixels)
        System.arraycopy(bottom, 0, out, pixels, pixels)
        System.arraycopy(top, pixels, out, pixels * 2, pixels / 2)
        System.arraycopy(bottom, pixels, out, pixels * 2 + pixels / 2, pixels / 2)
        return out
    }
}

/**
 * Camera2 concorrente, somente quando o Android anuncia par frontal/traseiro.
 * Cada câmera possui um ImageReader YUV; apenas os quadros mais recentes são pareados.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal class PhoneDualCamera(
    private val context: Context,
    private val frameIntervalMs: () -> Long,
    private val onPair: (ByteArray, ByteArray, Int, Int) -> Unit,
    private val onError: (String) -> Unit
) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread = HandlerThread("dual-cameras").also { it.start() }
    private val handler = Handler(thread.looper)
    private val devices = mutableMapOf<String, CameraDevice>()
    private val sessions = mutableMapOf<String, CameraCaptureSession>()
    private val readers = mutableMapOf<String, ImageReader>()
    private val closed = AtomicBoolean(false)
    private lateinit var backId: String
    private lateinit var frontId: String
    private var width = 0
    private var height = 0
    private var backRotation = 0
    private var frontRotation = 0
    private var backFrame: ByteArray? = null
    private var frontFrame: ByteArray? = null
    private var lastPublish = 0L

    companion object {
        fun supported(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            return try {
                manager.concurrentCameraIds.any { pair ->
                    pair.any { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } &&
                        pair.any { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
                }
            } catch (_: Exception) { false }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (closed.get() || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val pair = manager.concurrentCameraIds.firstOrNull { ids ->
                ids.any { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } &&
                    ids.any { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
            } ?: return false
            backId = pair.first { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
            frontId = pair.first { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
            val back = manager.getCameraCharacteristics(backId)
            val front = manager.getCameraCharacteristics(frontId)
            val backSizes = back.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
            val frontSizes = front.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
            val common = backSizes.filter { s -> frontSizes.any { it.width == s.width && it.height == s.height } && s.width % 2 == 0 && s.height % 2 == 0 }
            val size: Size = common.filter { it.width <= 640 && it.height <= 480 }
                .maxByOrNull { it.width * it.height } ?: return false
            width = size.width
            height = size.height
            val rotation = ((context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) * 90
            backRotation = ((back.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90) - rotation + 360) % 360
            frontRotation = ((front.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270) + rotation) % 360
            if ((backRotation % 180) != (frontRotation % 180)) return false
            for (id in listOf(backId, frontId)) {
                val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
                reader.setOnImageAvailableListener({ source ->
                    val image = try { source.acquireLatestImage() } catch (_: Exception) { null }
                    try { image?.use {
                        if (closed.get()) return@use
                        // Evitar copiar YUV a cada quadro quando a transmissão ainda está ocupada.
                        if (SystemClock.elapsedRealtime() - lastPublish < frameIntervalMs().coerceAtLeast(95L)) return@use
                        val bytes = imageToNv21(it)
                        if (id == backId) backFrame = bytes else frontFrame = bytes
                        val top = backFrame
                        val bottom = frontFrame
                        if (top != null && bottom != null) {
                            lastPublish = SystemClock.elapsedRealtime()
                            backFrame = null
                            frontFrame = null
                            val a = Nv21Frames.rotated(top, width, height, backRotation)
                            val b = Nv21Frames.rotated(bottom, width, height, frontRotation)
                            onPair(a, b, if (backRotation % 180 == 0) width else height,
                                if (backRotation % 180 == 0) height else width)
                        }
                    } } catch (_: Exception) { this@PhoneDualCamera.error() }
                }, handler)
                readers[id] = reader
            }
            // Ambos abertos para sessões concorrentes, apenas sobre pares aprovados pelo sistema.
            for (id in listOf(backId, frontId)) {
                manager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        if (closed.get()) { device.close(); return }
                        devices[id] = device
                        val surface = readers[id]?.surface ?: return
                        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                if (closed.get()) { session.close(); return }
                                sessions[id] = session
                                try {
                                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        addTarget(surface)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                    }
                                    session.setRepeatingRequest(request.build(), null, handler)
                                } catch (_: Exception) { this@PhoneDualCamera.error() }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) { this@PhoneDualCamera.error() }
                        }, handler)
                    }
                    override fun onDisconnected(device: CameraDevice) { device.close(); this@PhoneDualCamera.error() }
                    override fun onError(device: CameraDevice, error: Int) { device.close(); this@PhoneDualCamera.error() }
                }, handler)
            }
            true
        } catch (_: Exception) { close(); false }
    }

    private fun error() {
        if (!closed.get()) { close(); onError("Não foi possível manter as duas câmeras abertas.") }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        sessions.values.forEach { try { it.stopRepeating() } catch (_: Exception) {} }
        sessions.values.forEach { try { it.close() } catch (_: Exception) {} }
        devices.values.forEach { try { it.close() } catch (_: Exception) {} }
        readers.values.forEach { try { it.close() } catch (_: Exception) {} }
        sessions.clear(); devices.clear(); readers.clear()
        thread.quitSafely()
    }

    private fun imageToNv21(image: Image): ByteArray {
        val w = image.width
        val h = image.height
        val output = ByteArray(w * h * 3 / 2)
        val planes = image.planes
        for (planeIndex in 0..2) {
            val plane = planes[planeIndex]
            val buf = plane.buffer
            val rows = if (planeIndex == 0) h else h / 2
            val cols = if (planeIndex == 0) w else w / 2
            val start = if (planeIndex == 0) 0 else w * h
            for (y in 0 until rows) for (x in 0 until cols) {
                val pos = y * plane.rowStride + x * plane.pixelStride
                if (pos >= buf.limit()) continue
                val offset = if (planeIndex == 0) y * w + x
                    else start + (y * cols + x) * 2 + if (planeIndex == 1) 1 else 0
                output[offset] = buf.get(pos)
            }
        }
        return output
    }
}
