package com.minhacamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.content.Context
import android.net.wifi.WifiManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.text.TextUtils
import android.view.Gravity
import android.graphics.Typeface
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
    private var requestSerial = 0
    private var chosenName = "Nenhum aparelho"
    private var chosenUrl = ""
    private lateinit var deviceLabel: TextView
    private lateinit var connectionStatus: TextView
    private lateinit var switchDevice: Button
    private var scanner: BarcodeView? = null
    private var scannerDialog: AlertDialog? = null
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openQrScanner() else Toast.makeText(this, "Permissão da câmera necessária para ler QR.", Toast.LENGTH_LONG).show()
    }
    private fun dp(v: Int) = (resources.displayMetrics.density * v + .5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (getSharedPreferences(CameraTheme.PREFS, MODE_PRIVATE).getString(CameraTheme.KEY, CameraTheme.SYSTEM) == CameraTheme.AMOLED)
            setTheme(R.style.Theme_MinhaCamera_Amoled)
        super.onCreate(savedInstanceState)
        title = "Remoto · Arquivos"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(getColor(R.color.app_background))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(12))
            setBackgroundResource(R.drawable.bg_ui_card)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
        header.addView(TextView(this).apply {
            text = "Celular selecionado"
            textSize = 12f
            setTextColor(getColor(R.color.ui_muted))
        })
        deviceLabel = TextView(this).apply {
            text = "Nenhum aparelho"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(getColor(R.color.app_text))
            setPadding(0, dp(5), 0, dp(5))
        }
        header.addView(deviceLabel)
        connectionStatus = TextView(this).apply {
            text = "● Desconectado"
            textSize = 13f
            setTextColor(getColor(R.color.ui_muted))
            setPadding(0, 0, 0, dp(8))
        }
        header.addView(connectionStatus)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        switchDevice = Button(this).apply {
            text = "▣  Aparelhos"
            isAllCaps = false
            setOnClickListener { chooseSavedDevice() }
        }
        row.addView(switchDevice, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(Button(this).apply {
            text = "↻  Atualizar"
            isAllCaps = false
            setOnClickListener { refresh() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        header.addView(row)
        val addRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addRow.addView(Button(this).apply {
            text = "＋  Endereço"
            isAllCaps = false
            setOnClickListener { showManualConnection() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        addRow.addView(Button(this).apply {
            text = "▦  Ler QR"
            isAllCaps = false
            setOnClickListener { startQrScanner() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) })
        header.addView(addRow)
        content.addView(header)
        address = EditText(this).apply {
            setSingleLine(true)
            visibility = View.GONE // Credencial não fica exposta no cabeçalho.
            setText(intent.getStringExtra("url") ?: getSharedPreferences("phone_viewer", MODE_PRIVATE).getString("last_url", ""))
        }
        header.addView(address)
        info = TextView(this).apply {
            text = "Escolha um celular salvo, leia o QR ou informe a URL de pareamento. Só arquivos publicados pelo transmissor ficam acessíveis."
            setTextColor(getColor(R.color.ui_muted))
            textSize = 12.5f
            setPadding(dp(4), 0, 0, dp(10))
        }
        content.addView(info)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)

        val supplied = PhoneCameraUrls.normalize(address.text.toString())
        if (supplied != null) selectDevice(supplied, deviceName(supplied), true)
        else {
            val amount = savedPhones().size
            connectionStatus.text = "● Desconectado · $amount celular(es) salvo(s)"
            if (amount > 0) info.text = "Toque em Aparelhos para escolher uma conexão salva, ou leia o QR do transmissor."
        }
    }

    private fun savedPhones(): List<Camera> {
        val store = CameraStore(this).load().filter { PhoneCameraUrls.normalize(it.url) != null }
        val last = PhoneCameraUrls.normalize(getSharedPreferences("phone_viewer", MODE_PRIVATE).getString("last_url", "").orEmpty())
        return (store + (if (last != null && store.none { it.url == last }) listOf(Camera("Última conexão", last)) else emptyList()))
            .distinctBy { it.url }.take(50)
    }

    private fun deviceName(url: String): String = savedPhones().firstOrNull { it.url == url }?.name
        ?: "Celular ${Uri.parse(url).host.orEmpty()}"

    private fun chooseSavedDevice() {
        val phones = savedPhones()
        if (phones.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Nenhum celular salvo")
                .setMessage("Adicione o endereço autenticado do transmissor ou leia o QR exibido nele. Encontrar um dispositivo na rede não concede acesso aos arquivos.")
                .setPositiveButton("Adicionar endereço") { _, _ -> showManualConnection() }
                .setNeutralButton("Ler QR") { _, _ -> startQrScanner() }
                .setNegativeButton("Cancelar", null).show()
            return
        }
        val labels = phones.map { "${it.name} · ${Uri.parse(it.url).host ?: "Wi-Fi"}${if (it.url == chosenUrl) " · selecionado" else ""}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Celulares pareados")
            .setItems(labels) { _, index -> selectDevice(phones[index].url, phones[index].name, true) }
            .setNeutralButton("Adicionar outro") { _, _ -> showManualConnection() }
            .setNegativeButton("Fechar", null).show()
    }

    private fun showManualConnection() {
        val input = EditText(this).apply {
            hint = "http://192.168.1.2:8765/live/…"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText("")
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }
        AlertDialog.Builder(this).setTitle("Conectar outro celular")
            .setMessage("Cole a URL completa de pareamento, com o código privado. O endereço será validado e testado antes de salvar.")
            .setView(input)
            .setPositiveButton("Conectar") { _, _ ->
                val valid = PhoneCameraUrls.normalize(input.text.toString())
                if (valid == null) Toast.makeText(this, "Endereço inválido. Use o QR/URL do Minha Câmera.", Toast.LENGTH_LONG).show()
                else selectDevice(valid, deviceName(valid), true)
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun selectDevice(url: String, name: String, connect: Boolean) {
        val normalized = PhoneCameraUrls.normalize(url) ?: return
        chosenUrl = normalized
        chosenName = name.take(70)
        address.setText(normalized)
        deviceLabel.text = chosenName
        connectionStatus.text = "● Desconectado · Aguardando resposta"
        if (connect) refresh()
    }

    private fun startQrScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openQrScanner()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun openQrScanner() {
        if (isFinishing || isDestroyed || scannerDialog?.isShowing == true) return
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_phone_scan, null)
        val barcode = view.findViewById<BarcodeView>(R.id.phoneScanner)
        barcode.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        val dialog = AlertDialog.Builder(this).setView(view).create()
        scannerDialog = dialog
        scanner = barcode
        barcode.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val valid = PhoneCameraUrls.normalize(result?.text.orEmpty())
                dialog.dismiss()
                if (valid == null) Toast.makeText(this@RemoteMultimediaActivity, "QR inválido para a câmera.", Toast.LENGTH_LONG).show()
                else selectDevice(valid, deviceName(valid), true)
            }
            override fun possibleResultPoints(resultPoints: List<ResultPoint>?) = Unit
        })
        view.findViewById<View>(R.id.scanCancel).setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { barcode.pause(); if (scanner === barcode) scanner = null; scannerDialog = null }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.91f).toInt(), -2)
        barcode.resume()
    }

    override fun onPause() { scanner?.pause(); super.onPause() }
    override fun onResume() { super.onResume(); if (scannerDialog?.isShowing == true) scanner?.resume() }
    override fun onDestroy() {
        requestSerial++
        scannerDialog?.dismiss()
        scannerDialog = null
        scanner = null
        super.onDestroy()
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
        if (url == null || live == null) {
            connectionStatus.text = "● Desconectado · Endereço inválido"
            info.text = "Escolha um celular salvo, leia o QR ou informe a URL completa do transmissor."
            return
        }
        val serial = ++requestSerial
        busy = true
        connectionStatus.text = "● Conectando…"
        info.text = "Consultando biblioteca remota…"
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
                if (isFinishing || isDestroyed || serial != requestSerial) return@runOnUiThread
                list.removeAllViews()
                result.onSuccess { items ->
                    connectionStatus.text = "● Conectado · ${Uri.parse(live).host ?: "Rede local"}"
                    getSharedPreferences("phone_viewer", MODE_PRIVATE).edit().putString("last_url", live).apply()
                    val existing = CameraStore(this).load()
                    if (existing.none { it.url == live }) CameraStore(this).save(existing + Camera(chosenName, live))
                    info.text = "${items.length()} arquivo(s) do celular conectado. Abra ou baixe uma cópia."
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
                    connectionStatus.text = "● Desconectado · Sem resposta válida"
                    val reason = when {
                        it.message?.contains("HTTP 403") == true || it.message?.contains("HTTP 401") == true ->
                            "Acesso não autorizado. Leia novamente o QR do transmissor."
                        else -> "Verifique se o transmissor está ativo na mesma rede Wi-Fi e toque em Atualizar."
                    }
                    info.text = reason
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
