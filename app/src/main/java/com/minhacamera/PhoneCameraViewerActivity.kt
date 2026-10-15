package com.minhacamera

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Rational
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
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
        val battery: PhoneBatteryReading?
    )
    private class InvalidTokenException : IOException()

    private lateinit var pipHost: FrameLayout
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
    private var lastBattery: PhoneBatteryReading? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_phone_viewer)
        title = intent.getStringExtra("name") ?: getString(R.string.phone_camera)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        findViewById<View>(R.id.phoneRetry).setOnClickListener { stop(); start() }
        findViewById<View>(R.id.phoneViewerFit).setOnClickListener { toggleFitMode() }
        findViewById<View>(R.id.phoneViewerMirror).setOnClickListener { toggleMirrorMode() }
        findViewById<View>(R.id.phoneViewerFocus).setOnClickListener { toggleFocusMode() }
        previewContainer.setOnClickListener { toggleFocusMode() }
        findViewById<View>(R.id.phoneViewerMore).setOnClickListener { openMoreDialog() }
        refreshFitModeUi()
        refreshMirrorModeUi()
        refreshFocusModeUi()
        applyTelemetry(null)
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
            normalPreviewParent.addView(previewContainer, previewIndex)
            scrollContent.visibility = View.VISIBLE
            supportActionBar?.show()
            refreshFocusModeUi()
        }
        telemetryDetails.visibility = if (pip) View.GONE else telemetryDetails.visibility
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
        fitIcon.setImageResource(if (fillMode) R.drawable.ic_fullscreen else R.drawable.ic_fit)
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
        focusMode = !focusMode
        refreshFocusModeUi()
        if (focusMode) Toast.makeText(this, R.string.phone_view_focus_hint, Toast.LENGTH_SHORT).show()
    }

    private fun refreshFocusModeUi() {
        controls.visibility = if (focusMode) View.GONE else View.VISIBLE
        receiverInstructions.visibility = if (focusMode) View.GONE else View.VISIBLE
        performance.visibility = if (focusMode) View.GONE else View.VISIBLE
        val lp = previewContainer.layoutParams
        lp.height = if (focusMode) (resources.displayMetrics.heightPixels * 0.66f).toInt()
            else (240f * resources.displayMetrics.density + 0.5f).toInt()
        previewContainer.layoutParams = lp
        applyTelemetry(lastBattery)
    }

    private fun openMoreDialog() {
        val items = arrayOf(
            getString(R.string.pip_window),
            getString(if (showTelemetry) R.string.phone_hide_battery_overlay else R.string.phone_show_battery_overlay),
            getString(R.string.tentar_novamente),
            getString(if (focusMode) R.string.phone_view_exit_focus else R.string.phone_view_focus)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.ui_more)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> enterPip()
                    1 -> {
                        showTelemetry = !showTelemetry
                        applyTelemetry(lastBattery)
                    }
                    2 -> { stop(); start() }
                    3 -> toggleFocusMode()
                }
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
        lastTelemetryText = null
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
                            frameOnDeck.getAndSet(PendingFrame(bitmap, lag, telemetry))?.bitmap?.recycle()
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
                    if (!displayedFirstFrame) {
                        status.setText(R.string.phone_receiving)
                        displayedFirstFrame = true
                    }
                    if (newest.deliveryMs != null) lastDeliveryMs = newest.deliveryMs
                    lastBattery = newest.battery ?: lastBattery
                    // Evita invalidar TextViews em cada JPEG (~11 FPS); atualização só quando muda.
                    applyTelemetry(lastBattery)
                    fps.frame()
                    val measured = fps.sample(SystemClock.elapsedRealtime())
                    if (measured != null) {
                        val lag = lastDeliveryMs?.let { getString(R.string.phone_delay_estimate, it) }
                            ?: getString(R.string.phone_delay_unavailable)
                        performance.text = getString(R.string.phone_performance_values, measured, lag)
                    }
                } else newest.bitmap.recycle()
            }
            uiScheduled.set(false)
            if (frameOnDeck.get() != null && active && attempts == session) schedulePresentation(session)
        }
    }

    private fun applyTelemetry(reading: PhoneBatteryReading?) {
        if (!showTelemetry || reading == null) {
            telemetry.visibility = View.GONE
            telemetryDetails.visibility = View.GONE
            lastTelemetryText = null
            return
        }
        val label = reading.overlayLabel()
        if (lastTelemetryText != label) {
            telemetry.text = label
            telemetryDetails.text = reading.detailsLabel()
            lastTelemetryText = label
        }
        telemetry.visibility = View.VISIBLE
        telemetryDetails.visibility = if (focusMode) View.GONE else View.VISIBLE
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
    }
}
