package com.minhacamera

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Rational
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.os.Build
import android.os.Environment
import android.content.ContentValues
import android.provider.MediaStore
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.widget.SwitchCompat
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.BufferedInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Receptor MJPEG leve em retrato, com telemetria do celular transmissor. */
class PhoneCameraViewerActivity : AppCompatActivity() {
    private data class PendingFrame(
        val bitmap: Bitmap,
        val deliveryMs: Long?,
        val battery: PhoneBatteryReading?,
        val remoteRecording: Boolean?,
        val remoteEco: Boolean?,
        val recordingSeconds: Long?,
        val cameraFacing: Int?,
        val qualityProfile: Int?,
        val targetFps: Int?,
        val jpeg: ByteArray
    )
    private class InvalidTokenException : IOException()

    private lateinit var pipHost: FrameLayout
    private lateinit var batteryPill: TextView
    private lateinit var temperatureLabel: TextView
    private lateinit var resolutionLabel: TextView
    private lateinit var fpsLabel: TextView
    private lateinit var ecoSwitch: SwitchCompat
    private lateinit var recordLabel: TextView
    private lateinit var recordIcon: ImageView
    private lateinit var onlineDot: View
    private lateinit var remoteSection: View
    private var lastShownFps: Int? = null
    private var remoteEco: Boolean? = null
    private var rotation180 = false
    @Volatile private var latestPhotoBytes: ByteArray? = null
    private lateinit var fullscreenHost: FrameLayout
    private lateinit var fullControls: View
    private lateinit var recordingClock: TextView
    private lateinit var facingLabel: TextView
    private var remoteFacing: Int? = null
    private var remoteQuality: Int? = null
    private var remoteFps: Int? = null
    private lateinit var scrollContent: ScrollView
    private lateinit var normalPreviewParent: LinearLayout
    private var previewIndex = 1
    private var isPipLayout = false
    @Volatile private var imageAspect = Rational(16, 9)
    private var lastFrameWidth = 0
    private var lastFrameHeight = 0
    private lateinit var image: ImageView
    private lateinit var status: TextView
    private lateinit var performance: TextView
    private lateinit var telemetry: TextView
    private lateinit var telemetryDetails: TextView
    private lateinit var fitLabel: TextView
    private lateinit var fitIcon: ImageView
    private lateinit var mirrorLabel: TextView
    private lateinit var previewContainer: View
    private lateinit var controls: View
    private lateinit var receiverInstructions: View
    @Volatile private var active = false
    @Volatile private var connection: HttpURLConnection? = null
    private var reader: Thread? = null
    @Volatile private var attempts = 0
    private val frameOnDeck = AtomicReference<PendingFrame?>(null)
    private val uiScheduled = AtomicBoolean(false)
    private val fps = PhoneStreamStats()
    private var displayedFirstFrame = false
    private var lastDeliveryMs: Long? = null
    private var fillMode = false
    private var mirrorMode = false
    private var focusMode = false
    private var lastTelemetryText: String? = null
    private var showTelemetry = true
    private var showBatteryLevel = true
    private var showTemperature = true
    private var showChargeSymbol = true
    private var detailsVisible = false
    private var lastBattery: PhoneBatteryReading? = null
    private var remoteRecording: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_phone_viewer)
        title = intent.getStringExtra("name") ?: getString(R.string.phone_camera)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // Cabeçalho da câmera obtido do cadastro ou do endereço, nunca inventa o modelo.
        findViewById<TextView>(R.id.viewerCameraName).text = title
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        batteryPill = findViewById(R.id.viewerBatteryPill)
        temperatureLabel = findViewById(R.id.viewerTemperature)
        resolutionLabel = findViewById(R.id.viewerRemoteQualityValue)
        fpsLabel = findViewById(R.id.viewerRemoteFpsValue)
        ecoSwitch = findViewById(R.id.viewerRemoteEcoSwitch)
        recordingClock = findViewById(R.id.viewerRecordingClock)
        facingLabel = findViewById(R.id.viewerRemoteFacingValue)
        fullscreenHost = findViewById(R.id.phoneViewerFullscreenHost)
        fullControls = findViewById(R.id.viewerFullscreenControls)
        recordLabel = findViewById(R.id.viewerRecordActionLabel)
        recordIcon = findViewById(R.id.viewerRecordActionIcon)
        onlineDot = findViewById(R.id.viewerOnlineDot)
        remoteSection = findViewById(R.id.viewerRemoteSection)
        image = findViewById(R.id.phoneImage)
        status = findViewById(R.id.phoneViewerStatus)
        performance = findViewById(R.id.phoneViewerPerformance)
        telemetry = findViewById(R.id.phoneViewerTelemetry)
        telemetryDetails = findViewById(R.id.phoneViewerTelemetryDetails)
        fitLabel = findViewById(R.id.phoneViewerFitLabel)
        fitIcon = findViewById(R.id.phoneViewerFitIcon)
        mirrorLabel = findViewById(R.id.phoneViewerMirrorLabel)
        previewContainer = findViewById(R.id.phoneViewerPreviewContainer)
        pipHost = findViewById(R.id.phoneViewerPipHost)
        scrollContent = findViewById(R.id.phoneViewerScroll)
        normalPreviewParent = previewContainer.parent as LinearLayout
        previewIndex = normalPreviewParent.indexOfChild(previewContainer)
        controls = findViewById(R.id.phoneViewerControls)
        receiverInstructions = findViewById(R.id.phoneViewerInstructions)
        val settings = getSharedPreferences("phone_viewer", MODE_PRIVATE)
        fillMode = settings.getBoolean("fill", false)
        mirrorMode = settings.getBoolean("mirror", false)
        showTelemetry = settings.getBoolean("telemetry_visible", true)
        showBatteryLevel = settings.getBoolean("telemetry_battery", true)
        showTemperature = settings.getBoolean("telemetry_temperature", true)
        showChargeSymbol = settings.getBoolean("telemetry_charging", true)
        detailsVisible = settings.getBoolean("details_visible", false)
        findViewById<View>(R.id.phoneRetry).setOnClickListener { stop(); start() }
        findViewById<View>(R.id.phoneViewerFit).setOnClickListener { toggleFitMode() }
        findViewById<View>(R.id.phoneViewerMirror).setOnClickListener { toggleMirrorMode() }
        findViewById<View>(R.id.phoneViewerFocus).setOnClickListener { toggleFocusMode() }
        previewContainer.setOnClickListener {
            if (focusMode) fullControls.visibility = if (fullControls.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
            else toggleFocusMode()
        }
        findViewById<View>(R.id.viewerPipAction).setOnClickListener { enterPip() }
        findViewById<View>(R.id.viewerRotateAction).setOnClickListener {
            rotation180 = !rotation180
            image.rotation = if (rotation180) 180f else 0f
        }
        findViewById<View>(R.id.viewerSnapshotAction).setOnClickListener { requestRemotePhoto() }
        findViewById<View>(R.id.viewerRemoteQuality).setOnClickListener { chooseRemoteQuality() }
        findViewById<View>(R.id.viewerRemoteFps).setOnClickListener { chooseRemoteFps() }
        findViewById<View>(R.id.viewerRemoteFacing).setOnClickListener { chooseRemoteFacing() }
        findViewById<View>(R.id.viewerFullBack).setOnClickListener { if (focusMode) toggleFocusMode() }
        findViewById<View>(R.id.viewerFullRecord).setOnClickListener { setRemoteRecording(remoteRecording != true) }
        findViewById<View>(R.id.viewerFullPhoto).setOnClickListener { requestRemotePhoto() }
        findViewById<View>(R.id.viewerFullRotate).setOnClickListener {
            rotation180 = !rotation180
            image.rotation = if (rotation180) 180f else 0f
        }
        findViewById<View>(R.id.viewerFullPip).setOnClickListener { enterPip() }
        findViewById<View>(R.id.viewerRecordAction).setOnClickListener { setRemoteRecording(remoteRecording != true) }
        findViewById<View>(R.id.viewerRemoteEco).setOnClickListener {
            if (remoteEco == null) {
                Toast.makeText(this, R.string.viewer_eco_unavailable, Toast.LENGTH_LONG).show()
            } else setRemoteEco(remoteEco != true)
        }
        findViewById<View>(R.id.phoneViewerMore).setOnClickListener { openMoreDialog() }
        refreshFitModeUi()
        refreshMirrorModeUi()
        refreshFocusModeUi()
        applyTelemetry(null)
        refreshRecordingUi()
        updateEcoUi()
    }

    // onPause acontece ao entrar no PiP: manter a leitura MJPEG enquanto a janela está visível.
    override fun onResume() { super.onResume(); if (!active) start() }
    override fun onStop() { if (!(android.os.Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode)) stop(); super.onStop() }
    override fun onDestroy() { stop(); super.onDestroy() }

    override fun onPictureInPictureModeChanged(inPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig)
        switchPipLayout(inPictureInPictureMode)
        if (!inPictureInPictureMode && !active && !isFinishing && !isDestroyed) start()
    }

    private fun enterPip() {
        if (focusMode) toggleFocusMode()
        if (!PipSupport.available(this)) {
            Toast.makeText(this, R.string.pip_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        if (!displayedFirstFrame || !active) {
            Toast.makeText(this, R.string.pip_wait_for_video, Toast.LENGTH_SHORT).show()
            return
        }
        switchPipLayout(true)
        pipHost.post {
            if (!PipSupport.enter(this, pipHost, imageAspect)) {
                switchPipLayout(false)
                Toast.makeText(this, R.string.pip_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun switchPipLayout(pip: Boolean) {
        if (isPipLayout == pip) return
        isPipLayout = pip
        (previewContainer.parent as? ViewGroup)?.removeView(previewContainer)
        if (pip) {
            scrollContent.visibility = View.GONE
            pipHost.visibility = View.VISIBLE
            pipHost.addView(previewContainer, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            supportActionBar?.hide()
        } else {
            pipHost.visibility = View.GONE
            normalPreviewParent.addView(previewContainer, previewIndex, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (290f * resources.displayMetrics.density + 0.5f).toInt()))
            scrollContent.visibility = View.VISIBLE
            supportActionBar?.show()
            refreshFocusModeUi()
        }
        telemetryDetails.visibility = if (pip) View.GONE else telemetryDetails.visibility
        applyTelemetry(lastBattery)
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun toggleFitMode() {
        fillMode = !fillMode
        getSharedPreferences("phone_viewer", MODE_PRIVATE).edit().putBoolean("fill", fillMode).apply()
        refreshFitModeUi()
    }

    private fun refreshFitModeUi() {
        image.scaleType = if (fillMode) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        fitLabel.setText(if (fillMode) R.string.phone_view_fill else R.string.phone_view_fit)
        fitIcon.setImageResource(R.drawable.ic_ui_expand)
    }

    private fun toggleMirrorMode() {
        mirrorMode = !mirrorMode
        getSharedPreferences("phone_viewer", MODE_PRIVATE).edit().putBoolean("mirror", mirrorMode).apply()
        refreshMirrorModeUi()
    }

    private fun refreshMirrorModeUi() {
        // Transformação da View: nenhum novo JPEG, Bitmap ou gasto de CPU por quadro.
        // Badge nativo por cima da imagem não é invertido.
        image.scaleX = if (mirrorMode) -1f else 1f
        val actionLabel = if (mirrorMode) R.string.phone_view_unmirror else R.string.phone_view_mirror
        mirrorLabel.setText(actionLabel)
        findViewById<View>(R.id.phoneViewerMirror).contentDescription = getString(actionLabel)
    }

    private fun toggleFocusMode() {
        if (isPipLayout) return
        focusMode = !focusMode
        refreshFocusModeUi()
        if (focusMode) Toast.makeText(this, R.string.viewer_fullscreen_hint, Toast.LENGTH_SHORT).show()
    }

    private fun refreshFocusModeUi() {
        if (isPipLayout) return
        val currentlyFull = fullscreenHost.visibility == View.VISIBLE
        if (focusMode == currentlyFull) return
        (previewContainer.parent as? ViewGroup)?.removeView(previewContainer)
        if (focusMode) {
            scrollContent.visibility = View.GONE
            fullscreenHost.visibility = View.VISIBLE
            fullscreenHost.addView(previewContainer, 0, FrameLayout.LayoutParams(-1, -1))
            fullControls.visibility = View.VISIBLE
            supportActionBar?.hide()
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            fullscreenHost.visibility = View.GONE
            normalPreviewParent.addView(previewContainer, previewIndex,
                LinearLayout.LayoutParams(-1, (290f * resources.displayMetrics.density + 0.5f).toInt()))
            scrollContent.visibility = View.VISIBLE
            supportActionBar?.show()
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        applyTelemetry(lastBattery)
    }

    @Deprecated("Usado apenas para sair do modo imersivo")
    override fun onBackPressed() {
        if (focusMode) toggleFocusMode() else super.onBackPressed()
    }

    private fun openMoreDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad / 2)
        }
        content.setBackgroundResource(R.drawable.bg_phone_more_dialog)
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        val dialog = AlertDialog.Builder(this).setView(scroll).create()
        fun addRow(label: String, icon: Int, switch: Boolean? = null, action: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                minimumHeight = (58 * resources.displayMetrics.density).toInt()
                isClickable = true
                isFocusable = true
                background = androidx.core.content.ContextCompat.getDrawable(this@PhoneCameraViewerActivity, R.drawable.bg_ui_card)
                val margin = (4 * resources.displayMetrics.density).toInt()
                setPadding(margin * 2, margin, margin * 2, margin)
            }
            val imageView = ImageView(this).apply { setImageResource(icon) }
            row.addView(imageView, LinearLayout.LayoutParams((25 * resources.displayMetrics.density).toInt(), (25 * resources.displayMetrics.density).toInt()))
            val labelView = TextView(this).apply {
                text = label
                setTextColor(ContextCompat.getColor(this@PhoneCameraViewerActivity, R.color.app_text))
                textSize = 15f
            }
            row.addView(labelView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (12 * resources.displayMetrics.density).toInt()
            })
            if (switch != null) row.addView(SwitchCompat(this).apply {
                isChecked = switch
                isClickable = false
                isFocusable = false
            })
            row.setOnClickListener { dialog.dismiss(); action() }
            content.addView(row, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = (7*resources.displayMetrics.density).toInt() })
        }
        val titleView = TextView(this).apply {
            setText(R.string.ui_more)
            textSize = 23f
            setTextColor(ContextCompat.getColor(this@PhoneCameraViewerActivity, R.color.app_text))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        content.addView(titleView, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = (12*resources.displayMetrics.density).toInt() })
        addRow(getString(R.string.pip_window), R.drawable.ic_ui_pip) { enterPip() }
        addRow(getString(R.string.phone_view_focus), R.drawable.ic_fit, focusMode) { toggleFocusMode() }
        addRow(getString(R.string.phone_view_mirror), R.drawable.ic_ui_mirror, mirrorMode) { toggleMirrorMode() }
        addRow(getString(R.string.imagem_inteira), R.drawable.ic_fit, !fillMode) { toggleFitMode() }
        addRow(getString(R.string.phone_show_battery_overlay), R.drawable.ic_ui_battery, showTelemetry) {
            showTelemetry = !showTelemetry
            getSharedPreferences("phone_viewer", MODE_PRIVATE).edit().putBoolean("telemetry_visible", showTelemetry).apply()
            applyTelemetry(lastBattery)
        }
        addRow(getString(R.string.phone_view_telemetry_options), R.drawable.ic_ui_battery) { openTelemetryOptions() }
        addRow(getString(R.string.tentar_novamente), R.drawable.ic_ui_scan) { stop(); start() }
        addRow(getString(if (remoteRecording == true) R.string.phone_remote_record_stop else R.string.phone_remote_record_start), R.drawable.ic_ui_record) {
            setRemoteRecording(remoteRecording != true)
        }
        addRow(getString(R.string.viewer_technical_details), R.drawable.ic_ui_video) {
            val show = !detailsVisible
            detailsVisible = show
            getSharedPreferences("phone_viewer", MODE_PRIVATE).edit().putBoolean("details_visible", show).apply()
            applyTelemetry(lastBattery)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    /** Todos os comandos remotos usam POST e o token privado previamente validado. */
    private fun postRemote(endpoint: String?, notice: Int, onSuccess: () -> Unit = {}) {
        if (endpoint == null) {
            Toast.makeText(this, R.string.phone_invalid_url, Toast.LENGTH_SHORT).show()
            return
        }
        Thread({
            val ok = try {
                NetUtil.bindToWifi(applicationContext)
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("Content-Length", "0")
                    conn.responseCode == 204
                } finally { conn.disconnect() }
            } catch (_: Exception) { false }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (ok) onSuccess()
                    Toast.makeText(this, if (ok) notice else R.string.viewer_remote_command_failed,
                        Toast.LENGTH_LONG).show()
                }
            }
        }, "camera-remote-command").apply { isDaemon = true; start() }
    }

    private fun requestRemotePhoto() {
        val url = PhoneCameraUrls.photoCommandUrl(intent.getStringExtra("url").orEmpty())
        postRemote(url, R.string.viewer_remote_photo_requested)
    }

    private fun chooseRemoteQuality() {
        val options = intArrayOf(360, 480, 720)
        AlertDialog.Builder(this).setTitle(R.string.viewer_remote_quality_title)
            .setSingleChoiceItems(arrayOf("360p · economia", "480p · normal", "720p · qualidade"),
                options.indexOf(remoteQuality)) { dialog, index ->
                dialog.dismiss()
                postRemote(PhoneCameraUrls.qualityCommandUrl(intent.getStringExtra("url").orEmpty(), options[index]),
                    R.string.viewer_remote_quality_requested) { remoteQuality = options[index] }
            }.setNegativeButton(R.string.cancelar, null).show()
    }

    private fun chooseRemoteFps() {
        val options = intArrayOf(5, 10, 15)
        AlertDialog.Builder(this).setTitle(R.string.viewer_remote_fps_title)
            .setSingleChoiceItems(arrayOf("5 FPS", "10 FPS", "15 FPS"), options.indexOf(remoteFps)) { dialog, index ->
                dialog.dismiss()
                postRemote(PhoneCameraUrls.fpsCommandUrl(intent.getStringExtra("url").orEmpty(), options[index]),
                    R.string.viewer_remote_fps_updated) { remoteFps = options[index] }
            }.setNegativeButton(R.string.cancelar, null).show()
    }

    private fun chooseRemoteFacing() {
        AlertDialog.Builder(this).setTitle(R.string.viewer_remote_facing_title)
            .setSingleChoiceItems(arrayOf(getString(R.string.phone_back_camera), getString(R.string.phone_front_camera)),
                remoteFacing ?: -1) { dialog, index ->
                dialog.dismiss()
                postRemote(PhoneCameraUrls.facingCommandUrl(intent.getStringExtra("url").orEmpty(), index == 1),
                    R.string.viewer_remote_facing_requested)
            }.setNegativeButton(R.string.cancelar, null).show()
    }

    /** Comando HTTP POST idempotente na LAN, usando o mesmo token da URL MJPEG. */
    private fun setRemoteRecording(startRecording: Boolean) {
        val endpoint = PhoneCameraUrls.recordingCommandUrl(intent.getStringExtra("url").orEmpty(), startRecording)
            ?: run { Toast.makeText(this, R.string.phone_invalid_url, Toast.LENGTH_LONG).show(); return }
        Thread({
            val success = try {
                NetUtil.bindToWifi(applicationContext)
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Content-Length", "0")
                    connection.responseCode == 204
                } finally { connection.disconnect() }
            } catch (_: Exception) { false }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (success) remoteRecording = startRecording
                    Toast.makeText(this, if (!success) R.string.phone_remote_record_unavailable
                        else if (startRecording) R.string.phone_remote_record_started
                        else R.string.phone_remote_record_stopped, Toast.LENGTH_LONG).show()
                }
            }
        }, "phone-record-command").apply { isDaemon = true; start() }
    }

    /** Eco controla apenas o serviço persistente. POST autenticado pelo token local. */
    private fun setRemoteEco(enabled: Boolean) {
        val endpoint = PhoneCameraUrls.ecoCommandUrl(intent.getStringExtra("url").orEmpty(), enabled)
            ?: run { Toast.makeText(this, R.string.phone_invalid_url, Toast.LENGTH_LONG).show(); return }
        Thread({
            val success = try {
                NetUtil.bindToWifi(applicationContext)
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("Content-Length", "0")
                    conn.responseCode == 204
                } finally { conn.disconnect() }
            } catch (_: Exception) { false }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (success) remoteEco = enabled
                    updateEcoUi()
                    Toast.makeText(this, if (success) R.string.viewer_eco_updated else R.string.viewer_eco_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }, "phone-eco-command").apply { isDaemon = true; start() }
    }

    private fun updateEcoUi() {
        ecoSwitch.isChecked = remoteEco == true
        ecoSwitch.alpha = if (remoteEco == null) 0.4f else 1f
    }

    private fun refreshRecordingUi() {
        val recording = remoteRecording == true
        recordLabel.setText(if (recording) R.string.ui_stop else R.string.ui_record)
        recordIcon.setImageResource(if (recording) R.drawable.ic_ui_stop else R.drawable.ic_ui_record)
        if (::recordingClock.isInitialized) recordingClock.visibility =
            if (recording && !isPipLayout) View.VISIBLE else View.GONE
    }

    /** Foto do quadro recebido, salva NESTE aparelho, sem solicitar nova captura na câmera. */
    private fun saveReceivedSnapshot() {
        val bytes = latestPhotoBytes ?: run {
            Toast.makeText(this, R.string.viewer_photo_wait, Toast.LENGTH_SHORT).show()
            return
        }
        Thread({
            var saved = false
            try {
                val name = "MinhaCamera-${System.currentTimeMillis()}.jpg"
                if (Build.VERSION.SDK_INT >= 29) {
                    val resolver = applicationContext.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MinhaCamera")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        try {
                            resolver.openOutputStream(uri)?.use { it.write(bytes); saved = true }
                            if (saved) resolver.update(uri, ContentValues().apply {
                                put(MediaStore.Images.Media.IS_PENDING, 0)
                            }, null, null) else resolver.delete(uri, null, null)
                        } catch (_: Exception) { resolver.delete(uri, null, null) }
                    }
                } else {
                    val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MinhaCamera")
                    if (dir.exists() || dir.mkdirs()) {
                        FileOutputStream(File(dir, name)).use { it.write(bytes) }
                        saved = true
                    }
                }
            } catch (_: Exception) { saved = false }
            runOnUiThread { if (!isFinishing && !isDestroyed) Toast.makeText(this,
                if (saved) R.string.viewer_photo_saved else R.string.viewer_photo_failed, Toast.LENGTH_LONG).show() }
        }, "phone-received-photo").apply { isDaemon = true; start() }
    }

    private fun openTelemetryOptions() {
        val selected = booleanArrayOf(showBatteryLevel, showTemperature, showChargeSymbol)
        AlertDialog.Builder(this)
            .setTitle(R.string.phone_view_telemetry_options)
            .setMultiChoiceItems(arrayOf(getString(R.string.phone_view_show_battery),
                getString(R.string.phone_view_show_temperature),
                getString(R.string.phone_view_show_charge)), selected) { _, which, checked ->
                selected[which] = checked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showBatteryLevel = selected[0]
                showTemperature = selected[1]
                showChargeSymbol = selected[2]
                getSharedPreferences("phone_viewer", MODE_PRIVATE).edit()
                    .putBoolean("telemetry_battery", showBatteryLevel)
                    .putBoolean("telemetry_temperature", showTemperature)
                    .putBoolean("telemetry_charging", showChargeSymbol).apply()
                lastTelemetryText = null
                applyTelemetry(lastBattery)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun start() {
        val uri = PhoneCameraUrls.normalize(intent.getStringExtra("url").orEmpty())
        if (uri == null) { status.setText(R.string.phone_invalid_url); return }
        NetUtil.bindToWifi(this)
        active = true
        val session = ++attempts
        fps.reset(SystemClock.elapsedRealtime())
        displayedFirstFrame = false
        lastDeliveryMs = null
        lastBattery = null
        remoteRecording = null
        remoteEco = null
        remoteFacing = null
        remoteQuality = null
        remoteFps = null
        recordingClock.visibility = View.GONE
        facingLabel.text = getString(R.string.viewer_unknown)
        lastShownFps = null
        lastFrameWidth = 0
        lastFrameHeight = 0
        latestPhotoBytes = null
        lastTelemetryText = null
        updateEcoUi()
        refreshRecordingUi()
        resolutionLabel.text = getString(R.string.viewer_unknown)
        fpsLabel.text = getString(R.string.viewer_unknown)
        onlineDot.alpha = 0.3f
        image.setImageDrawable(null)
        performance.setText(R.string.phone_performance_waiting)
        applyTelemetry(null)
        status.setText(R.string.phone_connecting)
        reader = Thread({
            var failures = 0
            while (active && attempts == session && !Thread.currentThread().isInterrupted) {
                var ownConnection: HttpURLConnection? = null
                try {
                    NetUtil.bindToWifi(applicationContext)
                    if (NetUtil.wifiIpv4(applicationContext) == null) throw IOException("Wi-Fi indisponível")
                    val conn = (URL(uri).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 3500
                        readTimeout = 7000
                        instanceFollowRedirects = false
                        useCaches = false
                        setRequestProperty("Accept", "multipart/x-mixed-replace")
                    }
                    ownConnection = conn
                    if (!active || attempts != session) break
                    connection = conn
                    val startedAtMs = SystemClock.elapsedRealtime()
                    val responseCode = conn.responseCode
                    val headersAtMs = SystemClock.elapsedRealtime()
                    if (responseCode == 401 || responseCode == 403 || responseCode == 404) throw InvalidTokenException()
                    if (responseCode != 200 || !conn.contentType.orEmpty().startsWith("multipart/x-mixed-replace", true))
                        throw IOException("Transmissão indisponível")
                    val serverTime = conn.getHeaderField("X-Server-Elapsed-Ms")?.toLongOrNull()
                    val offset = PhoneDeliveryDelay.offsetMs(startedAtMs, headersAtMs, serverTime)
                    failures = 0
                    BufferedInputStream(conn.inputStream, 16 * 1024).use { input ->
                        while (active && attempts == session) {
                            val boundary = readAsciiLine(input) ?: throw IOException("Conexão encerrada")
                            if (boundary != "--frame") continue
                            var length = -1
                            var jpeg = false
                            var frameAtMs: Long? = null
                            var batteryLevel: Int? = null
                            var batteryTemp: Int? = null
                            var batteryCharging: Boolean? = null
                            var batteryAvgMa: Int? = null
                            var remoteRecordingHeader: Boolean? = null
                            var remoteEcoHeader: Boolean? = null
                            var remoteSecondsHeader: Long? = null
                            var remoteFacingHeader: Int? = null
                            var remoteQualityHeader: Int? = null
                            var remoteFpsHeader: Int? = null
                            while (true) {
                                val line = readAsciiLine(input) ?: throw IOException("Conexão encerrada")
                                if (line.isEmpty()) break
                                if (line.startsWith("Content-Length:", true))
                                    length = line.substringAfter(':').trim().toIntOrNull() ?: -1
                                if (line.equals("Content-Type: image/jpeg", true)) jpeg = true
                                if (line.startsWith("X-Frame-Elapsed-Ms:", true))
                                    frameAtMs = line.substringAfter(':').trim().toLongOrNull()
                                if (line.startsWith("X-Battery-Level:", true))
                                    batteryLevel = line.substringAfter(':').trim().toIntOrNull()
                                if (line.startsWith("X-Battery-Temp-Tenths:", true))
                                    batteryTemp = line.substringAfter(':').trim().toIntOrNull()
                                if (line.startsWith("X-Battery-Charging:", true)) {
                                    batteryCharging = when (line.substringAfter(':').trim()) {
                                        "1", "true", "TRUE" -> true
                                        "0", "false", "FALSE" -> false
                                        else -> null
                                    }
                                }
                                if (line.startsWith("X-Battery-Average-Ma:", true))
                                    batteryAvgMa = line.substringAfter(':').trim().toIntOrNull()
                                if (line.startsWith("X-Recording-Seconds:", true)) remoteSecondsHeader =
                                    line.substringAfter(':').trim().toLongOrNull()
                                if (line.startsWith("X-Camera-Facing:", true)) remoteFacingHeader =
                                    line.substringAfter(':').trim().toIntOrNull()
                                if (line.startsWith("X-Quality-Profile:", true)) remoteQualityHeader =
                                    line.substringAfter(':').trim().toIntOrNull()
                                if (line.startsWith("X-FPS-Target:", true)) remoteFpsHeader =
                                    line.substringAfter(':').trim().toIntOrNull()
                                if (line.startsWith("X-Eco:", true)) remoteEcoHeader = when (line.substringAfter(':').trim()) {
                                    "1" -> true
                                    "0" -> false
                                    else -> null
                                }
                                if (line.startsWith("X-Recording:", true))
                                    remoteRecordingHeader = when (line.substringAfter(':').trim()) {
                                        "1" -> true
                                        "0" -> false
                                        else -> null
                                    }
                            }
                            if (!jpeg || length !in 1..1_048_576) throw IOException("Quadro JPEG inválido")
                            val bytes = ByteArray(length)
                            var pos = 0
                            while (pos < length) {
                                val n = input.read(bytes, pos, length - pos)
                                if (n <= 0) throw IOException("Quadro incompleto")
                                pos += n
                            }
                            val lag = PhoneDeliveryDelay.estimateMs(SystemClock.elapsedRealtime(), frameAtMs, offset)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
                                ?: throw IOException("JPEG inválido")
                            val ratio = kotlin.math.max(bitmap.width, 1).toFloat() / kotlin.math.max(bitmap.height, 1)
                            // Android PiP aceita razões limitadas. O padrão é 16:9 em caso de valor atípico.
                            if (ratio in 0.43f..2.30f &&
                                (bitmap.width != lastFrameWidth || bitmap.height != lastFrameHeight)) {
                                imageAspect = Rational(bitmap.width, bitmap.height)
                                lastFrameWidth = bitmap.width
                                lastFrameHeight = bitmap.height
                            }
                            val telemetry = if (batteryLevel != null || batteryTemp != null || batteryCharging != null || batteryAvgMa != null)
                                PhoneBatteryReading(batteryLevel, batteryTemp, batteryCharging, batteryAvgMa) else null
                            frameOnDeck.getAndSet(PendingFrame(bitmap, lag, telemetry, remoteRecordingHeader, remoteEcoHeader,
                                remoteSecondsHeader, remoteFacingHeader, remoteQualityHeader, remoteFpsHeader, bytes))?.bitmap?.recycle()
                            schedulePresentation(session)
                        }
                    }
                    if (active && attempts == session) throw IOException("Transmissão encerrada")
                } catch (_: InvalidTokenException) {
                    runOnUiThread {
                        if (active && attempts == session) {
                            status.setText(R.string.phone_access_denied)
                            performance.setText(R.string.phone_performance_waiting)
                            applyTelemetry(null)
                        }
                    }
                    break
                } catch (_: Exception) {
                    if (!active || attempts != session || Thread.currentThread().isInterrupted) break
                    failures++
                    val waitMs = PhoneReconnectPolicy.delayMs(failures)
                    runOnUiThread {
                        if (active && attempts == session) {
                            status.setText(R.string.phone_reconnecting)
                            performance.setText(R.string.phone_performance_waiting)
                            displayedFirstFrame = false
                            lastDeliveryMs = null
                            fps.reset(SystemClock.elapsedRealtime())
                            // A imagem anterior não pode parecer um sinal ao vivo após queda do Wi-Fi.
                            image.setImageDrawable(null)
                            latestPhotoBytes = null
                            onlineDot.alpha = 0.3f
                            lastBattery = null
                            lastTelemetryText = null
                            applyTelemetry(null)
                        }
                    }
                    try { Thread.sleep(waitMs) } catch (_: InterruptedException) { break }
                } finally {
                    if (connection === ownConnection) connection = null
                    ownConnection?.disconnect()
                }
            }
        }, "mjpeg-viewer").apply { isDaemon = true; start() }
    }

    private fun schedulePresentation(session: Int) {
        if (!uiScheduled.compareAndSet(false, true)) return
        image.post {
            val newest = frameOnDeck.getAndSet(null)
            if (newest != null) {
                if (active && attempts == session) {
                    image.setImageBitmap(newest.bitmap)
                    latestPhotoBytes = newest.jpeg
                    if (lastFrameWidth > 0 && lastFrameHeight > 0) {
                        resolutionLabel.text = "${lastFrameWidth} × ${lastFrameHeight}"
                    }
                    if (!displayedFirstFrame) {
                        status.setText(R.string.phone_receiving)
                        displayedFirstFrame = true
                        onlineDot.alpha = 1f
                    }
                    if (newest.deliveryMs != null) lastDeliveryMs = newest.deliveryMs
                    lastBattery = newest.battery ?: lastBattery
                    remoteRecording = newest.remoteRecording ?: remoteRecording
                    remoteEco = newest.remoteEco ?: remoteEco
                    remoteFacing = newest.cameraFacing ?: remoteFacing
                    remoteQuality = newest.qualityProfile ?: remoteQuality
                    remoteFps = newest.targetFps ?: remoteFps
                    if (remoteFacing != null) facingLabel.setText(
                        if (remoteFacing == 1) R.string.phone_front_camera else R.string.phone_back_camera)
                    if (remoteRecording == true && newest.recordingSeconds != null) {
                        val seconds = newest.recordingSeconds.coerceAtLeast(0)
                        recordingClock.text = String.format(Locale.US, "● REC %02d:%02d:%02d",
                            seconds / 3600, seconds / 60 % 60, seconds % 60)
                    }
                    refreshRecordingUi()
                    updateEcoUi()
                    // Evita invalidar TextViews em cada JPEG (~11 FPS); atualização só quando muda.
                    applyTelemetry(lastBattery)
                    fps.frame()
                    val measured = fps.sample(SystemClock.elapsedRealtime())
                    if (measured != null) {
                        val lag = lastDeliveryMs?.let { getString(R.string.phone_delay_estimate, it) }
                            ?: getString(R.string.phone_delay_unavailable)
                        performance.text = getString(R.string.phone_performance_values, measured, lag)
                        lastShownFps = measured
                        fpsLabel.text = getString(R.string.viewer_fps_value, measured)
                    }
                } else newest.bitmap.recycle()
            }
            uiScheduled.set(false)
            if (frameOnDeck.get() != null && active && attempts == session) schedulePresentation(session)
        }
    }

    private fun applyTelemetry(reading: PhoneBatteryReading?) {
        val pill = if (showTelemetry && showBatteryLevel) reading?.levelPercent?.let {
            "${if (showChargeSymbol && reading.charging == true) "⚡ " else "🔋 "}$it%"
        } else null
        batteryPill.visibility = if (pill == null || isPipLayout) View.GONE else View.VISIBLE
        if (pill != null) batteryPill.text = pill
        val degrees = if (showTelemetry && showTemperature) reading?.temperatureTenthsC?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.1f°C", it / 10.0)
        } else null
        temperatureLabel.visibility = if (degrees == null || isPipLayout) View.GONE else View.VISIBLE
        if (degrees != null) temperatureLabel.text = degrees

        // Somente a pequena janela PiP mantém telemetria por cima do vídeo.
        val pipText = if (showTelemetry && isPipLayout) reading?.compactLabel(showBatteryLevel, showTemperature, showChargeSymbol) else null
        telemetry.visibility = if (pipText.isNullOrBlank()) View.GONE else View.VISIBLE
        if (!pipText.isNullOrBlank()) telemetry.text = pipText

        val fullDetails = if (showTelemetry) reading?.detailsLabel() else null
        val visible = detailsVisible && !focusMode && !isPipLayout && fullDetails != null
        telemetryDetails.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) telemetryDetails.text = fullDetails
        lastTelemetryText = pipText
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (sb.length <= 4096) {
            val ch = input.read()
            if (ch == -1) return null
            if (ch == '\n'.code) return sb.toString().trimEnd('\r')
            if (ch !in 32..126 && ch != '\r'.code) return null
            sb.append(ch.toChar())
        }
        return null
    }

    private fun stop() {
        active = false
        attempts++
        frameOnDeck.getAndSet(null)?.bitmap?.recycle()
        uiScheduled.set(false)
        try { connection?.disconnect() } catch (_: Exception) { }
        connection = null
        reader?.interrupt()
        reader = null
        image.setImageDrawable(null)
        latestPhotoBytes = null
        onlineDot.alpha = 0.3f
    }
}
