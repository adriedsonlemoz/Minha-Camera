package com.minhacamera

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs

/** Visualização integrada com Media3 e pinça em foto; sem enviar URI a um aplicativo externo. */
class GalleryViewerActivity : AppCompatActivity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var stage: FrameLayout
    private lateinit var title: TextView
    private var player: ExoPlayer? = null
    private var current: GalleryItem? = null
    private var index = 0
    private var generation = 0
    private var pendingDelete = false
    private var legacyDeleteConsent = false
    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { target ->
        val source = current ?: return@registerForActivityResult
        if (target != null) io.execute {
            val result = runCatching {
                contentResolver.openInputStream(source.uri)!!.use { input ->
                    contentResolver.openOutputStream(target, "w")!!.use { output -> input.copyTo(output) }
                }
            }.isSuccess
            ui.post { if (!isFinishing) toast(if (result) "Cópia salva." else "Falha ao copiar. Original preservado.") }
        }
    }
    private val deleteIntent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (pendingDelete && result.resultCode == Activity.RESULT_OK) {
            if (legacyDeleteConsent) {
                legacyDeleteConsent = false
                pendingDelete = false
                deleteCurrent()  // No Android 10 a autorização apenas permite tentar novamente.
                return@registerForActivityResult
            }
            toast("Arquivo excluído."); finish()
        } else if (pendingDelete) toast("Exclusão cancelada.")
        legacyDeleteConsent = false
        pendingDelete = false
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 15, 25)
        window.navigationBarColor = Color.rgb(9, 15, 25)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 15, 25))
        }
        setContentView(root)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(7), dp(5), dp(7), dp(5)) }
        header.addView(control("‹") { finish() }, LinearLayout.LayoutParams(dp(54), dp(52)))
        title = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        header.addView(title, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(control("🗑") {
            AlertDialog.Builder(this).setTitle("Excluir este arquivo?")
                .setMessage("A remoção pode ser permanente. O Android poderá pedir autorização.")
                .setPositiveButton("Excluir") { _, _ -> deleteCurrent() }
                .setNegativeButton("Cancelar", null).show()
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        header.addView(control("⋮") { options() }, LinearLayout.LayoutParams(dp(52), dp(52)))
        root.addView(header)
        stage = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dp(6), dp(5), dp(6), dp(10)) }
        footer.addView(control("‹ Anterior") { navigate(-1) }, LinearLayout.LayoutParams(0, dp(50), 1f))
        footer.addView(control("Compartilhar") { shareCurrent() }, LinearLayout.LayoutParams(0, dp(50), 1f))
        footer.addView(control("Próximo ›") { navigate(1) }, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(footer)
        val fallback = intent.getStringExtra("media_uri")?.let { Uri.parse(it) } ?: run { finish(); return }
        val list = GallerySession.items
        index = list.indexOfFirst { it.key == fallback.toString() }
        if (index < 0) {
            val mime = intent.getStringExtra("media_mime") ?: "video/*"
            val name = intent.getStringExtra("media_name") ?: "Mídia"
            current = GalleryItem(fallback, name, mime, 0, System.currentTimeMillis() / 1000,
                intent.getStringExtra("media_private")?.let { File(it) })
            show(current!!)
        } else show(list[index])
    }
    private fun control(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(24, 36, 52))
        setOnClickListener { action() }
    }
    private fun navigate(delta: Int) {
        val list = GallerySession.items
        if (list.isEmpty() || index !in list.indices) { toast("Sem outras mídias."); return }
        val next = index + delta
        if (next !in list.indices) { toast("Fim da galeria."); return }
        index = next
        show(list[next])
    }
    private fun show(item: GalleryItem) {
        current = item
        val epoch = ++generation
        releasePlayer()
        stage.removeAllViews()
        title.text = item.name
        if (item.isVideo) {
            val view = PlayerView(this).apply {
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
            stage.addView(view, FrameLayout.LayoutParams(-1, -1))
            val exo = ExoPlayer.Builder(this).build()
            player = exo
            view.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    toast("Não foi possível reproduzir este formato de vídeo.")
                }
            })
            exo.setMediaItem(MediaItem.fromUri(item.uri))
            exo.prepare()
            exo.playWhenReady = true
        } else {
            val image = PinchPhotoView(this) { direction -> navigate(direction) }
            stage.addView(image, FrameLayout.LayoutParams(-1, -1))
            io.execute {
                val bitmap = runCatching { GalleryThumbnails.decode(applicationContext, item, 2400) }.getOrNull()
                ui.post {
                    if (epoch != generation || isFinishing || isDestroyed) { bitmap?.recycle(); return@post }
                    if (bitmap != null) image.setImageBitmap(bitmap) else toast("Não foi possível abrir esta imagem.")
                }
            }
        }
    }
    private fun options() {
        val item = current ?: return
        AlertDialog.Builder(this).setTitle(item.name).setItems(arrayOf("Detalhes", "Salvar cópia", "Compartilhar", "Excluir")) { _, p ->
            when (p) {
                0 -> AlertDialog.Builder(this).setTitle("Detalhes").setMessage("${item.name}\n${item.mime}\n${Formatter.formatFileSize(this, item.size)}\n${if (item.privateFile != null) "Gravação privada" else "Mídia local"}")
                    .setPositiveButton("Fechar", null).show()
                1 -> saveFile.launch(item.name)
                2 -> shareCurrent()
                3 -> AlertDialog.Builder(this).setTitle("Excluir arquivo?")
                    .setMessage("Confirme a exclusão. A gravação privada não poderá ser recuperada.")
                    .setPositiveButton("Excluir") { _, _ -> deleteCurrent() }
                    .setNegativeButton("Cancelar", null).show()
            }
        }.show()
    }
    private fun shareCurrent() {
        val item = current ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = item.mime
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(send, "Compartilhar mídia")) }
            .onFailure { toast("Não foi possível compartilhar.") }
    }
    private fun deleteCurrent() {
        val item = current ?: return
        val private = item.privateFile
        if (private != null) {
            val root = runCatching { RecordingStore.root(this).canonicalFile }.getOrNull()
            val ok = root != null && runCatching { private.canonicalFile.parentFile == root &&
                    private.name.startsWith("Camera_") && private.delete() }.getOrDefault(false)
            toast(if (ok) "Gravação excluída." else "Falha na exclusão.")
            if (ok) finish()
            return
        }
        if (item.uri.authority?.startsWith("media") != true) {
            toast("Arquivo externo: gerencie a exclusão no aplicativo de origem.")
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val sender = runCatching { android.provider.MediaStore.createDeleteRequest(contentResolver, listOf(item.uri)) }.getOrNull()
            if (sender == null) { toast("Não foi possível solicitar exclusão."); return }
            pendingDelete = true
            deleteIntent.launch(IntentSenderRequest.Builder(sender.intentSender).build())
        } else {
            try {
                val ok = contentResolver.delete(item.uri, null, null) > 0
                toast(if (ok) "Arquivo excluído." else "Arquivo indisponível para exclusão.")
                if (ok) finish()
            } catch (ex: SecurityException) {
                if (Build.VERSION.SDK_INT == 29 && ex is android.app.RecoverableSecurityException) {
                    pendingDelete = true
                    legacyDeleteConsent = true
                    deleteIntent.launch(IntentSenderRequest.Builder(ex.userAction.actionIntent.intentSender).build())
                } else toast("A exclusão requer autorização do Android.")
            }
        }
    }
    private fun releasePlayer() { player?.release(); player = null }
    override fun onStop() { releasePlayer(); super.onStop() }
    override fun onRestart() { super.onRestart(); current?.takeIf { it.isVideo }?.let { show(it) } }
    override fun onDestroy() { ++generation; releasePlayer(); io.shutdownNow(); super.onDestroy() }
}

/** Pinça real e arraste no visualizador. Deslizar lateralmente a 1× muda de arquivo. */
private class PinchPhotoView(context: android.content.Context, val onSwipe: (Int) -> Unit) :
    androidx.appcompat.widget.AppCompatImageView(context) {
    private var zoom = 1f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var multi = false
    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 5f)
            scaleX = zoom; scaleY = zoom
            if (zoom == 1f) { translationX = 0f; translationY = 0f }
            return true
        }
    })
    init { scaleType = ScaleType.FIT_CENTER }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        pinch.onTouchEvent(event)
        if (event.pointerCount >= 2) multi = true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; lastX = event.x; lastY = event.y; multi = false }
            MotionEvent.ACTION_MOVE -> if (!multi && zoom > 1.01f) {
                translationX = (translationX + event.x - lastX).coerceIn(-width * (zoom - 1f) / 2f, width * (zoom - 1f) / 2f)
                translationY = (translationY + event.y - lastY).coerceIn(-height * (zoom - 1f) / 2f, height * (zoom - 1f) / 2f)
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (!multi && zoom <= 1.01f && abs(event.x - downX) > 90f && abs(event.y - downY) < 100f) {
                    onSwipe(if (event.x < downX) 1 else -1)
                }
            }
        }
        return true
    }
}
