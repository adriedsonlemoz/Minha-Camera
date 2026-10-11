package com.minhacamera

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Media3 para fluxos compatíveis; se falhar, LibVLC tenta os mesmos URLs (incluindo HEVC).
 * Não assume suporte H.265 garantido: decoder, SDP e câmera também precisam ser compatíveis.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private enum class Engine { MEDIA3, VLC }

    private var exo: ExoPlayer? = null
    private var vlcCore: LibVLC? = null
    private var vlcPlayer: VlcMediaPlayer? = null
    private lateinit var exoView: PlayerView
    private lateinit var vlcView: VLCVideoLayout
    private lateinit var progress: ProgressBar
    private lateinit var errorBox: View
    private lateinit var errorText: TextView
    private lateinit var errorDetail: TextView
    private lateinit var engineText: TextView

    private var urls: List<String> = emptyList()
    private var index = 0
    private var engine = Engine.MEDIA3
    private var generation = 0
    private var active = false
    private var hevcSeen = false
    private var failedGeneration = -1
    private val mainHandler = Handler(Looper.getMainLooper())
    private var vlcPlaying = false
    private val failures = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        title = intent.getStringExtra("name") ?: getString(R.string.app_name)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        exoView = findViewById(R.id.playerView)
        vlcView = findViewById(R.id.vlcView)
        progress = findViewById(R.id.progress)
        errorBox = findViewById(R.id.errorBox)
        errorText = findViewById(R.id.errorText)
        errorDetail = findViewById(R.id.errorDetail)
        engineText = findViewById(R.id.engineText)
        val main = intent.getStringExtra("url").orEmpty()
        val alternatives = intent.getStringArrayExtra("alts")?.toList().orEmpty()
        urls = (listOf(main) + alternatives).filter { it.startsWith("rtsp://", ignoreCase = true) }.distinct()
        findViewById<View>(R.id.btnRetry).setOnClickListener { resetAndPlay(Engine.MEDIA3) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 2001, 0, R.string.usar_media3)
        menu.add(0, 2002, 1, R.string.usar_vlc)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        2001 -> { resetAndPlay(Engine.MEDIA3); true }
        2002 -> { resetAndPlay(Engine.VLC); true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onStart() {
        super.onStart()
        active = true
        resetAndPlay(Engine.MEDIA3)
    }

    override fun onStop() {
        active = false
        generation++ // Ignora callbacks atrasados da conexão anterior.
        releasePlayers()
        super.onStop()
    }

    private fun resetAndPlay(startEngine: Engine) {
        index = 0
        engine = startEngine
        failures.clear()
        hevcSeen = false
        if (active) playCurrent()
    }

    private fun releasePlayers() {
        exoView.player = null
        exo?.let { runCatching { it.release() } }
        exo = null
        // Uma falha nativa de limpeza não deve impedir a troca de motor.
        vlcPlayer?.let { nativePlayer ->
            runCatching { nativePlayer.setEventListener(null) }
            runCatching { nativePlayer.stop() }
            runCatching { nativePlayer.detachViews() }
            runCatching { nativePlayer.release() }
        }
        vlcPlayer = null
        vlcCore?.let { runCatching { it.release() } }
        vlcCore = null
    }

    private fun playCurrent() {
        val url = urls.getOrNull(index)
        val current = ++generation
        failedGeneration = -1
        releasePlayers()
        errorBox.visibility = View.GONE
        if (url == null) { showFailure(); return }
        progress.visibility = View.VISIBLE
        exoView.visibility = if (engine == Engine.MEDIA3) View.VISIBLE else View.GONE
        vlcView.visibility = if (engine == Engine.VLC) View.VISIBLE else View.GONE
        engineText.text = getString(
            R.string.engine_fluxo,
            if (engine == Engine.MEDIA3) "Media3" else "LibVLC", index + 1, urls.size
        )
        engineText.visibility = View.VISIBLE
        when (engine) {
            Engine.MEDIA3 -> startMedia3(url, current)
            Engine.VLC -> startVlc(url, current)
        }
    }

    private fun startMedia3(url: String, current: Int) {
        try {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(500, 2000, 250, 500).build()
            val source = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(10_000)
                .createMediaSource(MediaItem.fromUri(url))
            val next = ExoPlayer.Builder(this).setLoadControl(loadControl).build()
            exo = next
            next.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (!active || generation != current) return
                    progress.visibility = if (state == Player.STATE_READY) View.GONE else View.VISIBLE
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!active || generation != current) return
                    val detail = error.errorCodeName + "\n" + causeSummary(error)
                    if (detail.contains("sprop-vps", true) || detail.contains("hevc", true) ||
                        detail.contains("h265", true)) hevcSeen = true
                    onAttemptFailed("Media3", detail)
                }
            })
            exoView.player = next
            next.setMediaSource(source)
            next.prepare()
            next.playWhenReady = true
        } catch (error: Exception) {
            onAttemptFailed("Media3", error.javaClass.simpleName)
        }
    }

    private fun startVlc(url: String, current: Int) {
        vlcPlaying = false
        // Sem vídeo após 15 s: evitar o spinner infinito em câmeras que não respondem.
        mainHandler.postDelayed({
            if (active && generation == current && !vlcPlaying) {
                onAttemptFailed("LibVLC", "Timeout de 15 s aguardando reprodução")
            }
        }, 15_000L)
        try {
            // RTP por TCP e cache baixo reduzem problemas típicos de Wi-Fi doméstico.
            val core = LibVLC(this, arrayListOf("--rtsp-tcp", "--network-caching=500"))
            vlcCore = core
            val next = VlcMediaPlayer(core)
            vlcPlayer = next
            next.setEventListener { event ->
                runOnUiThread {
                    if (!active || generation != current || engine != Engine.VLC) return@runOnUiThread
                    when (event.type) {
                        VlcMediaPlayer.Event.Playing -> {
                            vlcPlaying = true
                            progress.visibility = View.GONE
                            errorBox.visibility = View.GONE
                        }
                        VlcMediaPlayer.Event.EncounteredError -> onAttemptFailed("LibVLC", "Erro ao abrir ou decodificar fluxo RTSP")
                        VlcMediaPlayer.Event.EndReached -> onAttemptFailed("LibVLC", "Transmissão encerrada")
                    }
                }
            }
            next.attachViews(vlcView, null, false, false)
            val media = Media(core, Uri.parse(url))
            media.addOption(":rtsp-tcp")
            media.addOption(":network-caching=500")
            next.setMedia(media)
            media.release()
            next.play()
        } catch (error: Exception) {
            onAttemptFailed("LibVLC", error.javaClass.simpleName)
        } catch (error: LinkageError) {
            // Ausência de biblioteca nativa para a arquitetura do telefone.
            onAttemptFailed("LibVLC", "Biblioteca nativa indisponível (${error.javaClass.simpleName})")
        }
    }

    private fun onAttemptFailed(from: String, detail: String) {
        if (!active || failedGeneration == generation) return
        failedGeneration = generation // Não avançar duas vezes por callbacks duplicados.
        failures.add("$from (fluxo ${index + 1}): ${maskCredentials(detail)}")
        // Tenta todos os URLs conhecidos antes de mudar de motor.
        if (index + 1 < urls.size) {
            index++
        } else if (engine == Engine.MEDIA3) {
            engine = Engine.VLC
            index = 0
        } else {
            showFailure()
            return
        }
        // Não liberar o player dentro do callback de erro do próprio player.
        vlcView.post {
            if (active && !isFinishing && !isDestroyed) playCurrent()
        }
    }

    private fun showFailure() {
        releasePlayers()
        progress.visibility = View.GONE
        engineText.visibility = View.GONE
        errorText.setText(if (hevcSeen) R.string.erro_h265 else R.string.erro_generico)
        errorDetail.text = failures.takeLast(8).joinToString("\n")
            .ifBlank { getString(R.string.erro_sem_fluxos) }
        errorBox.visibility = View.VISIBLE
    }

    private fun causeSummary(error: Throwable): String =
        generateSequence(error) { it.cause }.take(6)
            .joinToString("; ") { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }

    // Exceções de players podem incluir a URL RTSP com usuário e senha.
    private fun maskCredentials(input: String): String =
        input.replace(Regex("(?i)rtsp://[^\\s\\"'<>]+"), "rtsp://[endereço oculto]")
}
