package com.minhacamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.os.Bundle
import android.content.res.Configuration
import android.content.Intent
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION")
class PhoneCameraActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private lateinit var preview: SurfaceView
    private lateinit var status: TextView
    private lateinit var address: TextView
    private var surfaceReady = false
    private var camera: Camera? = null
    @Volatile private var server: PhoneCameraServer? = null
    private var selectedFacing = Camera.CameraInfo.CAMERA_FACING_BACK
    private var selectedId: Int? = null
    @Volatile private var frameRotation = 0
    private var worker = Executors.newSingleThreadExecutor()
    private val encoding = AtomicBoolean(false)
    private var lastCapture = 0L
    private var visible = false
    private var permissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_camera)
        title = getString(R.string.phone_broadcast)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        preview = findViewById(R.id.phonePreview)
        status = findViewById(R.id.phoneStatus)
        address = findViewById(R.id.phoneAddress)
        preview.holder.addCallback(this)
        findViewById<Button>(R.id.phoneSwitch).setOnClickListener {
            selectedFacing = if (selectedFacing == Camera.CameraInfo.CAMERA_FACING_BACK)
                Camera.CameraInfo.CAMERA_FACING_FRONT else Camera.CameraInfo.CAMERA_FACING_BACK
            stopStreaming()
            tryStart()
        }
        findViewById<Button>(R.id.phoneCopy).setOnClickListener {
            val live = server?.url
            if (live == null) Toast.makeText(this, R.string.phone_not_ready, Toast.LENGTH_SHORT).show()
            else {
                CameraFormUi.copyText(this, live, getString(R.string.phone_stream_url), true)
                Toast.makeText(this, R.string.phone_copied, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.phoneRevoke).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.phone_revoke)
                .setMessage(R.string.phone_revoke_confirm)
                .setPositiveButton(R.string.phone_revoke) { _, _ ->
                    stopStreaming()
                    try {
                        PhoneCameraAccess.revoke(this)
                        tryStart()
                    } catch (_: Exception) { status.setText(R.string.phone_start_failed) }
                }
                .setNegativeButton(R.string.cancelar, null).show()
        }
        findViewById<Button>(R.id.phoneShare).setOnClickListener {
            val live = server?.url
            if (live == null) Toast.makeText(this, R.string.phone_not_ready, Toast.LENGTH_SHORT).show()
            else startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, live)
            }, getString(R.string.phone_share)))
        }
        status.setText(R.string.phone_waiting)
    }

    override fun onResume() { super.onResume(); visible = true; tryStart() }
    override fun onPause() { visible = false; stopStreaming(); super.onPause() }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try { updateOrientation() } catch (_: Exception) { stopStreaming(); tryStart() }
    }
    override fun onDestroy() {
        preview.holder.removeCallback(this)
        worker.shutdownNow()
        super.onDestroy()
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun surfaceCreated(holder: SurfaceHolder) { surfaceReady = true; tryStart() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceReady = false; stopStreaming() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 510) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) tryStart()
            else status.setText(R.string.phone_permission_denied)
        }
    }

    private fun tryStart() {
        if (!visible || !surfaceReady || camera != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!permissionRequested) {
                permissionRequested = true
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 510)
            } else status.setText(R.string.phone_permission_denied)
            return
        }
        val ip = NetUtil.wifiIpv4(this)
        if (ip == null) {
            status.setText(R.string.phone_no_wifi)
            address.setText(R.string.phone_no_address)
            return
        }
        try {
            val deviceId = (0 until Camera.getNumberOfCameras()).firstOrNull { id ->
                val info = Camera.CameraInfo()
                Camera.getCameraInfo(id, info)
                info.facing == selectedFacing
            } ?: (0 until Camera.getNumberOfCameras()).firstOrNull()
                ?: throw IllegalStateException(getString(R.string.phone_no_camera))
            val c = Camera.open(deviceId)
            camera = c
            selectedId = deviceId
            val params = c.parameters
            val sizes = params.supportedPreviewSizes.orEmpty()
            val size = sizes.minByOrNull {
                kotlin.math.abs(it.width - 640) + kotlin.math.abs(it.height - 480) +
                    if (it.width * it.height > 1280 * 720) 10000 else 0
            } ?: throw IllegalStateException("Preview indisponível")
            params.setPreviewSize(size.width, size.height)
            params.previewFormat = ImageFormat.NV21
            if (params.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) == true)
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            c.parameters = params
            // O JPEG é orientado sem rotacionar e recomprimir o bitmap.
            updateOrientation()
            c.setPreviewDisplay(preview.holder)
            val session = PhoneCameraServer(ip, PhoneCameraAccess.token(this))
            server = session
            worker = if (worker.isShutdown) Executors.newSingleThreadExecutor() else worker
            encoding.set(false)
            val byteCount = size.width * size.height * 3 / 2
            c.setPreviewCallbackWithBuffer { data, source ->
                if (data != null && visible && server === session && data.size >= byteCount &&
                    !encoding.get() && SystemClock.elapsedRealtime() - lastCapture >= 250 &&
                    encoding.compareAndSet(false, true)) {
                    lastCapture = SystemClock.elapsedRealtime()
                    val snapshot = data.copyOf(byteCount)
                    val streamRotation = frameRotation
                    try {
                        worker.execute {
                            try {
                                val frame = Nv21Frames.rotated(snapshot, size.width, size.height, streamRotation)
                                val w = if (streamRotation % 180 == 0) size.width else size.height
                                val h = if (streamRotation % 180 == 0) size.height else size.width
                                val jpeg = ByteArrayOutputStream()
                                if (YuvImage(frame, ImageFormat.NV21, w, h, null)
                                        .compressToJpeg(Rect(0, 0, w, h), 65, jpeg)) {
                                    if (visible && server === session) session.publish(jpeg.toByteArray())
                                }
                            } catch (_: Exception) { } finally { encoding.set(false) }
                        }
                    } catch (_: Exception) { encoding.set(false) }
                }
                try { source.addCallbackBuffer(data) } catch (_: Exception) { }
            }
            repeat(3) { c.addCallbackBuffer(ByteArray(byteCount)) }
            c.startPreview()
            session.start()
            address.text = session.url
            status.setText(R.string.phone_online)
        } catch (_: Exception) {
            stopStreaming()
            status.setText(R.string.phone_start_failed)
            address.setText(R.string.phone_no_address)
        }
    }

    private fun updateOrientation() {
        val id = selectedId ?: return
        val c = camera ?: return
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(id, info)
        val rotation = when (windowManager.defaultDisplay.rotation) {
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
        val display = if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            (360 - (info.orientation + rotation) % 360) % 360
            else (info.orientation - rotation + 360) % 360
        frameRotation = if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            (info.orientation + rotation) % 360 else display
        c.setDisplayOrientation(display)
    }

    private fun stopStreaming() {
        val previous = server
        server = null
        previous?.stop()
        val c = camera
        camera = null
        selectedId = null
        try { c?.setPreviewCallbackWithBuffer(null) } catch (_: Exception) { }
        try { c?.stopPreview() } catch (_: Exception) { }
        try { c?.release() } catch (_: Exception) { }
        address.setText(R.string.phone_no_address)
        if (visible) status.setText(R.string.phone_waiting)
    }
}
