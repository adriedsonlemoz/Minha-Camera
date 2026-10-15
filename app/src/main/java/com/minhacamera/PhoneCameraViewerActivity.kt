package com.minhacamera

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

/** Receptor HTTP multipart MJPEG, sem dependências de decodificação de vídeo. */
class PhoneCameraViewerActivity : AppCompatActivity() {
    private lateinit var image: ImageView
    private lateinit var status: TextView
    @Volatile private var active = false
    @Volatile private var connection: HttpURLConnection? = null
    private var reader: Thread? = null
    private var attempts = 0
    private val frameOnDeck = AtomicReference<Bitmap?>(null)
    private val uiScheduled = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_viewer)
        title = intent.getStringExtra("name") ?: getString(R.string.phone_camera)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        image = findViewById(R.id.phoneImage)
        status = findViewById(R.id.phoneViewerStatus)
        findViewById<Button>(R.id.phoneRetry).setOnClickListener { stop(); start() }
    }
    override fun onResume() { super.onResume(); start() }
    override fun onPause() { stop(); super.onPause() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun start() {
        val uri = PhoneCameraUrls.normalize(intent.getStringExtra("url").orEmpty())
        if (uri == null) { status.setText(R.string.phone_invalid_url); return }
        NetUtil.bindToWifi(this)
        active = true
        val currentAttempt = ++attempts
        status.setText(R.string.phone_connecting)
        reader = Thread({
            var ownConnection: HttpURLConnection? = null
            try {
                if (!active || attempts != currentAttempt) return@Thread
                val conn = (URL(uri).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 8000
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("Accept", "multipart/x-mixed-replace")
                }
                ownConnection = conn
                if (!active || attempts != currentAttempt) return@Thread
                connection = conn
                if (conn.responseCode != 200 || !conn.contentType.orEmpty().startsWith("multipart/x-mixed-replace", true))
                    throw IllegalStateException("Formato de transmissão não suportado")
                BufferedInputStream(conn.inputStream).use { input ->
                    while (active && attempts == currentAttempt) {
                        var line = readAsciiLine(input) ?: break
                        if (line != "--frame") continue
                        var length = -1
                        var jpeg = false
                        while (true) {
                            line = readAsciiLine(input) ?: throw IllegalStateException("Conexão encerrada")
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", true)) {
                                length = line.substringAfter(':').trim().toIntOrNull() ?: -1
                            }
                            if (line.equals("Content-Type: image/jpeg", true)) jpeg = true
                        }
                        if (!jpeg || length !in 1..1_048_576) throw IllegalStateException("Quadro JPEG inválido")
                        val data = ByteArray(length)
                        var offset = 0
                        while (offset < length) {
                            val n = input.read(data, offset, length - offset)
                            if (n <= 0) throw IllegalStateException("Quadro incompleto")
                            offset += n
                        }
                        // Receber e substituir o quadro pendente sem enfileirar dezenas de Runnables.
                        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size,
                            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
                            ?: throw IllegalStateException("Imagem não reconhecida")
                        val old = frameOnDeck.getAndSet(bitmap)
                        old?.recycle()
                        schedulePresentation(currentAttempt)
                    }
                }
                if (active && attempts == currentAttempt) throw IllegalStateException("Transmissão encerrada")
            } catch (_: Exception) {
                runOnUiThread {
                    if (active && attempts == currentAttempt) status.setText(R.string.phone_disconnected)
                }
            } finally {
                if (connection === ownConnection) connection = null
                ownConnection?.disconnect()
            }
        }, "mjpeg-viewer").apply { isDaemon = true; start() }
    }

    private fun schedulePresentation(currentAttempt: Int) {
        if (!uiScheduled.compareAndSet(false, true)) return
        image.post {
            val newest = frameOnDeck.getAndSet(null)
            if (newest != null) {
                if (active && attempts == currentAttempt) {
                    image.setImageBitmap(newest)
                    status.setText(R.string.phone_receiving)
                } else newest.recycle()
            }
            uiScheduled.set(false)
            if (frameOnDeck.get() != null && active && attempts == currentAttempt) schedulePresentation(currentAttempt)
        }
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val text = StringBuilder()
        while (text.length <= 2048) {
            val ch = input.read()
            if (ch == -1) return null
            if (ch == '\n'.code) return text.toString().trimEnd('\r')
            if (ch !in 32..126 && ch != '\r'.code) throw IllegalStateException("Cabeçalho inválido")
            text.append(ch.toChar())
        }
        throw IllegalStateException("Cabeçalho muito longo")
    }

    private fun stop() {
        active = false
        attempts++
        connection?.disconnect()
        connection = null
        reader?.interrupt()
        reader = null
        frameOnDeck.getAndSet(null)?.recycle()
        image.setImageDrawable(null)
    }
}
