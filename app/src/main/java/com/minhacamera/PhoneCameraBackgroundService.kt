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
import android.graphics.SurfaceTexture
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
    private var texture: SurfaceTexture? = null
    @Volatile private var server: PhoneCameraServer? = null
    private var recorder: PhoneMp4Recorder? = null // acessado só pelo recordWorker
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastCapture = 0L
    private var heatState = PhoneHeatState.NORMAL
    private var ecoEnabled = false
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
                if (!running || closing || (desired && latestJpeg == null)) false
                else {
                    if (recording != desired) toggleRecording()
                    true
                }
            }, recordingStatus = { recording })
        try {
            val deviceId = (0 until Camera.getNumberOfCameras()).firstOrNull { id ->
                val info = Camera.CameraInfo()
                Camera.getCameraInfo(id, info)
                info.facing == facing
            } ?: throw IllegalStateException("Câmera indisponível")
            val opened = Camera.open(deviceId)
            camera = opened
            val params = opened.parameters
            val size = params.supportedPreviewSizes.orEmpty().minByOrNull {
                kotlin.math.abs(it.width - 640) + kotlin.math.abs(it.height - 480) +
                    if (it.width * it.height > 1280 * 720) 10000 else 0
            } ?: throw IllegalStateException("Prévia indisponível")
            params.setPreviewSize(size.width, size.height)
            params.previewFormat = ImageFormat.NV21
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
                (info.orientation + degrees) % 360
            else (info.orientation - degrees + 360) % 360
            // SurfaceTexture própria: continua válida mesmo depois de a tela / Activity sumir.
            texture = SurfaceTexture(34).also { it.setDefaultBufferSize(size.width, size.height) }
            opened.setPreviewTexture(texture)
            val bytesPerFrame = size.width * size.height * 3 / 2
            heatState = PhoneHeatState.NORMAL
            lastCapture = 0L
            latestBattery = null
            latestJpeg = null
            opened.setPreviewCallbackWithBuffer { bytes, source ->
                if (bytes != null && server === session && !closing &&
                    bytes.size >= bytesPerFrame &&
                    SystemClock.elapsedRealtime() - lastCapture >= PhoneEcoPolicy.intervalMs(ecoEnabled, heatState) &&
                    encoding.compareAndSet(false, true)) {
                    lastCapture = SystemClock.elapsedRealtime()
                    val snapshot = bytes.copyOf(bytesPerFrame)
                    try {
                        encodeWorker.execute {
                            try {
                                if (server === session && !closing) {
                                    val rotated = Nv21Frames.rotated(snapshot, size.width, size.height, frameRotation)
                                    val width = if (frameRotation % 180 == 0) size.width else size.height
                                    val height = if (frameRotation % 180 == 0) size.height else size.width
                                    publish(session, rotated, width, height)
                                }
                            } catch (_: Exception) { /* Perda de um quadro não derruba o serviço. */ }
                            finally { encoding.set(false) }
                        }
                    } catch (_: Exception) { encoding.set(false) }
                }
                try { if (bytes != null) source.addCallbackBuffer(bytes) } catch (_: Exception) { }
            }
            repeat(3) { opened.addCallbackBuffer(ByteArray(bytesPerFrame)) }
            server = session
            session.start()
            opened.startPreview()
            streamUrl = session.url
            lastError = null
            running = true
            acquireLocks()
            updateNotification()
        } catch (e: Exception) {
            session.stop()
            throw e
        }
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
        val reading = battery.read()
        latestBattery = reading
        heatState = PhoneEcoPolicy.heatState(reading.temperatureTenthsC, heatState)
        val jpeg = ByteArrayOutputStream()
        if (!YuvImage(frame, ImageFormat.NV21, width, height, null)
                .compressToJpeg(Rect(0, 0, width, height), PhoneEcoPolicy.jpegQuality(ecoEnabled, heatState), jpeg)) return
        if (server !== session || closing) return
        val encoded = jpeg.toByteArray()
        latestJpeg = encoded
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
        streamUrl = null
        latestJpeg = null
        latestBattery = null
        val oldServer = server
        server = null
        oldServer?.stop()
        val oldCamera = camera
        camera = null
        try { oldCamera?.setPreviewCallbackWithBuffer(null) } catch (_: Exception) { }
        try { oldCamera?.stopPreview() } catch (_: Exception) { }
        try { oldCamera?.release() } catch (_: Exception) { }
        try { texture?.release() } catch (_: Exception) { }
        texture = null
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
