package com.minhacamera

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.util.Rational
import android.widget.LinearLayout
import androidx.media3.common.VideoSize
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.util.Locale

/** Keep the original Media3 -> LibVLC fallback. Recording uses the active VLC stream. */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private enum class Engine { MEDIA3, VLC }
    private var exo: ExoPlayer? = null
    private var vlcCore: LibVLC? = null
    private var vlcPlayer: VlcMediaPlayer? = null
    private lateinit var exoView: PlayerView
    private lateinit var vlcView: VLCVideoLayout
    private lateinit var loadingBox: View
    private lateinit var loadingDetail: TextView
    private lateinit var errorBox: View
    private lateinit var errorText: TextView
    private lateinit var errorDetail: TextView
    private lateinit var engineText: TextView
    private lateinit var recordButton: MaterialButton
    private lateinit var recordTimer: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var viewport: FrameLayout
    private lateinit var videoClip: FrameLayout
    private lateinit var videoContainer: FrameLayout
    private var urls: List<String> = emptyList()
    private var index = 0
    private var engine = Engine.MEDIA3
    private var generation = 0
    private var active = false
    private var hevcSeen = false
    private var failedGeneration = -1
    private var videoReady = false
    private var fullscreen = false
    private var pipVisible = false
    private var displayMode = 0 // full frame, upper half, lower half; never zoom
    private var videoAspect = 0.0
    private var identifying = false
    private var identityThread: Thread? = null
    private var recordPending = false
    private var recordingSession: File? = null
    private var recordingPlayer: VlcMediaPlayer? = null
    private var recordingConfirmed = false
    private var stoppingRecording = false
    private var recordingStart = 0L
    private var finalizingCount = 0
    private val finalizingSessions = mutableSetOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val failures = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        toolbar = findViewById(R.id.playerToolbar)
        setSupportActionBar(toolbar)
        title = intent.getStringExtra("name") ?: getString(R.string.app_name)
        supportActionBar?.subtitle = getString(R.string.monitoramento_vivo)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        exoView = findViewById(R.id.playerView)
        vlcView = findViewById(R.id.vlcView)
        loadingBox = findViewById(R.id.loadingBox)
        loadingDetail = findViewById(R.id.loadingDetail)
        errorBox = findViewById(R.id.errorBox)
        errorText = findViewById(R.id.errorText)
        errorDetail = findViewById(R.id.errorDetail)
        engineText = findViewById(R.id.engineText)
        recordButton = findViewById(R.id.btnRecord)
        recordTimer = findViewById(R.id.recordTimer)
        viewport = findViewById(R.id.videoViewport)
        videoClip = findViewById(R.id.videoClip)
        videoContainer = findViewById(R.id.videoContainer)
        val main = intent.getStringExtra("url").orEmpty()
        val alternatives = intent.getStringArrayExtra("alts")?.toList().orEmpty()
        urls = (listOf(main) + alternatives).filter { it.startsWith("rtsp://", true) }.distinct()
        displayMode = (savedInstanceState?.getInt("displayMode") ?: 0).coerceIn(0, 2)
        fullscreen = savedInstanceState?.getBoolean("fullscreen") ?: false
        findViewById<View>(R.id.btnRetry).setOnClickListener { resetAndPlay(Engine.MEDIA3) }
        recordButton.setOnClickListener { onRecordClick() }
        findViewById<View>(R.id.btnRecordings).setOnClickListener { startActivity(Intent(this, RecordingsActivity::class.java)) }
        findViewById<View>(R.id.btnDisplay).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.exibicao_video).setSingleChoiceItems(arrayOf(
                getString(R.string.imagem_inteira),
                getString(R.string.imagem_superior), getString(R.string.imagem_inferior)
            ), displayMode) { dialog, which -> displayMode = which; applyDisplayMode(); dialog.dismiss() }.show()
        }
        findViewById<View>(R.id.btnPip).setOnClickListener { enterPip() }
        findViewById<View>(R.id.btnFullscreen).setOnClickListener { fullscreen = !fullscreen; applyFullscreen() }
        viewport.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right-left != oldRight-oldLeft || bottom-top != oldBottom-oldTop) applyDisplayMode()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (fullscreen) { fullscreen = false; applyFullscreen() } else finish() }
        })
        configureControls()
        applyFullscreen()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("fullscreen", fullscreen); outState.putInt("displayMode", displayMode)
        super.onSaveInstanceState(outState)
    }
    override fun onPictureInPictureModeChanged(inPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig)
        pipVisible = inPictureInPictureMode
        findViewById<View>(R.id.playerControls).visibility = if (pipVisible) View.GONE else View.VISIBLE
        engineText.visibility = if (pipVisible) View.GONE else View.VISIBLE
        recordTimer.visibility = if (pipVisible) View.GONE else if (recordingConfirmed) View.VISIBLE else View.GONE
        if (pipVisible) toolbar.visibility = View.GONE else applyFullscreen()
        viewport.post { applyDisplayMode() }
    }

    private fun enterPip() {
        if (!PipSupport.available(this)) {
            Toast.makeText(this, R.string.pip_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        if (!videoReady || !active) {
            Toast.makeText(this, R.string.pip_wait_for_video, Toast.LENGTH_SHORT).show()
            return
        }
        if (recordingSession != null || recordPending) {
            Toast.makeText(this, R.string.pip_stop_recording, Toast.LENGTH_LONG).show()
            return
        }
        val ratio = if (videoAspect.isFinite() && videoAspect in 0.43..2.30)
            Rational((videoAspect * 1000).toInt().coerceAtLeast(1), 1000) else Rational(16, 9)
        if (!PipSupport.enter(this, viewport, ratio))
            Toast.makeText(this, R.string.pip_unavailable, Toast.LENGTH_LONG).show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configureControls()
        viewport.post { applyDisplayMode(); applyFullscreen() }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 2001, 0, R.string.usar_media3)
        menu.add(0, 2002, 1, R.string.usar_vlc)
        menu.add(0, 2003, 2, R.string.informacoes_tecnicas)
        menu.add(0, 2004, 3, R.string.identificar_camera)
        menu.add(0, 2005, 4, R.string.pip_window)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        2001 -> { resetAndPlay(Engine.MEDIA3); true }
        2002 -> { resetAndPlay(Engine.VLC); true }
        2003 -> { AlertDialog.Builder(this).setTitle(R.string.informacoes_tecnicas).setMessage(getString(
            R.string.engine_fluxo, if (engine == Engine.MEDIA3) "Media3" else "LibVLC", index+1, urls.size
        )).setPositiveButton(android.R.string.ok, null).show(); true }
        2004 -> { identifyCamera(); true }
        2005 -> { enterPip(); true }
        else -> super.onOptionsItemSelected(item)
    }
    override fun onSupportNavigateUp(): Boolean { if (fullscreen) { fullscreen=false; applyFullscreen() } else finish(); return true }
    override fun onStart() {
        super.onStart()
        if (!active) { active = true; resetAndPlay(Engine.MEDIA3) }
    }
    override fun onStop() {
        if (android.os.Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode) { super.onStop(); return }
        active = false; recordPending = false; generation++
        mainHandler.removeCallbacksAndMessages(null)
        releasePlayers()
        super.onStop()
    }
    override fun onDestroy() { identityThread?.interrupt(); identityThread = null; super.onDestroy() }
    private fun applyFullscreen() {
        toolbar.visibility = if (fullscreen || pipVisible) View.GONE else View.VISIBLE
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else controller.show(WindowInsetsCompat.Type.systemBars())
        findViewById<MaterialButton>(R.id.btnFullscreen).setText(if (fullscreen) R.string.sair_tela_cheia else R.string.tela_cheia)
    }
    private fun configureControls() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val controls = findViewById<LinearLayout>(R.id.playerControls)
        controls.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        for (id in listOf(R.id.controlsPrimary, R.id.controlsSecondary)) {
            findViewById<LinearLayout>(id).layoutParams = LinearLayout.LayoutParams(
                if (landscape) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, if (landscape) 1f else 0f)
        }
        toolbar.layoutParams = toolbar.layoutParams.apply {
            height = ((if (landscape) 48 else 56) * resources.displayMetrics.density).toInt()
        }
        supportActionBar?.subtitle = if (landscape) null else getString(R.string.monitoramento_vivo)
    }
    private fun setVideoAspect(width: Int, height: Int, pixelRatio: Double = 1.0, rotated: Boolean = false) {
        if (width <= 0 || height <= 0 || pixelRatio <= 0 || !pixelRatio.isFinite()) return
        val raw = width.toDouble() * pixelRatio / height
        val aspect = if (rotated) 1.0 / raw else raw
        if (kotlin.math.abs(aspect - videoAspect) > 0.0001) { videoAspect = aspect; applyDisplayMode() }
    }
    private fun readVlcVideoSize(player: VlcMediaPlayer) {
        runCatching {
            val track = player.currentVideoTrack ?: return
            val sar = if (track.sarNum > 0 && track.sarDen > 0) track.sarNum.toDouble()/track.sarDen else 1.0
            setVideoAspect(track.width, track.height, sar, track.orientation >= 4)
        }
    }
    private fun applyDisplayMode() {
        val frame = if (displayMode > 0) VideoGeometry.fitHalf(viewport.width, viewport.height, videoAspect, displayMode == 2) else null
        val clipParams = videoClip.layoutParams as FrameLayout.LayoutParams
        val clipWidth = frame?.width ?: FrameLayout.LayoutParams.MATCH_PARENT
        val clipHeight = frame?.clipHeight ?: FrameLayout.LayoutParams.MATCH_PARENT
        if (clipParams.width != clipWidth || clipParams.height != clipHeight || clipParams.gravity != Gravity.CENTER) {
            clipParams.width = clipWidth; clipParams.height = clipHeight; clipParams.gravity = Gravity.CENTER
            videoClip.layoutParams = clipParams
        }
        val params = videoContainer.layoutParams as FrameLayout.LayoutParams
        val width = frame?.width ?: FrameLayout.LayoutParams.MATCH_PARENT
        val height = frame?.videoHeight ?: FrameLayout.LayoutParams.MATCH_PARENT
        if (params.width != width || params.height != height || params.gravity != (Gravity.TOP or Gravity.CENTER_HORIZONTAL)) {
            params.width = width; params.height = height; params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            videoContainer.layoutParams = params
        }
        videoContainer.translationY = frame?.videoTop ?: 0f
        exoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        runCatching { vlcPlayer?.setVideoScale(VlcMediaPlayer.ScaleType.SURFACE_BEST_FIT) }
    }
    private fun identifyCamera() {
        if (identifying) return
        val uri = Uri.parse(urls.getOrNull(index) ?: return)
        val host = uri.host ?: return
        val auth = uri.encodedUserInfo.orEmpty()
        val user = Uri.decode(auth.substringBefore(':'))
        val pass = Uri.decode(auth.substringAfter(':', ""))
        identifying = true
        val dialog = AlertDialog.Builder(this).setTitle(R.string.identificar_camera)
            .setMessage(R.string.identificando_camera).setPositiveButton(android.R.string.ok, null).show()
        val app = applicationContext
        identityThread = Thread {
            val result = runCatching {
                val endpoints = WsDiscovery.search(app).filter { it.ip == host }.flatMap { it.xaddrs }
                OnvifClient.identify(host, user, pass, endpoints)
            }.getOrElse { getString(R.string.identificacao_indisponivel) }
            runOnUiThread {
                identifying = false
                if (!isFinishing && !isDestroyed && dialog.isShowing) dialog.setMessage(result)
            }
        }.also { it.start() }
    }
    private fun resetAndPlay(startEngine: Engine) {
        recordPending = false; index = 0; engine = startEngine
        failures.clear(); hevcSeen = false
        if (active) playCurrent()
    }
    private fun releasePlayers() {
        exoView.player = null; exo?.let { runCatching { it.release() } }; exo = null
        vlcPlayer?.let { nativePlayer ->
            val session = recordingSession.takeIf { recordingPlayer === nativePlayer }
            if (session != null) runCatching { nativePlayer.record(null) }
            runCatching { nativePlayer.setEventListener(null) }
            // Synchronous stop closes native outputs before moving a completed file.
            runCatching { nativePlayer.stop() }
            runCatching { nativePlayer.detachViews() }; runCatching { nativePlayer.release() }
            if (session != null) finishRecording(session)
        }
        vlcPlayer = null; vlcCore?.let { runCatching { it.release() } }; vlcCore = null
    }
    private fun loading(detail: Int) {
        loadingDetail.setText(detail); loadingBox.visibility = View.VISIBLE
        engineText.setText(R.string.status_conectando)
    }
    private fun videoAvailable() {
        val first = !videoReady
        videoReady = true; loadingBox.visibility = View.GONE; errorBox.visibility = View.GONE
        engineText.setText(R.string.status_ao_vivo); updateRecordButton()
        if (first && recordPending) { recordPending = false; beginRecording() }
    }
    private fun playCurrent() {
        val url = urls.getOrNull(index)
        val current = ++generation; failedGeneration = -1
        releasePlayers(); videoReady = false; videoAspect = 0.0; applyDisplayMode(); errorBox.visibility = View.GONE
        if (url == null) { showFailure(); return }
        loading(if (failures.isEmpty()) R.string.aguarde_imagem else R.string.conexao_alternativa)
        exoView.visibility = if (engine == Engine.MEDIA3) View.VISIBLE else View.GONE
        vlcView.visibility = if (engine == Engine.VLC) View.VISIBLE else View.GONE
        updateRecordButton()
        mainHandler.postDelayed({
            if (active && generation == current && !videoReady) onAttemptFailed(
                if (engine == Engine.MEDIA3) "Media3" else "LibVLC", "Timeout de 15 s aguardando a primeira imagem")
        }, 15_000L)
        if (engine == Engine.MEDIA3) startMedia3(url, current) else startVlc(url, current)
    }
    private fun startMedia3(url: String, current: Int) {
        try {
            val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(500, 2000, 250, 500).build()
            val source = RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(10_000)
                .createMediaSource(MediaItem.fromUri(url))
            val next = ExoPlayer.Builder(this).setLoadControl(loadControl).build(); exo = next
            next.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    if (active && generation == current) setVideoAspect(size.width, size.height,
                        size.pixelWidthHeightRatio.toDouble(), size.unappliedRotationDegrees % 180 != 0)
                }
                override fun onRenderedFirstFrame() { if (active && generation == current) videoAvailable() }
                override fun onPlaybackStateChanged(state: Int) {
                    if (!active || generation != current) return
                    if (state == Player.STATE_BUFFERING) loading(if (videoReady) R.string.buffer_imagem else R.string.aguarde_imagem)
                    if (state == Player.STATE_READY && videoReady) videoAvailable()
                    if (state == Player.STATE_ENDED) onAttemptFailed("Media3", "Transmissão encerrada")
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (!active || generation != current) return
                    val detail = error.errorCodeName + "\n" + causeSummary(error)
                    if (detail.contains("sprop-vps", true) || detail.contains("hevc", true) || detail.contains("h265", true)) hevcSeen = true
                    onAttemptFailed("Media3", detail)
                }
            })
            exoView.player = next; next.setMediaSource(source); next.prepare(); next.playWhenReady = true
            applyDisplayMode()
        } catch (error: Exception) { onAttemptFailed("Media3", causeSummary(error)) }
          catch (error: LinkageError) { onAttemptFailed("Media3", "Componente de reprodução indisponível (${error.javaClass.simpleName})") }
    }
    private fun startVlc(url: String, current: Int) {
        try {
            val core = LibVLC(this, arrayListOf("--rtsp-tcp", "--network-caching=500", "--stats")); vlcCore = core
            val next = VlcMediaPlayer(core); vlcPlayer = next
            next.setEventListener { event ->
                // Recording close events must be handled even while leaving the screen.
                val eventType = event.type
                val eventRecording = if (eventType == VlcMediaPlayer.Event.RecordChanged) event.recording else false
                runOnUiThread {
                    if (eventType == VlcMediaPlayer.Event.RecordChanged && recordingPlayer === next) {
                        val session = recordingSession
                        if (session != null) {
                            if (eventRecording && !stoppingRecording) { recordingConfirmed = true; updateRecordButton(); tickRecording(session) }
                            else if (!eventRecording) finishRecording(session)
                        }
                    }
                    if (!active || generation != current || engine != Engine.VLC) return@runOnUiThread
                    when (eventType) {
                        VlcMediaPlayer.Event.Vout -> readVlcVideoSize(next)
                        VlcMediaPlayer.Event.Buffering -> {
                            if (event.buffering < 100f) loading(if (videoReady) R.string.buffer_imagem else R.string.aguarde_imagem)
                            else if (videoReady) videoAvailable()
                        }
                        VlcMediaPlayer.Event.EncounteredError -> onAttemptFailed("LibVLC", "Erro ao abrir ou decodificar fluxo RTSP")
                        VlcMediaPlayer.Event.EndReached -> onAttemptFailed("LibVLC", "Transmissão encerrada")
                    }
                }
            }
            next.attachViews(vlcView, null, false, false)
            val media = Media(core, Uri.parse(url)); media.addOption(":rtsp-tcp"); media.addOption(":network-caching=500")
            next.setMedia(media); media.release(); next.play(); applyDisplayMode()
            // Playing means input is running; actual displayed pictures gate the loading screen.
            fun awaitPicture() {
                if (!active || generation != current || videoReady || vlcPlayer !== next) return
                readVlcVideoSize(next)
                val displayed = runCatching {
                    val currentMedia = next.media
                    try { currentMedia?.stats?.displayedPictures ?: 0 } finally { currentMedia?.release() }
                }.getOrDefault(0)
                if (displayed > 0) videoAvailable() else mainHandler.postDelayed({ awaitPicture() }, 250L)
            }
            mainHandler.postDelayed({ awaitPicture() }, 250L)
        } catch (error: Exception) { onAttemptFailed("LibVLC", causeSummary(error)) }
        catch (error: LinkageError) { onAttemptFailed("LibVLC", "Biblioteca nativa indisponível (${error.javaClass.simpleName})") }
    }
    private fun onRecordClick() {
        if (recordPending) { recordPending=false; updateRecordButton(); return }
        if (recordingSession != null) { stopRecording(); return }
        if (!videoReady || finalizingCount > 0) return
        AlertDialog.Builder(this).setTitle(R.string.gravar).setMessage(R.string.gravacao_local_hint)
            .setNegativeButton(R.string.cancelar, null).setPositiveButton(R.string.iniciar_gravacao) { _, _ ->
                if (!active || !videoReady) return@setPositiveButton
                if (engine == Engine.MEDIA3) { recordPending = true; engine = Engine.VLC; playCurrent() } else beginRecording()
            }.show()
    }
    private fun beginRecording() {
        val player = vlcPlayer ?: return
        if (!active || !videoReady || recordingSession != null || finalizingCount > 0) return
        val session = runCatching { RecordingStore.newSession(this) }.getOrNull()
        if (session == null || session.usableSpace < 100L*1024*1024) {
            session?.delete(); Toast.makeText(this, R.string.gravacao_espaco, Toast.LENGTH_LONG).show(); updateRecordButton(); return
        }
        recordingSession = session; recordingPlayer = player; stoppingRecording = false
        recordingConfirmed = false; recordingStart = SystemClock.elapsedRealtime()
        val ok = runCatching { player.record(session.absolutePath) }.getOrDefault(false)
        if (!ok) {
            recordingSession = null; recordingPlayer = null; session.delete()
            Toast.makeText(this, R.string.gravacao_falhou, Toast.LENGTH_LONG).show()
        } else mainHandler.postDelayed({
            if (recordingSession == session && !recordingConfirmed && !stoppingRecording) {
                Toast.makeText(this, R.string.gravacao_sem_confirmacao, Toast.LENGTH_LONG).show(); stopRecording()
            }
        }, 8_000L)
        updateRecordButton()
    }
    private fun tickRecording(session: File) {
        if (!active || recordingSession != session || !recordingConfirmed || stoppingRecording) return
        val secs = (SystemClock.elapsedRealtime()-recordingStart)/1000
        recordTimer.text = String.format(Locale.ROOT, "● REC %02d:%02d", secs/60, secs%60)
        recordTimer.visibility = View.VISIBLE
        if (session.usableSpace < 16L*1024*1024) {
            Toast.makeText(this, R.string.gravacao_espaco, Toast.LENGTH_LONG).show(); stopRecording(); return
        }
        mainHandler.postDelayed({ tickRecording(session) }, 1000L)
    }
    private fun stopRecording() {
        val session = recordingSession ?: return
        if (stoppingRecording) return
        stoppingRecording = true; updateRecordButton()
        runCatching { recordingPlayer?.record(null) }
        // If native closure is not confirmed, stop the native player before moving files.
        mainHandler.postDelayed({
            if (recordingSession == session && stoppingRecording) {
                val current = generation
                releasePlayers()
                if (active && generation == current) playCurrent()
            }
        }, 1500L)
    }
    private fun finishRecording(session: File) {
        if (!finalizingSessions.add(session.absolutePath)) return
        if (recordingSession == session) {
            recordingSession = null; recordingPlayer = null; recordingConfirmed = false; stoppingRecording = false
        }
        finalizingCount++; recordTimer.visibility = View.GONE; updateRecordButton()
        val app = applicationContext
        RecordingStore.io.execute {
            val result = runCatching { RecordingFiles.finish(RecordingStore.root(app), session) }
            runOnUiThread {
                finalizingCount--; if (!isDestroyed) updateRecordButton()
                RecordingStore.notifyChanged()
                val message = if (result.getOrNull()?.isNotEmpty() == true) R.string.gravacao_salva else R.string.gravacao_vazia
                Toast.makeText(app, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun updateRecordButton() {
        if (!::recordButton.isInitialized) return
        val text = when {
            stoppingRecording || finalizingCount > 0 -> R.string.salvando_gravacao
            recordingSession != null && recordingConfirmed -> R.string.parar_gravacao
            recordingSession != null || recordPending -> R.string.preparando_gravacao
            else -> R.string.gravar
        }
        recordButton.setText(text)
        recordButton.setIconResource(if (recordingSession != null || recordPending) R.drawable.ic_stop else R.drawable.ic_record)
        recordButton.isEnabled = active && !stoppingRecording && finalizingCount == 0 && (videoReady || recordPending || recordingSession != null)
    }
    private fun onAttemptFailed(from: String, detail: String) {
        if (!active || failedGeneration == generation) return
        failedGeneration = generation
        failures.add("$from (fluxo ${index+1}): ${maskCredentials(detail)}")
        if (index+1 < urls.size) index++ else if (engine == Engine.MEDIA3) { engine=Engine.VLC; index=0 } else { showFailure(); return }
        val failed = generation
        vlcView.post { if (active && generation == failed && !isFinishing && !isDestroyed) playCurrent() }
    }
    private fun showFailure() {
        releasePlayers(); recordPending=false; videoReady=false; loadingBox.visibility=View.GONE
        engineText.setText(R.string.status_conectando)
        errorText.setText(if (hevcSeen) R.string.erro_h265 else R.string.erro_generico)
        errorDetail.text = failures.takeLast(8).joinToString("\n").ifBlank { getString(R.string.erro_sem_fluxos) }
        errorBox.visibility=View.VISIBLE; updateRecordButton()
    }
    private fun causeSummary(error: Throwable): String = generateSequence(error) { it.cause }.take(6)
        .joinToString("; ") { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
    private fun maskCredentials(input: String): String = input.replace(Regex("""(?i)rtsp://[^\s"'<>]+"""), "rtsp://[endereço oculto]")
}
