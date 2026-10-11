package com.minhacamera

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progress: ProgressBar
    private lateinit var errorBox: View
    private lateinit var errorText: TextView
    private lateinit var errorDetail: TextView
    private var urls: List<String> = emptyList()
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        title = intent.getStringExtra("name") ?: getString(R.string.app_name)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        playerView = findViewById(R.id.playerView)
        progress = findViewById(R.id.progress)
        errorBox = findViewById(R.id.errorBox)
        errorText = findViewById(R.id.errorText)
        errorDetail = findViewById(R.id.errorDetail)

        val main = intent.getStringExtra("url") ?: ""
        val alts = intent.getStringArrayExtra("alts")?.toList() ?: emptyList()
        urls = listOf(main) + alts

        findViewById<Button>(R.id.btnRetry).setOnClickListener {
            index = 0
            startPlayer()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onStart() {
        super.onStart()
        startPlayer()
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    private fun startPlayer() {
        val url = urls.getOrNull(index) ?: return
        player?.release()
        player = null
        errorBox.visibility = View.GONE
        progress.visibility = View.VISIBLE

        // Buffer pequeno para baixa latência.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 2000, 250, 500)
            .build()

        val source = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true) // RTP sobre TCP: mais estável em Wi-Fi
            .setTimeoutMs(10_000)
            .createMediaSource(MediaItem.fromUri(url))

        val exo = ExoPlayer.Builder(this).setLoadControl(loadControl).build()
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    progress.visibility = View.GONE
                    errorBox.visibility = View.GONE
                } else {
                    progress.visibility =
                        if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE)
                            View.VISIBLE else View.GONE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handleError(error)
            }
        })
        playerView.player = exo
        exo.setMediaSource(source)
        exo.prepare()
        exo.playWhenReady = true
        player = exo
    }

    private fun handleError(error: PlaybackException) {
        // Tenta automaticamente o próximo fluxo (ex.: secundário em H.264).
        if (index + 1 < urls.size) {
            index++
            Toast.makeText(
                this, getString(R.string.tentando_outro, index + 1, urls.size), Toast.LENGTH_SHORT
            ).show()
            playerView.post {
                if (!isFinishing && !isDestroyed) startPlayer()
            }
            return
        }

        progress.visibility = View.GONE
        val chain = generateSequence<Throwable>(error) { it.cause }
            .joinToString("\n") { "${it.javaClass.simpleName}: ${it.message ?: ""}" }
        val msg = when {
            chain.contains("sprop-vps", ignoreCase = true) ||
                chain.contains("h265", ignoreCase = true) ||
                chain.contains("hevc", ignoreCase = true) -> R.string.erro_h265
            chain.contains("401") || chain.contains("Unauthorized", ignoreCase = true) -> R.string.erro_login
            chain.contains("404") -> R.string.erro_caminho
            else -> R.string.erro_generico
        }
        errorText.setText(msg)
        errorDetail.text = error.errorCodeName + "\n" + chain
        errorBox.visibility = View.VISIBLE
    }
}
