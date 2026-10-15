package com.minhacamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.res.Configuration
import android.content.Intent
import android.os.SystemClock
import android.view.TextureView
import android.view.WindowManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
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
import java.util.concurrent.atomic.AtomicInteger

@Suppress("DEPRECATION")
class PhoneCameraActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {
    private lateinit var preview: TextureView
    private lateinit var status: TextView
    private lateinit var address: TextView
    private lateinit var placeholder: TextView
    private lateinit var singlePreviewContainer: View
    private lateinit var dualPreviewContainer: View
    private lateinit var dualTop: ImageView
    private lateinit var dualBottom: ImageView
    private lateinit var telemetryPreview: TextView
    private val batteryTelemetry by lazy { PhoneBatteryTelemetry(applicationContext) }
    private val batteryOverlay = PhoneBatteryOverlay()
    @Volatile private var showBatteryOverlay = true
    private var lastTelemetryLabel = ""
    private lateinit var recordLabel: TextView
    private lateinit var recordIcon: ImageView
    private lateinit var recordButton: View
    private val uiHandler = Handler(Looper.getMainLooper())
    private var dualMode = false
    private var dualController: PhoneDualCamera? = null
    @Volatile private var recordingRequested = false
    private var recorder: PhoneMp4Recorder? = null // worker exclusivo
    @Volatile private var recordingStartedAt = 0L
    private var lastDualPreviewTime = 0L
    private val recordTick = object : Runnable {
        override fun run() {
            if (recordingRequested && recordingStartedAt > 0L) {
                val seconds = (SystemClock.elapsedRealtime() - recordingStartedAt) / 1000
                status.text = "REC %02d:%02d".format(seconds / 60, seconds % 60)
                uiHandler.postDelayed(this, 1000)
            }
        }
    }
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
    private val recordWorker = Executors.newSingleThreadExecutor()
    private val encoding = AtomicBoolean(false)
    private val recordingFrameBusy = AtomicBoolean(false)
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
        telemetryPreview = findViewById(R.id.phoneTelemetryPreview)
        showBatteryOverlay = getSharedPreferences("phone_video", MODE_PRIVATE)
            .getBoolean("show_battery_overlay", true)
        singlePreviewContainer = findViewById(R.id.phoneSinglePreview)
        dualPreviewContainer = findViewById(R.id.phoneDualPreview)
        dualTop = findViewById(R.id.phoneDualTop)
        dualBottom = findViewById(R.id.phoneDualBottom)
        recordButton = findViewById(R.id.phoneRecord)
        recordLabel = findViewById(R.id.phoneRecordLabel)
        recordIcon = findViewById(R.id.phoneRecordIcon)
        findViewById<View>(R.id.phoneDual).setOnClickListener { toggleDual() }
        recordButton.setOnClickListener { toggleRecording() }
        findViewById<View>(R.id.phoneMore).setOnClickListener { openMoreDialog() }
        preview.surfaceTextureListener = this
        // SurfaceTexture desenha como uma View normal, mesmo dentro de ScrollView.
        surfaceReady = preview.isAvailable
        findViewById<View>(R.id.phoneSwitch).setOnClickListener {
            if (dualMode) {
                Toast.makeText(this, R.string.phone_dual_has_both, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (recordingRequested) {
                Toast.makeText(this, R.string.phone_stop_record_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectedFacing = if (selectedFacing == Camera.CameraInfo.CAMERA_FACING_BACK)
                Camera.CameraInfo.CAMERA_FACING_FRONT else Camera.CameraInfo.CAMERA_FACING_BACK
            stopStreaming()
            tryStart()
        }
        findViewById<View>(R.id.phoneQr).setOnClickListener { openQrDialog() }

        // Código de acesso acessível em "Mais", preservando as quatro ações anteriores.
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
        if (dualMode) { stopStreaming(); tryStart() }
        else try { updateOrientation() } catch (_: Exception) { stopStreaming(); tryStart() }
    }
    override fun onDestroy() {
        preview.surfaceTextureListener = null
        uiHandler.removeCallbacks(recordTick)
        // Terminar gravações pendentes antes de encerrar o worker.
        worker.shutdown()
        recordWorker.shutdown()
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
        if (!dualMode) stopStreaming()
        return true // Android libera a textura após a câmera deixar de utilizá-la.
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        renderedPreviewFrames = true
        if (placeholder.visibility != View.GONE) placeholder.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 511) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleRecording()
            else Toast.makeText(this, R.string.phone_storage_denied, Toast.LENGTH_LONG).show()
        }
        if (requestCode == 510) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) tryStart()
            else status.setText(R.string.phone_permission_denied)
        }
    }

    private fun tryStart() {
        if (!visible || (!dualMode && (!surfaceReady || !preview.isAvailable)) || camera != null || dualController != null) return
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
            if (dualMode) {
                if (startDual(ip)) return
                dualMode = false
                dualPreviewContainer.visibility = View.GONE
                singlePreviewContainer.visibility = View.VISIBLE
                Toast.makeText(this, R.string.phone_dual_unavailable, Toast.LENGTH_LONG).show()
                if (!surfaceReady || !preview.isAvailable) return
            }
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
            lastTelemetryLabel = ""
            server = session
            worker = if (worker.isShutdown) Executors.newSingleThreadExecutor() else worker
            encoding.set(false)
            val byteCount = size.width * size.height * 3 / 2
            c.setPreviewCallbackWithBuffer { data, source ->
                if (data != null && visible && server === session && data.size >= byteCount) {
                    receivedCameraFrames = true
                }
                if (data != null && visible && server === session && data.size >= byteCount &&
                    !encoding.get() && SystemClock.elapsedRealtime() - lastCapture >= 90 &&
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
                                publishFrame(session, frame, w, h, false)
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

    private fun toggleDual() {
        if (recordingRequested) {
            Toast.makeText(this, R.string.phone_stop_record_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (!dualMode && !PhoneDualCamera.supported(this)) {
            Toast.makeText(this, R.string.phone_dual_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        dualMode = !dualMode
        stopStreaming()
        singlePreviewContainer.visibility = if (dualMode) View.GONE else View.VISIBLE
        dualPreviewContainer.visibility = if (dualMode) View.VISIBLE else View.GONE
        tryStart()
    }

    private fun startDual(ip: java.net.Inet4Address): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val session = try { PhoneCameraServer(ip, PhoneCameraAccess.token(this)) }
            catch (_: Exception) { return false }
        val controller = PhoneDualCamera(this, { a, b, w, h ->
            if (visible && server === session && !encoding.get() &&
                encoding.compareAndSet(false, true)) {
                try {
                    worker.execute {
                        try {
                            if (visible && server === session) {
                                val frame = PhoneDualFrames.stack(a, b, w, h)
                                publishFrame(session, frame, w, h * 2, true)
                            }
                        } catch (_: Exception) { } finally { encoding.set(false) }
                    }
                } catch (_: Exception) { encoding.set(false) }
            }
        }, { message ->
            runOnUiThread {
                if (server === session && visible) {
                    stopStreaming()
                    dualMode = false
                    dualPreviewContainer.visibility = View.GONE
                    singlePreviewContainer.visibility = View.VISIBLE
                    status.text = message
                    tryStart()
                }
            }
        })
        if (!controller.start()) { controller.close(); session.stop(); return false }
        dualController = controller
        lastTelemetryLabel = ""
        server = session
        worker = if (worker.isShutdown) Executors.newSingleThreadExecutor() else worker
        session.start()
        address.text = session.url
        status.setText(R.string.phone_waiting_frames)
        uiHandler.postDelayed({
            if (visible && server === session && !publishedStreamFrame) status.setText(R.string.phone_no_frames)
        }, 5000)
        return true
    }

    private fun publishFrame(session: PhoneCameraServer, frame: ByteArray, w: Int, h: Int, dual: Boolean) {
        if (showBatteryOverlay) {
            val label = batteryTelemetry.read().display()
            batteryOverlay.burnInto(frame, w, h, label)
            if (lastTelemetryLabel != label) {
                lastTelemetryLabel = label
                runOnUiThread {
                    if (visible && server === session && showBatteryOverlay) {
                        telemetryPreview.text = label
                        telemetryPreview.visibility = if (dual) View.GONE else View.VISIBLE
                    }
                }
            }
        }
        val jpeg = ByteArrayOutputStream()
        if (!YuvImage(frame, ImageFormat.NV21, w, h, null)
                .compressToJpeg(Rect(0, 0, w, h), 62, jpeg)) return
        if (!visible || server !== session) return
        val encoded = jpeg.toByteArray()
        session.publish(encoded)
        if (!publishedStreamFrame) {
            publishedStreamFrame = true
            runOnUiThread { if (visible && server === session && !recordingRequested) status.setText(R.string.phone_online) }
        }
        if (dual && SystemClock.elapsedRealtime() - lastDualPreviewTime >= 160) {
            lastDualPreviewTime = SystemClock.elapsedRealtime()
            try {
                val full = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
                if (full != null) {
                    val half = full.height / 2
                    val top = Bitmap.createBitmap(full, 0, 0, full.width, half)
                    val bottom = Bitmap.createBitmap(full, 0, half, full.width, half)
                    full.recycle()
                    runOnUiThread {
                        if (dualMode && visible && server === session) {
                            dualTop.setImageBitmap(top)
                            dualBottom.setImageBitmap(bottom)
                        } else { top.recycle(); bottom.recycle() }
                    }
                }
            } catch (_: Exception) { }
        }
        if (recordingRequested && recordingFrameBusy.compareAndSet(false, true)) {
            val snapshot = frame.copyOf()
            val timestamp = System.nanoTime()
            try {
                recordWorker.execute {
                    try {
                        if (recordingRequested) {
                            if (recorder == null) {
                                recorder = PhoneMp4Recorder(applicationContext, w, h)
                                recordingStartedAt = SystemClock.elapsedRealtime()
                                runOnUiThread { showRecordState(true) }
                            }
                            recorder?.append(snapshot, timestamp)
                        }
                    } catch (_: Exception) {
                        recordingRequested = false
                        recorder?.cancel()
                        recorder = null
                        runOnUiThread {
                            showRecordState(false)
                            Toast.makeText(this, R.string.phone_record_failed, Toast.LENGTH_LONG).show()
                        }
                    } finally { recordingFrameBusy.set(false) }
                }
            } catch (_: Exception) { recordingFrameBusy.set(false) }
        }
    }

    private fun toggleRecording() {
        if (recordingRequested) {
            recordingRequested = false
            showRecordState(false)
            recordWorker.execute { finishRecording() }
        } else {
            if (server == null || !publishedStreamFrame) {
                Toast.makeText(this, R.string.phone_not_ready, Toast.LENGTH_SHORT).show()
                return
            }
            if (Build.VERSION.SDK_INT < 29 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 511)
                return
            }
            recordingRequested = true
            status.setText(R.string.phone_record_preparing)
            showRecordState(true)
        }
    }

    private fun showRecordState(active: Boolean) {
        recordLabel.setText(if (active) R.string.ui_stop else R.string.ui_record)
        recordIcon.setImageResource(if (active) R.drawable.ic_ui_stop else R.drawable.ic_ui_record)
        recordIcon.setColorFilter(if (active) 0xFFB22323.toInt() else 0xFF111827.toInt())
        uiHandler.removeCallbacks(recordTick)
        if (active && recordingStartedAt > 0L) uiHandler.post(recordTick)
        if (!active) {
            recordingStartedAt = 0L
            if (server != null) status.setText(R.string.phone_online)
        }
    }

    /** Executar exclusivamente no worker de gravação após os quadros já enfileirados. */
    private fun finishRecording() {
        val previous = recorder ?: return
        recorder = null
        try {
            val saved = previous.finish()
            runOnUiThread {
                Toast.makeText(this, if (saved != null) R.string.phone_record_saved else R.string.phone_record_empty,
                    Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            runOnUiThread { Toast.makeText(this, R.string.phone_record_failed, Toast.LENGTH_LONG).show() }
        }
    }

    private fun openMoreDialog() {
        val overlayOption = getString(if (showBatteryOverlay)
            R.string.phone_hide_battery_overlay else R.string.phone_show_battery_overlay)
        AlertDialog.Builder(this).setTitle(R.string.ui_more)
            .setItems(arrayOf(getString(R.string.ui_link), getString(R.string.ui_code),
                overlayOption, getString(R.string.phone_record_location))) { _, which ->
                when (which) {
                    0 -> openLinkDialog()
                    1 -> findViewById<View>(R.id.phoneRevoke).performClick()
                    2 -> {
                        showBatteryOverlay = !showBatteryOverlay
                        getSharedPreferences("phone_video", MODE_PRIVATE).edit()
                            .putBoolean("show_battery_overlay", showBatteryOverlay).apply()
                        lastTelemetryLabel = ""
                        telemetryPreview.visibility = View.GONE
                        Toast.makeText(this, if (showBatteryOverlay)
                            R.string.phone_show_battery_overlay else R.string.phone_hide_battery_overlay,
                            Toast.LENGTH_SHORT).show()
                    }
                    else -> Toast.makeText(this, R.string.phone_record_location, Toast.LENGTH_LONG).show()
                }
            }.setNegativeButton(R.string.cancelar, null).show()
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
        recordingRequested = false
        uiHandler.removeCallbacks(recordTick)
        showRecordState(false)
        try { recordWorker.execute { finishRecording() } } catch (_: Exception) { }
        dualController?.close()
        dualController = null
        dualTop.setImageDrawable(null)
        dualBottom.setImageDrawable(null)
        lastTelemetryLabel = ""
        telemetryPreview.visibility = View.GONE
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
