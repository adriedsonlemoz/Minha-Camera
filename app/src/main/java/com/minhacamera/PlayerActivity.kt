package com.minhacamera

import android.os.Bundle
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

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        title = intent.getStringExtra("name") ?: getString(R.string.app_name)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        playerView = findViewById(R.id.playerView)
        progress = findViewById(R.id.progress)
        errorText = findViewById(R.id.errorText)
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
        val url = intent.getStringExtra("url") ?: return
        errorText.visibility = View.GONE
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
                progress.visibility =
                    if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE)
                        View.VISIBLE else View.GONE
            }

            override fun onPlayerError(error: PlaybackException) {
                progress.visibility = View.GONE
                errorText.text = getString(R.string.erro_conexao, error.errorCodeName)
                errorText.visibility = View.VISIBLE
            }
        })
        playerView.player = exo
        exo.setMediaSource(source)
        exo.prepare()
        exo.playWhenReady = true
        player = exo
    }
}
