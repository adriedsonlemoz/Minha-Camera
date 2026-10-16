package com.minhacamera

import android.content.Intent
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
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import android.view.View
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.GridLayout
import android.widget.SeekBar
import android.widget.Button
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.BufferedInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
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
    private lateinit var codecButton: TextView
    private lateinit var remoteClock: TextView
    private var viewerCodec = "mjpeg"
    private var vlcCore: LibVLC? = null
    private var vlcPlayer: VlcMediaPlayer? = null
    private var vlcVideo: VLCVideoLayout? = null
    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusPollPending = false
    private var h264StartedAt = 0L
    private var lastH264Resolution: String? = null
    private val codecStatusTick = object : Runnable {
        override fun run() {
            if (!active || viewerCodec != "h264") return
            if (!statusPollPending) {
                statusPollPending = true
                Thread({
                    val snapshot = probeStatus()
                    runOnUiThread {
                        statusPollPending = false
                        if (!active || viewerCodec != "h264") return@runOnUiThread
                        if (snapshot.codec == "mjpeg") { stop(); start(); return@runOnUiThread }
                        val resolution = snapshot.resolution
                        if (resolution != null && lastH264Resolution != null &&
                            resolution != lastH264Resolution) {
                            PhoneStreamDiagnostics.add("Resolução H264 alterada; reconectando decoder")
                            stop(); start(); return@runOnUiThread
                        }
                        if (resolution != null) lastH264Resolution = resolution
                        applyRemoteStatus(snapshot)
                    }
                }, "h264-telemetry").apply { isDaemon = true; start() }
            }
            statusHandler.postDelayed(this, 1500)
        }
    }
    private data class RemoteStatus(val codec: String?, val transmissionSeconds: Long?,
        val recordSeconds: Long?, val recording: Boolean?, val battery: Int?, val temp: Int?,
        val charging: Boolean?, val eco: Boolean?, val quality: Int?, val fps: Int?, val facing: Int?,
        val resolution: String? = null,
        val zoomPercent: Int? = null, val zoomRatio100: Int? = null)
    private lateinit var batteryPill: TextView
    private lateinit var temperatureLabel: TextView
    private lateinit var resolutionLabel: TextView
    private lateinit var fpsLabel: TextView
    private lateinit var ecoLabel: TextView
    private lateinit var recordLabel: TextView
    private lateinit var recordIcon: ImageView
    private lateinit var onlineDot: View
    private lateinit var remoteSection: View
    private var lastShownFps: Int? = null
    private var remoteEco: Boolean? = null
    /** Correção manual local, sem recodificar os quadros transmitidos. */
    private var rotationDegrees = 0
    private var diagnosticToExport = ""
    private val diagnosticExporter = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val successful = runCatching {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(diagnosticToExport.toByteArray(Charsets.UTF_8))
                } ?: error("Destino de exportação indisponível")
            }.isSuccess
            Toast.makeText(this, if (successful) "Diagnóstico exportado" else
                "Não foi possível exportar o diagnóstico", Toast.LENGTH_LONG).show()
        }
    }
    @Volatile private var latestPhotoBytes: ByteArray? = null
    private lateinit var fullscreenHost: FrameLayout
    private lateinit var fullControls: View
    private lateinit var recordingClock: TextView
    private lateinit var facingLabel: TextView
    private var remoteFacing: Int? = null
    private var remoteQuality: Int? = null
    private var remoteFps: Int? = null
    private var remoteZoomPercent: Int? = null
    private var remoteZoomRatio100: Int? = null
    private lateinit var remoteZoomLabel: TextView
    private lateinit var scrollContent: ScrollView
    private lateinit var normalPreviewParent: LinearLayout
    private var previewIndex = 1
    private var isPipLayout = false
    @Volatile private var imageAspect = Rational(16, 9)
    private var lastFrameWidth = 0
    private var lastFrameHeight = 0
    private lateinit var image: PhoneViewerImageView
    private lateinit var status: TextView
    private lateinit var performance: TextView
    private lateinit var telemetry: TextView
    private lateinit var telemetryDetails: TextView
    private lateinit var fitLabel: TextView
    private lateinit var fitIcon: ImageView
    private lateinit var mirrorLabel: TextView
    private lateinit var previewContainer: View
    private lateinit var controls: GridLayout
    private var currentControlColumns = 0
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
    private var focusManuallyRequested = false
    private var viewerControlsLocked = false
    private lateinit var viewerUnlock: View

    // A tela cheia automática acompanha a orientação; saída manual é respeitada
    // até que o telefone seja novamente girado (evita reentradas inesperadas).
    private var automaticFullscreenOrientation: Int? = null
    private var dismissedFullscreenOrientation: Int? = null
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
        // Respeitar rotação do Android; o usuário pode assistir casa ou retrovisor em paisagem.
        applyViewerOrientation()
        setContentView(R.layout.activity_phone_viewer)
        title = intent.getStringExtra("name") ?: getString(R.string.phone_camera)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // Cabeçalho da câmera obtido do cadastro ou do endereço, nunca inventa o modelo.
        findViewById<TextView>(R.id.viewerCameraName).text = title
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        codecButton = findViewById(R.id.viewerCodecTop)
        remoteClock = findViewById(R.id.viewerTransmissionClock)
        codecButton.setOnClickListener { chooseRemoteCodec() }
        findViewById<View>(R.id.viewerDiagnosticTop).setOnClickListener { showRemoteDiagnostic() }
        batteryPill = findViewById(R.id.viewerBatteryPill)
        temperatureLabel = findViewById(R.id.viewerTemperature)
        resolutionLabel = findViewById(R.id.viewerRemoteQualityValue)
        fpsLabel = findViewById(R.id.viewerRemoteFpsValue)
        remoteZoomLabel = findViewById(R.id.viewerRemoteZoomValue)
        ecoLabel = findViewById(R.id.viewerRemoteEcoValue)
        recordingClock = findViewById(R.id.viewerRecordingClock)
        facingLabel = findViewById(R.id.viewerRemoteFacingValue)
        fullscreenHost = findViewById(R.id.phoneViewerFullscreenHost)
        fullControls = findViewById(R.id.viewerFullscreenControls)
        viewerUnlock = findViewById(R.id.viewerFullUnlock)
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
        PhoneCameraUrls.normalize(intent.getStringExtra("url").orEmpty())?.let { savedUrl ->
            getSharedPreferences("phone_viewer", MODE_PRIVATE).edit().putString("last_url", savedUrl).apply()
        }
        val settings = getSharedPreferences("phone_viewer", MODE_PRIVATE)
        fillMode = settings.getBoolean("fill", false)
        mirrorMode = settings.getBoolean("mirror", false)
        rotationDegrees = PhoneViewerImageView.normalizeDegrees(settings.getInt("rotation_degrees", 0))
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
            if (focusMode && !viewerControlsLocked)
                fullControls.visibility = if (fullControls.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
            else if (!focusMode) toggleFocusMode()
        }
        findViewById<View>(R.id.viewerPipAction).setOnClickListener { enterPip() }
        findViewById<View>(R.id.viewerRotateAction).setOnClickListener { rotateVideo() }
        findViewById<View>(R.id.viewerSnapshotAction).setOnClickListener { requestRemotePhoto() }
        findViewById<View>(R.id.viewerRemoteQuality).setOnClickListener { chooseRemoteQuality() }
        findViewById<View>(R.id.viewerRemoteFps).setOnClickListener { chooseRemoteFps() }
        findViewById<View>(R.id.viewerRemoteZoom).setOnClickListener { chooseRemoteZoom() }
        findViewById<View>(R.id.viewerFullZoom).setOnClickListener { chooseRemoteZoom() }
        findViewById<View>(R.id.viewerRemoteFacing).setOnClickListener { chooseRemoteFacing() }
        findViewById<View>(R.id.viewerFullBack).setOnClickListener { if (focusMode) toggleFocusMode() }
        findViewById<View>(R.id.viewerFullRecord).setOnClickListener { setRemoteRecording(remoteRecording != true) }
        findViewById<View>(R.id.viewerFullPhoto).setOnClickListener { requestRemotePhoto() }
        findViewById<View>(R.id.viewerFullRotate).setOnClickListener { rotateVideo() }
        findViewById<View>(R.id.viewerFullFit).setOnClickListener { toggleFitMode() }
        findViewById<View>(R.id.viewerFullMirror).setOnClickListener { toggleMirrorMode() }
        findViewById<View>(R.id.viewerFullSettings).setOnClickListener { chooseViewerOrientation() }
        findViewById<View>(R.id.viewerFullLock).setOnClickListener { setViewerLocked(true) }
        val unlockRunnable = Runnable { setViewerLocked(false) }
        viewerUnlock.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { statusHandler.postDelayed(unlockRunnable, 2000L); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    statusHandler.removeCallbacks(unlockRunnable); true
                }
                else -> true
            }
        }
        findViewById<View>(R.id.viewerRecordAction).setOnClickListener { setRemoteRecording(remoteRecording != true) }
        findViewById<View>(R.id.viewerRemoteEco).setOnClickListener {
            if (remoteEco == null) {
                Toast.makeText(this, R.string.viewer_eco_unavailable, Toast.LENGTH_LONG).show()
            } else setRemoteEco(remoteEco != true)
        }
        // Todos os comandos antes ocultos no menu Mais agora têm card próprio.
        findViewById<View>(R.id.phoneViewerMore).setOnClickListener { chooseViewerOrientation() }
        findViewById<View>(R.id.viewerBatteryAction).setOnClickListener { toggleVideoBatteryOverlay() }
        findViewById<View>(R.id.viewerIndicatorsAction).setOnClickListener { openTelemetryOptions() }
        findViewById<View>(R.id.viewerLibraryAction).setOnClickListener {
            startActivity(Intent(this, RemoteMultimediaActivity::class.java)
                .putExtra("url", intent.getStringExtra("url")))
        }
        findViewById<View>(R.id.viewerDetailsAction).setOnClickListener { toggleTechnicalDetails() }
        findViewById<View>(R.id.viewerCodecAction).setOnClickListener { chooseRemoteCodec() }
        findViewById<View>(R.id.viewerDiagnosticsAction).setOnClickListener { showRemoteDiagnostic() }
        // Mantém cinco colunas nas telas usuais e reduz em dispositivos muito estreitos.
        controls.post { adaptControlColumns() }
        refreshPreviewOrientation()
        refreshFitModeUi()
        refreshMirrorModeUi()
        updateVideoRotation()
        refreshFocusModeUi()
        syncLandscapeFullscreen()
        applyTelemetry(null)
        refreshRecordingUi()
        updateEcoUi()
        refreshQuickControlStates()
    }

    /** O receptor pode seguir o Android, ficar vertical ou horizontal fixo. */
    private fun applyViewerOrientation() {
        requestedOrientation = when (getSharedPreferences("phone_viewer", MODE_PRIVATE)
            .getString("orientation_mode", PhoneOrientationMode.AUTO)) {
            PhoneOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PhoneOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun chooseViewerOrientation() {
        val modes = arrayOf(PhoneOrientationMode.AUTO, PhoneOrientationMode.PORTRAIT,
            PhoneOrientationMode.LANDSCAPE)
        val labels = arrayOf(getString(R.string.phone_viewer_orientation_auto),
            getString(R.string.phone_viewer_orientation_portrait),
            getString(R.string.phone_viewer_orientation_landscape))
        val prefs = getSharedPreferences("phone_viewer", MODE_PRIVATE)
        PhoneViewerChoiceDialog.show(this, getString(R.string.phone_viewer_orientation_title),
            "Defina a orientação da tela do receptor", labels,
            intArrayOf(R.drawable.ic_ui_screen, R.drawable.ic_ui_smartphone, R.drawable.ic_ui_expand),
            modes.indexOf(prefs.getString("orientation_mode", PhoneOrientationMode.AUTO)).coerceAtLeast(0)) { index ->
            prefs.edit().putString("orientation_mode", modes[index]).apply()
            dismissedFullscreenOrientation = null
            applyViewerOrientation()
            syncLandscapeFullscreen()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        focusManuallyRequested = false
        refreshPreviewOrientation()
        if (::controls.isInitialized) controls.post { adaptControlColumns() }
        syncLandscapeFullscreen()
        if (::previewContainer.isInitialized) previewContainer.post { updateVideoRotation() }
    }

    private fun refreshPreviewOrientation() {
        if (!::previewContainer.isInitialized || isPipLayout || focusMode) return
        // Tela horizontal: ampliar a área do vídeo sem distorcer o FIT_CENTER.
        val heightDp = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 210 else 290
        val layout = previewContainer.layoutParams
        layout.height = (heightDp * resources.displayMetrics.density + 0.5f).toInt()
        previewContainer.layoutParams = layout
    }

    // onPause acontece ao entrar no PiP: manter a leitura MJPEG enquanto a janela está visível.
    override fun onResume() {
        super.onResume()
        if (!active) start()
        syncLandscapeFullscreen()
    }
    override fun onStop() { if (!(android.os.Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode)) stop(); super.onStop() }
    override fun onDestroy() { stop(); super.onDestroy() }

    override fun onPictureInPictureModeChanged(inPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig)
        switchPipLayout(inPictureInPictureMode)
        if (!inPictureInPictureMode) {
            dismissedFullscreenOrientation = null
            syncLandscapeFullscreen()
        }
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
        updateVideoRotation()
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
        updateVideoRotation()
        val actionLabel = if (mirrorMode) R.string.phone_view_unmirror else R.string.phone_view_mirror
        mirrorLabel.setText(actionLabel)
        findViewById<View>(R.id.phoneViewerMirror).contentDescription = getString(actionLabel)
    }

    private fun isAutomaticViewerRotation(): Boolean =
        PhoneOrientationMode.normalize(getSharedPreferences("phone_viewer", MODE_PRIVATE)
            .getString("orientation_mode", PhoneOrientationMode.AUTO)) == PhoneOrientationMode.AUTO

    /** Auto fullscreen somente na horizontal, mantendo entrada manual independente. */
    private fun syncLandscapeFullscreen() {
        if (!::fullscreenHost.isInitialized || isPipLayout) return
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!landscape) {
            dismissedFullscreenOrientation = null
            automaticFullscreenOrientation = null
            if (focusMode && !focusManuallyRequested) {
                focusMode = false
                setViewerLocked(false)
                refreshFocusModeUi()
            }
            return
        }
        if (automaticFullscreenOrientation != Configuration.ORIENTATION_LANDSCAPE) {
            automaticFullscreenOrientation = Configuration.ORIENTATION_LANDSCAPE
            dismissedFullscreenOrientation = null
        }
        if (dismissedFullscreenOrientation != Configuration.ORIENTATION_LANDSCAPE && !focusMode) {
            focusManuallyRequested = false
            focusMode = true
            refreshFocusModeUi()
        }
    }

    private fun setViewerLocked(locked: Boolean) {
        viewerControlsLocked = locked
        fullControls.visibility = if (locked) View.GONE else View.VISIBLE
        viewerUnlock.visibility = if (locked && focusMode) View.VISIBLE else View.GONE
    }

    private fun toggleFocusMode() {
        if (isPipLayout) return
        if (viewerControlsLocked) {
            Toast.makeText(this, "Segure o cadeado por 2 segundos", Toast.LENGTH_SHORT).show()
            return
        }
        if (focusMode) {
            dismissedFullscreenOrientation = resources.configuration.orientation
            focusManuallyRequested = false
        } else {
            dismissedFullscreenOrientation = null
            focusManuallyRequested = true
        }
        focusMode = !focusMode
        refreshFocusModeUi()
        if (focusMode) Toast.makeText(this, R.string.viewer_fullscreen_hint, Toast.LENGTH_SHORT).show()
    }

    private fun rotateVideo() {
        rotationDegrees = (rotationDegrees + 90) % 360
        getSharedPreferences("phone_viewer", MODE_PRIVATE).edit()
            .putInt("rotation_degrees", rotationDegrees).apply()
        updateVideoRotation()
        Toast.makeText(this, "Rotação: $rotationDegrees°", Toast.LENGTH_SHORT).show()
    }

    private fun updateVideoRotation() {
        image.setVideoTransform(rotationDegrees, mirrorMode, fillMode)
        if (lastFrameWidth > 0 && lastFrameHeight > 0) {
            val turns = rotationDegrees % 180 != 0
            imageAspect = if (turns) Rational(lastFrameHeight, lastFrameWidth)
                else Rational(lastFrameWidth, lastFrameHeight)
        }
        val video = vlcVideo ?: return
        video.rotation = rotationDegrees.toFloat()
        // A textura do VLC gira apenas no receptor, sem mudar o stream transmitido.
        video.post {
            if (vlcVideo !== video) return@post
            val hostWidth = previewContainer.width.toFloat()
            val hostHeight = previewContainer.height.toFloat()
            val quarterTurn = rotationDegrees % 180 != 0
            val scale = if (quarterTurn && hostWidth > 0f && hostHeight > 0f)
                minOf(hostWidth / hostHeight, hostHeight / hostWidth) else 1f
            video.scaleX = scale
            video.scaleY = scale
        }
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
            fullControls.visibility = if (viewerControlsLocked) View.GONE else View.VISIBLE
            viewerUnlock.visibility = if (viewerControlsLocked) View.VISIBLE else View.GONE
            supportActionBar?.hide()
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            setViewerLocked(false)
            fullscreenHost.visibility = View.GONE
            normalPreviewParent.addView(previewContainer, previewIndex,
                LinearLayout.LayoutParams(-1, (290f * resources.displayMetrics.density + 0.5f).toInt()))
            scrollContent.visibility = View.VISIBLE
            supportActionBar?.show()
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
        applyTelemetry(lastBattery)
        previewContainer.post { updateVideoRotation() }
    }

    @Deprecated("Usado apenas para sair do modo imersivo")
    override fun onBackPressed() {
        if (focusMode && viewerControlsLocked)
            Toast.makeText(this, "Segure o cadeado por 2 segundos", Toast.LENGTH_SHORT).show()
        else if (focusMode) toggleFocusMode() else super.onBackPressed()
    }

    /** Reorganiza os mesmos cards, sem remover controles quando o visor é estreito. */
    private fun adaptControlColumns() {
        val usableWidth = controls.width.takeIf { it > 0 } ?: return
        val widthDp = usableWidth / resources.displayMetrics.density
        val columns = when {
            widthDp < 248f -> 3
            widthDp < 318f -> 4
            else -> 5
        }
        if (currentControlColumns == columns) return
        // Definir primeiro a quantidade de colunas evita exceção na realocação das crianças.
        controls.columnCount = columns
        val margin = (2 * resources.displayMetrics.density + .5f).toInt()
        for (i in 0 until controls.childCount) {
            val tile = controls.getChildAt(i)
            val params = GridLayout.LayoutParams(GridLayout.spec(i / columns), GridLayout.spec(i % columns, 1f)).apply {
                width = 0
                height = (66 * resources.displayMetrics.density + .5f).toInt()
                setMargins(margin, margin, margin, margin)
                setGravity(android.view.Gravity.FILL_HORIZONTAL)
            }
            tile.layoutParams = params
        }
        currentControlColumns = columns
        controls.requestLayout()
    }

    private fun toggleVideoBatteryOverlay() {
        showTelemetry = !showTelemetry
        getSharedPreferences("phone_viewer", MODE_PRIVATE).edit()
            .putBoolean("telemetry_visible", showTelemetry).apply()
        applyTelemetry(lastBattery)
        refreshQuickControlStates()
    }

    private fun toggleTechnicalDetails() {
        detailsVisible = !detailsVisible
        getSharedPreferences("phone_viewer", MODE_PRIVATE).edit()
            .putBoolean("details_visible", detailsVisible).apply()
        applyTelemetry(lastBattery)
        refreshQuickControlStates()
    }

    private fun refreshQuickControlStates() {
        findViewById<View>(R.id.viewerBatteryAction).alpha = if (showTelemetry) 1f else .55f
        findViewById<View>(R.id.viewerDetailsAction).alpha = if (detailsVisible) 1f else .75f
        findViewById<View>(R.id.viewerBatteryAction).isSelected = showTelemetry
        findViewById<View>(R.id.viewerDetailsAction).isSelected = detailsVisible
    }

    /** Todos os comandos remotos usam POST e o token privado previamente validado. */
    private fun postRemote(endpoint: String?, notice: Int, onSuccess: () -> Unit = {}) {
        if (endpoint == null) {
            Toast.makeText(this, R.string.phone_invalid_url, Toast.LENGTH_SHORT).show()
            return
        }
        Thread({
            var reason: String? = null
            val ok = try {
                NetUtil.bindToWifi(applicationContext)
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 3500
                    conn.readTimeout = 3500
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("Content-Length", "0")
                    val status = conn.responseCode
                    reason = conn.getHeaderField("X-Control-Reason")
                    status == 204
                } finally { conn.disconnect() }
            } catch (_: Exception) { false }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (ok) onSuccess()
                    val message = if (ok) getString(notice) else when (reason) {
                        "recording-active" -> getString(R.string.remote_blocked_recording)
                        "unsupported-mode" -> getString(R.string.remote_unsupported_mode)
                        else -> getString(R.string.viewer_remote_command_failed)
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    if (ok) refreshRemoteStatusSoon()
                }
            }
        }, "camera-remote-command").apply { isDaemon = true; start() }
    }

    private fun refreshRemoteStatusSoon() {
        statusHandler.postDelayed({
            if (!isFinishing && !isDestroyed) Thread({
                val state = probeStatus()
                runOnUiThread { if (!isFinishing && !isDestroyed) applyRemoteStatus(state) }
            }, "remote-state-refresh").apply { isDaemon = true; start() }
        }, 950)
    }

    private fun chooseRemoteZoom() {
        val current = remoteZoomPercent
        if (current == null) {
            Toast.makeText(this, "Zoom remoto indisponível neste modo ou nesta câmera", Toast.LENGTH_LONG).show()
            return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val label = TextView(this).apply {
            textSize = 18f
            setTextColor(getColor(R.color.app_text))
            text = remoteZoomRatio100?.let { String.format(Locale.US, "Zoom da câmera: %.1f×", it/100f) }
                ?: "Zoom: $current%"
        }
        container.addView(label)
        val slider = SeekBar(this).apply {
            max = 100
            progress = current
            contentDescription = "Zoom digital remoto de 0 a 100%"
        }
        container.addView(slider)
        val description = TextView(this).apply {
            text = "0% é 1×. O máximo depende da câmera; não aumenta a resolução real."
            textSize = 13f
            setTextColor(getColor(R.color.ui_muted))
        }
        container.addView(description)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) label.text = "Zoom solicitado: $progress%"
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        AlertDialog.Builder(this)
            .setTitle("Zoom remoto")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("1×") { _, _ -> sendRemoteZoom(0) }
            .setPositiveButton("Aplicar") { _, _ -> sendRemoteZoom(slider.progress) }
            .show()
    }

    private fun sendRemoteZoom(percent: Int) {
        if (remoteZoomPercent == null) {
            Toast.makeText(this, "Zoom não disponível", Toast.LENGTH_SHORT).show()
            return
        }
        val endpoint = PhoneCameraUrls.zoomCommandUrl(intent.getStringExtra("url").orEmpty(),percent)
        postRemote(endpoint,
            R.string.viewer_remote_zoom_requested) {
            remoteZoomPercent = percent.coerceIn(0,100)
            remoteZoomLabel.text = "Atualizando…"
        }
    }

    private fun chooseRemoteCodec() {
        val options = arrayOf("MJPEG • compatibilidade", "H.264 • experimental")
        PhoneViewerChoiceDialog.show(this, getString(R.string.phone_codec_title),
            "MJPEG é mais compatível; H.264 continua experimental", options,
            intArrayOf(R.drawable.ic_ui_video, R.drawable.ic_ui_video),
            if (viewerCodec == "h264") 1 else 0) { index ->
            val next = if (index == 1) "h264" else "mjpeg"
            if (next != viewerCodec) postRemote(
                PhoneCameraUrls.codecCommandUrl(intent.getStringExtra("url").orEmpty(), next),
                R.string.phone_codec_changed) { stop(); start() }
        }
    }

    private fun showRemoteDiagnostic() {
        val url = PhoneCameraUrls.diagnosticUrl(intent.getStringExtra("url").orEmpty())
        if (url == null) return
        Thread({
            val report = try {
                NetUtil.bindToWifi(applicationContext)
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 2500; conn.readTimeout = 2500
                    if (conn.responseCode != 200) getString(R.string.phone_diagnostic_unavailable)
                    else conn.inputStream.bufferedReader().use { it.readText().take(16000) }
                } finally { conn.disconnect() }
            } catch (_: Exception) { getString(R.string.phone_diagnostic_unavailable) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                CameraReportDialog(this, getString(R.string.phone_diagnostic_title),
                    sanitizeDiagnostic(report), { safeReport ->
                        diagnosticToExport = sanitizeDiagnostic(safeReport)
                        diagnosticExporter.launch("Minha-Camera-diagnostico-transmissao.txt")
                    }, "Diagnóstico remoto • dados técnicos sem tokens ou senhas").show()
            }
        }, "remote-diagnostic").apply { isDaemon = true; start() }
    }

    /** Não exportar tokens de acesso à câmera nem credenciais de URLs. */
    private fun sanitizeDiagnostic(report: String): String = report
        .replace(Regex("(?i)(token|password|passwd|secret|apikey|api_key)=([^\\s&]+)"), "$1=[REDACTED]")
        .replace(Regex("(?i)(https?://)[^/@\\s]+@"), "$1[REDACTED]@")

    private fun probeStatus(): RemoteStatus {
        val url = PhoneCameraUrls.statusUrl(intent.getStringExtra("url").orEmpty()) ?: return RemoteStatus(null,null,null,null,null,null,null,null,null,null,null)
        return try {
            NetUtil.bindToWifi(applicationContext)
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 1800; conn.readTimeout = 1800; conn.useCaches = false
                if (conn.responseCode != 200) return RemoteStatus(null,null,null,null,null,null,null,null,null,null,null)
                fun int(name: String) = conn.getHeaderField(name)?.toIntOrNull()
                fun bool(name: String) = when (conn.getHeaderField(name)) { "1" -> true; "0" -> false; else -> null }
                RemoteStatus(conn.getHeaderField("X-Stream-Codec"),
                    conn.getHeaderField("X-Transmission-Seconds")?.toLongOrNull(),
                    conn.getHeaderField("X-Recording-Seconds")?.toLongOrNull(), bool("X-Recording"),
                    int("X-Battery-Level"),int("X-Battery-Temp-Tenths"),bool("X-Battery-Charging"),
                    bool("X-Eco"),int("X-Quality-Profile"),int("X-FPS-Target"),int("X-Camera-Facing"),
                    conn.getHeaderField("X-Resolution"),
                    if (bool("X-Zoom-Supported") == true) int("X-Zoom-Percent") else null,
                    if (bool("X-Zoom-Supported") == true) int("X-Zoom-Ratio-100") else null)
            } finally { conn.disconnect() }
        } catch (_: Exception) { RemoteStatus(null,null,null,null,null,null,null,null,null,null,null) }
    }

    private fun applyRemoteStatus(state: RemoteStatus) {
        state.transmissionSeconds?.let { seconds ->
            remoteClock.text = String.format(Locale.US, "AO VIVO %02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        }
        remoteRecording = state.recording ?: remoteRecording
        remoteEco = state.eco ?: remoteEco
        remoteQuality = state.quality ?: remoteQuality
        remoteFps = state.fps ?: remoteFps
        // A câmera pode não oferecer zoom (modo H.264, dupla ou aparelho sem suporte).
        if (state.codec != null) {
            remoteZoomPercent = state.zoomPercent
            remoteZoomRatio100 = state.zoomRatio100
            remoteZoomLabel.text = state.zoomRatio100?.let { String.format(Locale.US,"%.1f×",it/100f) } ?: "Indisponível"
        }
        remoteFacing = state.facing ?: remoteFacing
        state.recordSeconds?.let { seconds ->
            if (remoteRecording == true) recordingClock.text = String.format(Locale.US, "● REC %02d:%02d:%02d",
                seconds / 3600, seconds / 60 % 60, seconds % 60)
        }
        if (state.battery != null || state.temp != null || state.charging != null)
            lastBattery = PhoneBatteryReading(state.battery, state.temp, state.charging, null)
        applyTelemetry(lastBattery)
        refreshRecordingUi()
        updateEcoUi()
        if (state.resolution != null) resolutionLabel.text = state.resolution.replace('x', '×')
        else if (remoteQuality != null) resolutionLabel.text = "${remoteQuality}p"
        if (remoteFps != null) fpsLabel.text = "$remoteFps FPS"
        if (remoteFacing != null) facingLabel.text = if (remoteFacing == 1) "Frontal" else "Traseira"
    }

    private fun startH264(session: Int) {
        val url = PhoneCameraUrls.h264Url(intent.getStringExtra("url").orEmpty()) ?: return
        try {
            val parent = previewContainer as FrameLayout
            val view = VLCVideoLayout(this)
            parent.addView(view, 1, FrameLayout.LayoutParams(-1, -1))
            vlcVideo = view
            updateVideoRotation()
            image.visibility = View.GONE
            val core = LibVLC(this, arrayListOf("--network-caching=140", "--no-audio", "--stats"))
            vlcCore = core
            val player = VlcMediaPlayer(core)
            vlcPlayer = player
            player.attachViews(view, null, false, false)
            player.setEventListener { event ->
                runOnUiThread {
                    if (!active || attempts != session || vlcPlayer !== player) return@runOnUiThread
                    when (event.type) {
                        VlcMediaPlayer.Event.Vout -> {
                            onlineDot.alpha = 1f
                            status.setText(R.string.phone_receiving)
                            displayedFirstFrame = true
                        }
                        VlcMediaPlayer.Event.EncounteredError, VlcMediaPlayer.Event.EndReached -> {
                            status.setText(R.string.phone_h264_playback_failed)
                            onlineDot.alpha = 0.3f
                        }
                    }
                }
            }
            val media = Media(core, Uri.parse(url))
            media.addOption(":demux=h264")
            media.addOption(":network-caching=140")
            media.addOption(":live-caching=140")
            media.addOption(":no-audio")
            player.setMedia(media)
            media.release()
            player.play()
            viewerCodec = "h264"
            codecButton.text = "H.264"
            remoteClock.visibility = View.VISIBLE
            statusHandler.post(codecStatusTick)
        } catch (e: Exception) {
            PhoneStreamDiagnostics.add("Visualizador AVC indisponível: ${e.javaClass.simpleName}")
            status.setText(R.string.phone_h264_playback_failed)
            releaseVlc()
        }
    }

    private fun releaseVlc() {
        try { vlcPlayer?.stop() } catch (_: Exception) { }
        try { vlcPlayer?.detachViews() } catch (_: Exception) { }
        try { vlcPlayer?.release() } catch (_: Exception) { }
        vlcPlayer = null
        vlcVideo?.let { (it.parent as? FrameLayout)?.removeView(it) }
        vlcVideo = null
        try { vlcCore?.release() } catch (_: Exception) { }
        vlcCore = null
        image.visibility = View.VISIBLE
    }

    private fun requestRemotePhoto() {
        val url = PhoneCameraUrls.photoCommandUrl(intent.getStringExtra("url").orEmpty())
        postRemote(url, R.string.viewer_remote_photo_requested)
    }

    private fun chooseRemoteQuality() {
        val options = intArrayOf(360, 480, 720, 768, 1080)
        PhoneViewerChoiceDialog.show(this, getString(R.string.viewer_remote_quality_title),
            "A resolução solicitada depende da câmera transmissora",
            arrayOf("360p · economia", "480p · normal", "720p · HD",
                "1024×768 · 4:3", "1080p · quando suportado"),
            intArrayOf(R.drawable.ic_ui_leaf, R.drawable.ic_ui_video, R.drawable.ic_ui_video,
                R.drawable.ic_ui_screen, R.drawable.ic_ui_video),
            options.indexOf(remoteQuality ?: -1)) { index ->
            postRemote(PhoneCameraUrls.qualityCommandUrl(intent.getStringExtra("url").orEmpty(), options[index]),
                R.string.viewer_remote_quality_requested) { remoteQuality = options[index] }
        }
    }

    private fun chooseRemoteFps() {
        val options = intArrayOf(5, 10, 15, 24, 30)
        PhoneViewerChoiceDialog.show(this, getString(R.string.viewer_remote_fps_title),
            "Meta de quadros, não garantia de FPS efetivos",
            options.map { "$it FPS" }.toTypedArray(),
            IntArray(options.size) { R.drawable.ic_ui_video }, options.indexOf(remoteFps ?: -1)) { index ->
            postRemote(PhoneCameraUrls.fpsCommandUrl(intent.getStringExtra("url").orEmpty(), options[index]),
                R.string.viewer_remote_fps_updated) { remoteFps = options[index] }
        }
    }

    private fun chooseRemoteFacing() {
        PhoneViewerChoiceDialog.show(this, getString(R.string.viewer_remote_facing_title),
            "Troca a lente utilizada no Redmi A3",
            arrayOf(getString(R.string.phone_back_camera), getString(R.string.phone_front_camera)),
            intArrayOf(R.drawable.ic_ui_camera, R.drawable.ic_ui_smartphone), remoteFacing ?: -1) { index ->
            postRemote(PhoneCameraUrls.facingCommandUrl(intent.getStringExtra("url").orEmpty(), index == 1),
                R.string.viewer_remote_facing_requested)
        }
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

    /** Eco funciona no serviço e no modo de transmissão com a tela ligada. */
    private fun setRemoteEco(enabled: Boolean) {
        postRemote(PhoneCameraUrls.ecoCommandUrl(intent.getStringExtra("url").orEmpty(), enabled),
            R.string.viewer_eco_updated) { remoteEco = enabled; updateEcoUi() }
    }

    private fun updateEcoUi() {
        ecoLabel.text = when (remoteEco) { true -> "Ligado"; false -> "Desligado"; null -> "--" }
        findViewById<View>(R.id.viewerRemoteEco).alpha = if (remoteEco == null) .65f else 1f
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
        viewerCodec = "mjpeg"
        codecButton.text = "MJPEG"
        remoteClock.text = getString(R.string.phone_clock_idle)
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
                val state = probeStatus()
                if (state.codec == "h264") {
                    runOnUiThread { if (active && attempts == session) {
                        lastH264Resolution = state.resolution
                        applyRemoteStatus(state); startH264(session)
                    } }
                    return@Thread
                }
                if (state.codec == "mjpeg") runOnUiThread { if (active && attempts == session) applyRemoteStatus(state) }
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
                            var streamSecondsHeader: Long? = null
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
                                if (line.startsWith("X-Transmission-Seconds:", true)) streamSecondsHeader =
                                    line.substringAfter(':').trim().toLongOrNull()
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
                            if (!jpeg || length !in 1..2_500_000) throw IOException("Quadro JPEG inválido")
                            val bytes = ByteArray(length)
                            var pos = 0
                            while (pos < length) {
                                val n = input.read(bytes, pos, length - pos)
                                if (n <= 0) throw IOException("Quadro incompleto")
                                pos += n
                            }
                            if (streamSecondsHeader != null) {
                                val seconds = streamSecondsHeader
                                runOnUiThread { if (active && attempts == session) remoteClock.text =
                                    String.format(Locale.US, "AO VIVO %02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60) }
                            }
                            val lag = PhoneDeliveryDelay.estimateMs(SystemClock.elapsedRealtime(), frameAtMs, offset)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
                                ?: throw IOException("JPEG inválido")
                            val ratio = kotlin.math.max(bitmap.width, 1).toFloat() / kotlin.math.max(bitmap.height, 1)
                            // Android PiP aceita razões limitadas. O padrão é 16:9 em caso de valor atípico.
                            if (ratio in 0.43f..2.30f &&
                                (bitmap.width != lastFrameWidth || bitmap.height != lastFrameHeight)) {
                                imageAspect = if (rotationDegrees % 180 != 0)
                                    Rational(bitmap.height, bitmap.width) else
                                    Rational(bitmap.width, bitmap.height)
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
                    image.refreshTransform()
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
                    if (remoteFacing != null) facingLabel.text = if (remoteFacing == 1) "Frontal" else "Traseira"
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
        statusHandler.removeCallbacks(codecStatusTick)
        statusPollPending = false
        releaseVlc()
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
