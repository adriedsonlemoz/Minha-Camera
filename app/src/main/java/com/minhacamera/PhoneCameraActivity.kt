package com.minhacamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.os.Bundle
import android.content.res.Configuration
import android.content.Intent
import android.os.SystemClock
import android.view.TextureView
import android.view.WindowManager
import android.view.View
import android.widget.ImageView
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION")
class PhoneCameraActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {
    private lateinit var preview: TextureView
    private lateinit var status: TextView
    private lateinit var address: TextView
    private lateinit var placeholder: TextView
    private var qrDialog: AlertDialog? = null
    private var surfaceReady = false
    private var previewWidth = 0
    private var previewHeight = 0
    private var previewDisplayRotation = 0
    @Volatile private var receivedCameraFrames = false
    @Volatile private var renderedPreviewFrames = false
    @Volatile private var publishedStreamFrame = false
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
        placeholder = findViewById(R.id.previewPlaceholder)
        preview.surfaceTextureListener = this
        // SurfaceTexture desenha como uma View normal, mesmo dentro de ScrollView.
        surfaceReady = preview.isAvailable
        findViewById<View>(R.id.phoneSwitch).setOnClickListener {
            selectedFacing = if (selectedFacing == Camera.CameraInfo.CAMERA_FACING_BACK)
                Camera.CameraInfo.CAMERA_FACING_FRONT else Camera.CameraInfo.CAMERA_FACING_BACK
            stopStreaming()
            tryStart()
        }
        findViewById<View>(R.id.phoneQr).setOnClickListener { openQrDialog() }
        findViewById<View>(R.id.phoneLink).setOnClickListener { openLinkDialog() }
        findViewById<View>(R.id.phoneRevoke).setOnClickListener {
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
        status.setText(R.string.phone_waiting)
    }

    override fun onResume() {
        super.onResume()
        visible = true
        surfaceReady = preview.isAvailable
        tryStart()
    }
    override fun onPause() { visible = false; stopStreaming(); super.onPause() }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try { updateOrientation() } catch (_: Exception) { stopStreaming(); tryStart() }
    }
    override fun onDestroy() {
        preview.surfaceTextureListener = null
        worker.shutdownNow()
        super.onDestroy()
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceReady = true
        tryStart()
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        updatePreviewTransform()
    }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        surfaceReady = false
        stopStreaming()
        return true // Android libera a textura após a câmera deixar de utilizá-la.
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        renderedPreviewFrames = true
        if (placeholder.visibility != View.GONE) placeholder.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 510) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) tryStart()
            else status.setText(R.string.phone_permission_denied)
        }
    }

    private fun tryStart() {
        if (!visible || !surfaceReady || !preview.isAvailable || camera != null) return
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
            previewWidth = size.width
            previewHeight = size.height
            receivedCameraFrames = false
            renderedPreviewFrames = false
            publishedStreamFrame = false
            placeholder.visibility = View.VISIBLE
            // TextureView evita a superfície externa do SurfaceView em ScrollViews MIUI.
            // O JPEG da rede continua orientado por NV21, independente da prévia.
            updateOrientation()
            c.setPreviewTexture(preview.surfaceTexture
                ?: throw IllegalStateException("Superfície da prévia indisponível"))
            val session = PhoneCameraServer(ip, PhoneCameraAccess.token(this))
            server = session
            worker = if (worker.isShutdown) Executors.newSingleThreadExecutor() else worker
            encoding.set(false)
            val byteCount = size.width * size.height * 3 / 2
            c.setPreviewCallbackWithBuffer { data, source ->
                if (data != null && visible && server === session && data.size >= byteCount) {
                    receivedCameraFrames = true
                }
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
                                    if (visible && server === session) {
                                        session.publish(jpeg.toByteArray())
                                        if (!publishedStreamFrame) {
                                            publishedStreamFrame = true
                                            runOnUiThread {
                                                if (visible && server === session) status.setText(R.string.phone_online)
                                            }
                                        }
                                    }
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
            status.setText(R.string.phone_waiting_frames)
            // O QR só é criado ao tocar no botão QR, nunca automaticamente.
            // Uma sessão TCP/QR ativa não prova que a câmera esteja fornecendo imagem.
            // Informa precisamente se faltam quadros ou se a prévia gráfica não os desenha.
            preview.postDelayed({
                if (visible && server === session) {
                    when {
                        !receivedCameraFrames || !publishedStreamFrame -> status.setText(R.string.phone_no_frames)
                        !renderedPreviewFrames -> status.setText(R.string.phone_preview_not_rendered)
                    }
                }
            }, 5000)
        } catch (_: Exception) {
            stopStreaming()
            status.setText(R.string.phone_start_failed)
            address.setText(R.string.phone_no_address)
        }
    }

    private fun openQrDialog() {
        val live = server?.url
        if (live == null) {
            Toast.makeText(this, R.string.phone_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        if (qrDialog?.isShowing == true) return
        try {
            val bitmap = BarcodeEncoder().encodeBitmap(live, BarcodeFormat.QR_CODE, 512, 512)
            val content = LayoutInflater.from(this).inflate(R.layout.dialog_phone_qr, null)
            content.findViewById<ImageView>(R.id.dialogQr).setImageBitmap(bitmap)
            val dialog = AlertDialog.Builder(this).setView(content).create()
            qrDialog = dialog
            content.findViewById<View>(R.id.dialogClose).setOnClickListener { dialog.dismiss() }
            dialog.setOnDismissListener { if (qrDialog === dialog) qrDialog = null }
            dialog.show()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.91f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.phone_qr_generation_error, Toast.LENGTH_SHORT).show()
        }
    }

    /** Link mantém as duas ações legadas em um único item da barra. */
    private fun openLinkDialog() {
        val live = server?.url
        if (live == null) {
            Toast.makeText(this, R.string.phone_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.ui_link_actions)
            .setItems(arrayOf(getString(R.string.ui_link_copy), getString(R.string.ui_link_share))) { _, selection ->
                if (selection == 0) {
                    CameraFormUi.copyText(this, live, getString(R.string.phone_stream_url), true)
                    Toast.makeText(this, R.string.phone_copied, Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, live)
                    }, getString(R.string.phone_share)))
                }
            }.setNegativeButton(R.string.cancelar, null).show()
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
        previewDisplayRotation = display
        c.setDisplayOrientation(display)
        updatePreviewTransform()
    }

    /** Centraliza a imagem sem achatamento, com recorte apenas do excedente. */
    private fun updatePreviewTransform() {
        if (previewWidth <= 0 || previewHeight <= 0 || preview.width <= 0 || preview.height <= 0) return
        val rotated = previewDisplayRotation % 180 != 0
        val imageWidth = (if (rotated) previewHeight else previewWidth).toFloat()
        val imageHeight = (if (rotated) previewWidth else previewHeight).toFloat()
        val viewWidth = preview.width.toFloat()
        val viewHeight = preview.height.toFloat()
        val cover = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val matrix = Matrix().apply {
            setScale(imageWidth * cover / viewWidth, imageHeight * cover / viewHeight,
                viewWidth / 2f, viewHeight / 2f)
        }
        preview.setTransform(matrix)
    }

    private fun stopStreaming() {
        qrDialog?.dismiss()
        qrDialog = null
        val previous = server
        server = null
        previous?.stop()
        val c = camera
        camera = null
        selectedId = null
        receivedCameraFrames = false
        renderedPreviewFrames = false
        publishedStreamFrame = false
        try { c?.setPreviewCallbackWithBuffer(null) } catch (_: Exception) { }
        try { c?.stopPreview() } catch (_: Exception) { }
        try { c?.release() } catch (_: Exception) { }
        address.setText(R.string.phone_no_address)
        if (visible) status.setText(R.string.phone_waiting)
    }
}
