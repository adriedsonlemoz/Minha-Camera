package com.minhacamera

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captura MJPEG independente da Activity. O usuário ativa este serviço com a tela visível,
 * antes de bloquear o aparelho. START_NOT_STICKY evita reabrir a câmera sem autorização
 * em um processo restaurado em segundo plano (restrição Android 14+).
 *
 * Uma câmera por vez. Nenhuma dependência da TextureView nem de instâncias de Activity.
 */
@Suppress("DEPRECATION")
class PhoneCameraBackgroundService : Service() {
    companion object {
        private const val CHANNEL = "phone-camera-background-v1"
        private const val NOTIFICATION_ID = 1047
        const val ACTION_START = "com.minhacamera.BACKGROUND_START"
        const val ACTION_STOP = "com.minhacamera.BACKGROUND_STOP"
        const val ACTION_RECORD = "com.minhacamera.BACKGROUND_RECORD"
        const val EXTRA_FACING = "facing"

        @Volatile var running = false
            private set
        @Volatile var recording = false
            private set
        @Volatile var streamUrl: String? = null
            private set
        @Volatile var latestJpeg: ByteArray? = null
            private set
        @Volatile internal var latestBattery: PhoneBatteryReading? = null
            private set
        @Volatile var lastError: String? = null
            private set

        fun command(context: Context, action: String, facing: Int = Camera.CameraInfo.CAMERA_FACING_BACK) {
            val intent = Intent(context, PhoneCameraBackgroundService::class.java).apply {
                this.action = action
                putExtra(EXTRA_FACING, facing)
            }
            if (action == ACTION_START) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val encodeWorker = Executors.newSingleThreadExecutor { r -> Thread(r, "phone-background-mjpeg") }
    private val recordWorker = Executors.newSingleThreadExecutor { r -> Thread(r, "phone-background-mp4") }
    private val encoding = AtomicBoolean(false)
    private val recordingBusy = AtomicBoolean(false)
    private val battery by lazy { PhoneBatteryTelemetry(applicationContext) }
    private var camera: Camera? = null
    private var headlessPreview: PhoneHeadlessPreview? = null
    private var selectedFacing = Camera.CameraInfo.CAMERA_FACING_BACK
    @Volatile private var requestedQuality = 480
    @Volatile private var fpsLimit = 10
    @Volatile private var switching = false
    @Volatile private var takingPhoto = false
    @Volatile private var recordingStartMs = 0L
    @Volatile private var server: PhoneCameraServer? = null
    private var recorder: PhoneMp4Recorder? = null // acessado só pelo recordWorker
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastCapture = 0L
    @Volatile private var lastPublishedAtMs = 0L
    private var watchdogRestarts = 0
    private val frameWatchdog = object : Runnable {
        override fun run() {
            if (!running || closing) return
            val age = SystemClock.elapsedRealtime() - lastPublishedAtMs
            if (age > 12_000 && !switching && !takingPhoto && !recording) {
                if (watchdogRestarts >= 3) {
                    lastError = "A câmera parou de enviar quadros após tentativas de recuperação"
                    stopCaptureAndFinish()
                    return
                }
                watchdogRestarts++
                server?.let { reconfigure(it, selectedFacing, requestedQuality) }
            }
            mainHandler.postDelayed(this, 6000)
        }
    }
    private var heatState = PhoneHeatState.NORMAL
    private var lastBatteryReadAt = 0L
    private var cachedBattery: PhoneBatteryReading? = null
    @Volatile private var ecoEnabled = false
    private var closing = false
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.phone_background_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCaptureAndFinish()
            ACTION_RECORD -> if (running && !closing) toggleRecording()
            ACTION_START -> {
                if (running || closing) return START_NOT_STICKY
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    lastError = getString(R.string.phone_permission_denied)
                    stopSelf()
                    return START_NOT_STICKY
                }
                // startForeground deve ocorrer enquanto a Activity solicitante ainda está visível.
                try {
                    lastError = null
                    startCameraForegroundNotification()
                    foregroundStarted = true
                    ecoEnabled = getSharedPreferences("phone_video", MODE_PRIVATE).getBoolean("eco_enabled", false)
                    requestedQuality = getSharedPreferences("phone_video", MODE_PRIVATE).getInt("remote_quality", 480)
                    fpsLimit = getSharedPreferences("phone_video", MODE_PRIVATE).getInt("remote_fps", 10)
                    val facing = intent.getIntExtra(EXTRA_FACING, Camera.CameraInfo.CAMERA_FACING_BACK)
                    startCapture(facing)
                } catch (e: Exception) {
                    lastError = e.javaClass.simpleName.take(60) // não registrar URL/token
                    stopCaptureAndFinish()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun pending(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, Intent(this, PhoneCameraBackgroundService::class.java).apply {
            this.action = action
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, PhoneCameraActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.phone_background_channel))
            .setContentText(getString(if (recording) R.string.phone_background_recording else R.string.phone_background_transmitting))
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(if (recording) R.string.ui_stop else R.string.ui_record), pending(ACTION_RECORD, 2))
            .addAction(0, getString(R.string.phone_background_stop), pending(ACTION_STOP, 3))
            .build()
    }

    private fun startCameraForegroundNotification() {
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        else startForeground(NOTIFICATION_ID, notification())
    }

    private fun updateNotification() {
        if (foregroundStarted) (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification())
    }

    private fun startCapture(facing: Int) {
        val ip = NetUtil.wifiIpv4(this) ?: throw IllegalStateException("Wi-Fi indisponível")
        val session = PhoneCameraServer(ip, PhoneCameraAccess.token(this),
            changeRecording = { desired ->
                if (!running || closing || switching || (desired && latestJpeg == null)) false
                else { if (recording != desired) mainHandler.post {
                    if (!closing && recording != desired) toggleRecording()
                }; true }
            }, recordingStatus = { recording },
            recordingSeconds = { if (recording) ((SystemClock.elapsedRealtime() - recordingStartMs) / 1000).coerceAtLeast(0) else 0 },
            changeEco = { desired ->
                if (!running || closing) false else {
                    ecoEnabled = desired
                    getSharedPreferences("phone_video", MODE_PRIVATE).edit().putBoolean("eco_enabled", desired).apply()
                    true
                }
            }, ecoStatus = { ecoEnabled },
            changeQuality = { quality ->
                if (!running || closing || switching || recording || takingPhoto || quality !in listOf(360, 480, 720)) false
                else { mainHandler.post { reconfigure(session, selectedFacing, quality) }; true }
            }, qualityStatus = { requestedQuality },
            changeFps = { desired ->
                if (!running || closing || desired !in listOf(5, 10, 15)) false else {
                    fpsLimit = desired
                    mainHandler.post { applyCameraFpsRange() }
                    getSharedPreferences("phone_video", MODE_PRIVATE).edit().putInt("remote_fps", desired).apply()
                    true
                }
            }, fpsStatus = { fpsLimit },
            changeFacing = { desired ->
                if (!running || closing || switching || recording || takingPhoto ||
                    desired !in listOf(Camera.CameraInfo.CAMERA_FACING_BACK, Camera.CameraInfo.CAMERA_FACING_FRONT) ||
                    !hasFacing(desired)) false
                else { mainHandler.post { reconfigure(session, desired, requestedQuality) }; true }
            }, facingStatus = { selectedFacing },
            takePhoto = {
                if (!running || closing || switching || recording || takingPhoto || camera == null) false
                else { takingPhoto = true; mainHandler.post { captureFullPhoto() }; true }
            })
        try {
            server = session
            openPreview(session, facing, requestedQuality)
            session.start()
            streamUrl = session.url
            lastError = null
            running = true
            lastPublishedAtMs = SystemClock.elapsedRealtime()
            mainHandler.postDelayed(frameWatchdog, 6000)
            acquireLocks()
            updateNotification()
        } catch (e: Exception) {
            session.stop()
            server = null
            releasePreview()
            throw e
        }
    }

    private fun hasFacing(facing: Int) = (0 until Camera.getNumberOfCameras()).any { id ->
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(id, info)
        info.facing == facing
    }

    private fun releasePreview() {
        val old = camera
        camera = null
        try { old?.setPreviewCallbackWithBuffer(null) } catch (_: Exception) { }
        try { old?.stopPreview() } catch (_: Exception) { }
        try { old?.release() } catch (_: Exception) { }
        headlessPreview?.close()
        headlessPreview = null
    }

    /** Reconfigura no thread principal, sem recriar HTTP/token e sem executar durante MP4. */
    private fun reconfigure(session: PhoneCameraServer, facing: Int, quality: Int) {
        if (server !== session || closing || switching || recording || takingPhoto) return
        switching = true
        val oldFacing = selectedFacing
        val oldQuality = requestedQuality
        releasePreview()
        latestJpeg = null
        session.clearFrame()
        try {
            openPreview(session, facing, quality)
            lastError = null
        } catch (e: Exception) {
            lastError = "Câmera indisponível: ${e.javaClass.simpleName}"
            try { releasePreview(); openPreview(session, oldFacing, oldQuality) }
            catch (_: Exception) { stopCaptureAndFinish() }
        } finally { switching = false }
    }

    private fun openPreview(session: PhoneCameraServer, facing: Int, quality: Int) {
        val deviceId = (0 until Camera.getNumberOfCameras()).firstOrNull { id ->
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(id, info)
            info.facing == facing
        } ?: throw IllegalStateException("Câmera indisponível")
        val opened = Camera.open(deviceId)
        camera = opened
        val params = opened.parameters
        val sizes = params.supportedPreviewSizes.orEmpty().filter { it.width > 0 && it.height > 0 && it.width * it.height <= 1280 * 720 }
        val target = quality // altura da imagem em paisagem é o lado menor (360/480/720).
        val size = sizes.minByOrNull { kotlin.math.abs(minOf(it.width, it.height) - target) * 10 +
            kotlin.math.abs(maxOf(it.width, it.height) - target * 16 / 9) } ?: throw IllegalStateException("Resolução indisponível")
        params.setPreviewSize(size.width, size.height)
        params.previewFormat = ImageFormat.NV21
        val photo = params.supportedPictureSizes.orEmpty().maxByOrNull { it.width.toLong() * it.height }
        if (photo != null) params.setPictureSize(photo.width, photo.height)
        params.jpegQuality = 95
        val range = params.supportedPreviewFpsRange.orEmpty().minByOrNull {
            kotlin.math.abs(it[0] - fpsLimit * 1000) + kotlin.math.abs(it[1] - fpsLimit * 1000)
        }
        if (range != null) try { params.setPreviewFpsRange(range[0], range[1]) } catch (_: Exception) { }
        if (params.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) == true)
            params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        opened.parameters = params
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(deviceId, info)
        val rotation = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        val degrees = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val frameRotation = if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            (info.orientation + degrees) % 360 else (info.orientation - degrees + 360) % 360
        val preview = PhoneHeadlessPreview.create(size.width, size.height)
        headlessPreview = preview
        opened.setPreviewTexture(preview.surfaceTexture)
        val byteCount = size.width * size.height * 3 / 2
        lastCapture = 0L
        lastPublishedAtMs = SystemClock.elapsedRealtime()
        opened.setPreviewCallbackWithBuffer { bytes, source ->
            if (bytes != null && server === session && !closing && !switching &&
                bytes.size >= byteCount &&
                SystemClock.elapsedRealtime() - lastCapture >= maxOf(1000L / fpsLimit.coerceAtLeast(1), PhoneEcoPolicy.intervalMs(ecoEnabled, heatState)) &&
                encoding.compareAndSet(false, true)) {
                lastCapture = SystemClock.elapsedRealtime()
                val snapshot = bytes.copyOf(byteCount)
                try {
                    encodeWorker.execute {
                        try {
                            if (server === session && !closing && !switching) {
                                val rotated = Nv21Frames.rotated(snapshot, size.width, size.height, frameRotation)
                                val width = if (frameRotation % 180 == 0) size.width else size.height
                                val height = if (frameRotation % 180 == 0) size.height else size.width
                                publish(session, rotated, width, height)
                            }
                        } catch (_: Exception) { }
                        finally { encoding.set(false) }
                    }
                } catch (_: Exception) { encoding.set(false) }
            }
            try { if (bytes != null) source.addCallbackBuffer(bytes) } catch (_: Exception) { }
        }
        repeat(3) { opened.addCallbackBuffer(ByteArray(byteCount)) }
        opened.startPreview()
        selectedFacing = facing
        requestedQuality = quality
        getSharedPreferences("phone_video", MODE_PRIVATE).edit().putInt("remote_quality", quality).apply()
        heatState = PhoneHeatState.NORMAL
    }

    private fun applyCameraFpsRange() {
        val opened = camera ?: return
        if (switching || takingPhoto || closing) return
        try {
            val params = opened.parameters
            val range = params.supportedPreviewFpsRange.orEmpty().minByOrNull {
                kotlin.math.abs(it[0] - fpsLimit * 1000) + kotlin.math.abs(it[1] - fpsLimit * 1000)
            } ?: return
            params.setPreviewFpsRange(range[0], range[1])
            opened.parameters = params
        } catch (_: Exception) { /* Mantém limite de quadros por software. */ }
    }

    /** JPEG nativo no maior tamanho anunciado pelo hardware; reinicia preview após takePicture. */
    private fun captureFullPhoto() {
        val opened = camera
        if (!running || closing || opened == null || recording) { takingPhoto = false; return }
        try {
            mainHandler.postDelayed({
                if (takingPhoto && !closing) {
                    takingPhoto = false
                    server?.let { reconfigure(it, selectedFacing, requestedQuality) }
                }
            }, 8000)
            opened.takePicture(null, null, Camera.PictureCallback { jpeg, source ->
                if (jpeg != null && jpeg.isNotEmpty()) {
                    try { encodeWorker.execute {
                        val saved = PhoneRemotePhoto.save(applicationContext, jpeg)
                        if (!saved) lastError = "Não foi possível salvar a foto"
                    } } catch (_: Exception) { }
                }
                try {
                    if (!closing && camera === source) {
                        // Alguns aparelhos descartam buffers de callback ao tirar fotos.
                        val sz = source.parameters.previewSize
                        source.startPreview()
                        // Alguns dispositivos devolvem zero buffers após uma captura estática.
                        repeat(3) { source.addCallbackBuffer(ByteArray(sz.width * sz.height * 3 / 2)) }
                    }
                } catch (_: Exception) { lastError = "Prévia não retomada após foto" }
                takingPhoto = false
            })
        } catch (_: Exception) { takingPhoto = false; lastError = "Falha ao capturar foto" }
    }

    private fun acquireLocks() {
        try {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MinhaCamera:BackgroundCapture")
                .apply { acquire() }
        } catch (_: Exception) { }
        try {
            @Suppress("DEPRECATION")
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MinhaCamera:BackgroundWifi")
                .apply { acquire() }
        } catch (_: Exception) { }
    }

    private fun publish(session: PhoneCameraServer, frame: ByteArray, width: Int, height: Int) {
        if (server !== session || closing) return
        val now = SystemClock.elapsedRealtime()
        val reading = if (cachedBattery == null || now - lastBatteryReadAt >= 2000L) {
            battery.read().also { cachedBattery = it; lastBatteryReadAt = now }
        } else requireNotNull(cachedBattery)
        latestBattery = reading
        heatState = PhoneEcoPolicy.heatState(reading.temperatureTenthsC, heatState)
        val jpeg = ByteArrayOutputStream()
        if (!YuvImage(frame, ImageFormat.NV21, width, height, null)
                .compressToJpeg(Rect(0, 0, width, height), PhoneEcoPolicy.jpegQuality(ecoEnabled, heatState), jpeg)) return
        if (server !== session || closing) return
        val encoded = jpeg.toByteArray()
        latestJpeg = encoded
        lastPublishedAtMs = SystemClock.elapsedRealtime()
        watchdogRestarts = 0
        session.publish(encoded, reading) // Dados da bateria nos headers, nunca gravados nos pixels.
        if (recording && recordingBusy.compareAndSet(false, true)) {
            val snapshot = frame.copyOf()
            try {
                recordWorker.execute {
                    try {
                        if (recording) {
                            if (recorder == null) recorder = PhoneMp4Recorder(applicationContext, width, height)
                            recorder?.append(snapshot, System.nanoTime())
                        }
                    } catch (_: Exception) {
                        recording = false
                        try { recorder?.cancel() } catch (_: Exception) { }
                        recorder = null
                        lastError = getString(R.string.phone_record_failed)
                        mainHandler.post { updateNotification() }
                    } finally { recordingBusy.set(false) }
                }
            } catch (_: Exception) { recordingBusy.set(false) }
        }
    }

    private fun toggleRecording() {
        recording = !recording
        recordingStartMs = if (recording) SystemClock.elapsedRealtime() else 0L
        if (!recording) recordWorker.execute { finishRecording() }
        updateNotification()
    }

    private fun finishRecording() {
        val previous = recorder ?: return
        recorder = null
        try { previous.finish() } catch (_: Exception) {
            lastError = getString(R.string.phone_record_failed)
            try { previous.cancel() } catch (_: Exception) { }
        }
    }

    private fun stopCaptureAndFinish() {
        if (closing) return
        closing = true
        running = false
        recording = false
        recordingStartMs = 0L
        mainHandler.removeCallbacks(frameWatchdog)
        streamUrl = null
        latestJpeg = null
        latestBattery = null
        val oldServer = server
        server = null
        oldServer?.stop()
        releasePreview()
        encodeWorker.shutdown()
        // Enquanto a finalização do MP4 estiver pendente, conservar as proteções de suspensão.
        recordWorker.execute {
            try { finishRecording() } finally {
                mainHandler.post {
                    try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) { }
                    try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) { }
                    wifiLock = null
                    wakeLock = null
                    if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
                    foregroundStarted = false
                    stopSelf()
                }
            }
        }
        recordWorker.shutdown()
    }

    override fun onDestroy() {
        if (!closing) stopCaptureAndFinish()
        super.onDestroy()
    }
}
