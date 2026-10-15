package com.minhacamera

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Biblioteca MCRP do outro celular: consulta, reprodução e download; nunca apaga arquivos. */
class RemoteMultimediaActivity : AppCompatActivity() {
    private lateinit var address: EditText
    private lateinit var list: LinearLayout
    private lateinit var info: TextView
    private var busy = false
    private fun dp(v: Int) = (resources.displayMetrics.density * v + .5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Arquivos do outro celular"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        address = EditText(this).apply {
            setSingleLine(true); hint = "http://192.168.1.2:8765/live/…"; textSize = 12f
            setText(intent.getStringExtra("url") ?: getSharedPreferences("phone_viewer", MODE_PRIVATE).getString("last_url", ""))
        }
        root.addView(address)
        root.addView(Button(this).apply { text = "Consultar gravações e fotos"; setOnClickListener { refresh() } })
        info = TextView(this).apply { text = "O transmissor precisa estar ativo na mesma rede Wi-Fi."; setPadding(0, dp(10), 0, dp(10)) }
        root.addView(info)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (PhoneCameraUrls.normalize(address.text.toString()) != null) refresh()
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    private fun refresh() {
        val live = PhoneCameraUrls.normalize(address.text.toString())
        val url = live?.let { PhoneCameraUrls.mcrpMediaUrl(it) }
        if (url == null) { info.text = "Endereço inválido. Use o QR/URL completo do transmissor."; return }
        if (busy) return
        busy = true
        info.text = "Consultando biblioteca remota…"
        getSharedPreferences("phone_viewer", MODE_PRIVATE).edit().putString("last_url", live).apply()
        Thread({
            val result = runCatching {
                NetUtil.bindToWifi(applicationContext)
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 4500; conn.readTimeout = 7000; conn.instanceFollowRedirects = false
                    require(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                    val bytes = java.io.ByteArrayOutputStream()
                    conn.inputStream.use { stream ->
                        val buffer = ByteArray(4096)
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            require(bytes.size() + n <= 1_000_000) { "Lista excessivamente grande" }
                            bytes.write(buffer, 0, n)
                        }
                    }
                    JSONObject(bytes.toString("UTF-8")).getJSONArray("items")
                } finally { conn.disconnect() }
            }
            runOnUiThread {
                busy = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                list.removeAllViews()
                result.onSuccess { items ->
                    info.text = "${items.length()} arquivo(s) remoto(s). Reprodução e download exigem autorização de pareamento."
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val id = item.optString("id"); val mime = item.optString("mime")
                        val name = item.optString("name").take(150)
                        val size = item.optLong("size")
                        val fileUrl = PhoneCameraUrls.mcrpMediaFileUrl(live, id) ?: continue
                        list.addView(Button(this).apply {
                            text = "${if (mime.startsWith("image/")) "▧" else "▶"} $name  ·  ${size / 1024} KB"
                            isAllCaps = false
                            setOnClickListener { showItem(fileUrl, name, mime, size) }
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
                        })
                    }
                }.onFailure { info.text = "Não foi possível consultar o outro celular: ${it.message}" }
            }
        }, "mcrp-media-list").start()
    }

    private fun showItem(url: String, name: String, mime: String, size: Long) {
        AlertDialog.Builder(this).setTitle(name).setItems(arrayOf("Reproduzir / visualizar", "Baixar para este celular")) { _, index ->
            if (index == 0) {
                if (mime.startsWith("video/")) playVideo(url)
                else if (mime.startsWith("image/")) viewPhoto(url, size)
                else Toast.makeText(this, "Formato sem visualização", Toast.LENGTH_SHORT).show()
            } else download(url, name, mime)
        }.show()
    }
    private fun playVideo(url: String) {
        val video = VideoView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(260))
            setVideoURI(Uri.parse(url))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Vídeo remoto").setView(video)
            .setPositiveButton("Fechar", null).create()
        dialog.setOnDismissListener { video.stopPlayback() }
        dialog.show()
        video.setOnPreparedListener { video.start() }
        video.setOnErrorListener { _, _, _ -> Toast.makeText(this, "Falha na reprodução. Tente baixar o arquivo.", Toast.LENGTH_LONG).show(); true }
        video.requestFocus()
    }
    private fun viewPhoto(url: String, size: Long) {
        if (size > 12_000_000L) { Toast.makeText(this, "Imagem grande: use Baixar.", Toast.LENGTH_LONG).show(); return }
        Thread({
            val bmp = runCatching {
                NetUtil.bindToWifi(applicationContext)
                val conn = URL(url).openConnection() as HttpURLConnection
                try { conn.connectTimeout = 4000; conn.readTimeout = 6000
                    require(conn.responseCode == 200)
                    conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
                } finally { conn.disconnect() }
            }.getOrNull()
            runOnUiThread {
                if (bmp == null || isFinishing) Toast.makeText(this, "Foto indisponível", Toast.LENGTH_LONG).show()
                else AlertDialog.Builder(this).setView(android.widget.ImageView(this).apply {
                    setImageBitmap(bmp); adjustViewBounds = true
                }).setPositiveButton("Fechar", null).show()
            }
        }, "mcrp-photo").start()
    }
    private fun download(url: String, name: String, mime: String) {
        Toast.makeText(this, "Baixando para este celular…", Toast.LENGTH_SHORT).show()
        Thread({
            val saved = runCatching {
                NetUtil.bindToWifi(applicationContext)
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(90)
                var legacyFile: File? = null
                val uri = if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/MinhaCameraRecebidos")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Falha ao criar arquivo")
                } else {
                    val folder = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MinhaCameraRecebidos")
                    check(folder.isDirectory || folder.mkdirs())
                    val file = File.createTempFile("mcrp_", "_${safeName.takeLast(40)}", folder)
                    legacyFile = file
                    FileProvider.getUriForFile(this, "$packageName.recordings", file)
                }
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    try {
                        conn.connectTimeout = 4500; conn.readTimeout = 30_000; conn.instanceFollowRedirects = false
                        require(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                        val output = if (Build.VERSION.SDK_INT >= 29) contentResolver.openOutputStream(uri)!!
                            else legacyFile!!.outputStream()
                        output.use { out -> conn.inputStream.use { it.copyTo(out) } }
                    } finally { conn.disconnect() }
                    if (Build.VERSION.SDK_INT >= 29) contentResolver.update(uri, ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null)
                    uri
                } catch (error: Exception) { if (Build.VERSION.SDK_INT >= 29) contentResolver.delete(uri, null, null) else legacyFile?.delete(); throw error }
            }
            runOnUiThread {
                if (!isFinishing) Toast.makeText(this, if (saved.isSuccess) "Download concluído" else "Erro: ${saved.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }, "mcrp-download").start()
    }
}
