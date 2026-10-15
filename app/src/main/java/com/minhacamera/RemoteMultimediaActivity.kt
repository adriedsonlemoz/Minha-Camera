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
        if (getSharedPreferences(CameraTheme.PREFS, MODE_PRIVATE).getString(CameraTheme.KEY, CameraTheme.SYSTEM) == CameraTheme.AMOLED)
            setTheme(R.style.Theme_MinhaCamera_Amoled)
        super.onCreate(savedInstanceState)
        title = "Arquivos do outro celular"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(getColor(R.color.app_background))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)

        content.addView(TextView(this).apply {
            text = "Consulte, reproduza e baixe fotos e vídeos do transmissor pela mesma rede Wi‑Fi."
            textSize = 14f
            setTextColor(getColor(R.color.ui_muted))
            setPadding(0, 0, 0, dp(14))
        })

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundResource(R.drawable.bg_ui_card)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        address = EditText(this).apply {
            setSingleLine(true)
            hint = "http://192.168.1.2:8765/live/..."
            textSize = 13f
            setText(intent.getStringExtra("url") ?: getSharedPreferences("phone_viewer", MODE_PRIVATE).getString("last_url", ""))
        }
        header.addView(TextView(this).apply { text = "Endereço do transmissor"; textSize = 17f; setTextColor(getColor(R.color.app_text)) })
        header.addView(TextView(this).apply { text = "Use o QR/URL do outro celular para abrir a biblioteca remota."; textSize = 12.5f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, dp(4), 0, dp(10)) })
        header.addView(address)
        header.addView(Button(this).apply {
            text = "Atualizar biblioteca"
            isAllCaps = false
            setOnClickListener { refresh() }
        }.also { (it.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = dp(10) })
        content.addView(header)

        info = TextView(this).apply {
            text = "O transmissor precisa estar ativo e autenticado na mesma rede Wi‑Fi."
            setTextColor(getColor(R.color.ui_muted))
            textSize = 13f
            setPadding(dp(4), 0, 0, dp(10))
        }
        content.addView(info)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)

        if (PhoneCameraUrls.normalize(address.text.toString()) != null) refresh()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun makeCard(title: String, subtitle: String, openAction: () -> Unit, downloadAction: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.bg_ui_card)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            addView(TextView(context).apply { text = title; textSize = 17f; setTextColor(getColor(R.color.app_text)) })
            addView(TextView(context).apply { text = subtitle; textSize = 12.5f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, dp(5), 0, dp(12)) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Button(context).apply {
                    text = "Abrir"
                    isAllCaps = false
                    setOnClickListener { openAction() }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(Button(context).apply {
                    text = "Baixar"
                    isAllCaps = false
                    setOnClickListener { downloadAction() }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
            })
        }
    }

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
                    info.text = "${items.length()} arquivo(s) remoto(s). Abra para visualizar ou baixe uma cópia neste celular."
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val id = item.optString("id")
                        val mime = item.optString("mime")
                        val name = item.optString("name").take(150)
                        val size = item.optLong("size")
                        val fileUrl = PhoneCameraUrls.mcrpMediaFileUrl(live, id) ?: continue
                        val typeLabel = when {
                            mime.startsWith("image/") -> "Foto"
                            mime.startsWith("video/") -> "Vídeo"
                            else -> "Arquivo"
                        }
                        list.addView(makeCard(
                            name,
                            "$typeLabel • ${size / 1024} KB",
                            { openItem(fileUrl, mime, size) },
                            { download(fileUrl, name, mime) }
                        ))
                    }
                    if (items.length() == 0) {
                        list.addView(TextView(this).apply {
                            text = "Nenhum arquivo disponível no transmissor agora."
                            setTextColor(getColor(R.color.ui_muted))
                            setPadding(dp(6), dp(8), 0, 0)
                        })
                    }
                }.onFailure {
                    info.text = "Não foi possível consultar o outro celular: ${it.message}"
                }
            }
        }, "mcrp-media-list").start()
    }

    private fun openItem(url: String, mime: String, size: Long) {
        if (mime.startsWith("video/")) playVideo(url)
        else if (mime.startsWith("image/")) viewPhoto(url, size)
        else Toast.makeText(this, "Formato sem visualização", Toast.LENGTH_SHORT).show()
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
                try {
                    conn.connectTimeout = 4000; conn.readTimeout = 6000
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
                        conn.connectTimeout = 5000; conn.readTimeout = 12000
                        require(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                        contentResolver.openOutputStream(uri, "w")!!.use { out -> conn.inputStream.use { it.copyTo(out) } }
                    } finally { conn.disconnect() }
                    if (Build.VERSION.SDK_INT >= 29) {
                        contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    }
                    legacyFile?.absolutePath ?: uri.toString()
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= 29) runCatching { contentResolver.delete(uri, null, null) }
                    throw e
                }
            }
            runOnUiThread {
                saved.onSuccess { Toast.makeText(this, "Salvo: $it", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(this, "Falha ao baixar: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }, "mcrp-download").start()
    }
}
