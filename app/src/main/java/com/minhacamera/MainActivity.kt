package com.minhacamera

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var store: CameraStore
    private lateinit var listView: ListView
    private lateinit var adapter: CameraAdapter
    private var cams = mutableListOf<Camera>()
    private val remoteDetails = mutableMapOf<String, String>()

    private var scanDialog: AlertDialog? = null
    private var scannerView: BarcodeView? = null

    private val scanPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openPhoneScanDialog()
        else Toast.makeText(this, R.string.ui_permission_camera, Toast.LENGTH_LONG).show()
    }

    /** Aceita exclusivamente o endereço MJPEG local validado pelo aplicativo. */
    private fun handlePhoneQr(text: String) {
        val stream = PhoneCameraUrls.normalize(text)
        if (stream == null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.phone_qr_invalid_title)
                .setMessage(R.string.phone_qr_invalid_message)
                .setPositiveButton(R.string.phone_scan_again) { _, _ -> startPhoneQrScanner() }
                .setNegativeButton(R.string.cancelar, null)
                .show()
            return
        }
        val existing = cams.firstOrNull { it.url == stream }
        val label = if (existing != null) existing.name else {
            val host = Uri.parse(stream).host.orEmpty()
            "Celular $host".also { name ->
                cams.add(Camera(name, stream))
                store.save(cams)
                refresh()
            }
        }
        startActivity(Intent(this, PhoneCameraViewerActivity::class.java)
            .putExtra("name", label).putExtra("url", stream))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        NetUtil.bindToWifi(this)
        window.decorView.post { showPreviousCrashIfAny() }

        store = CameraStore(this)
        cams = store.load()

        listView = findViewById(R.id.list)
        listView.emptyView = findViewById(R.id.empty)
        adapter = CameraAdapter()
        listView.adapter = adapter
        refresh()
        listView.setOnItemClickListener { _, _, pos, _ -> openCamera(cams[pos]) }
        listView.setOnItemLongClickListener { _, _, pos, _ -> showOptions(pos); true }
        findViewById<View>(R.id.mainMore).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.mainMedia).setOnClickListener {
            startActivity(Intent(this, MultimediaActivity::class.java))
        }
        findViewById<View>(R.id.roleTransmit).setOnClickListener {
            startActivity(Intent(this, PhoneCameraActivity::class.java))
        }
        findViewById<View>(R.id.roleAssistir).setOnClickListener { startPhoneQrScanner() }
        findViewById<View>(R.id.phoneManual).setOnClickListener { showPhoneDialog() }
        findViewById<View>(R.id.mainAdd).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java).putExtra("manual", true))
        }
        findViewById<View>(R.id.mainScan).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        if (savedInstanceState == null) window.decorView.postDelayed({ showWelcomeAfterUpdate() }, 600)
    }

    /** Mostra novidades somente uma vez por versionCode, inclusive na primeira instalação. */
    private fun showWelcomeAfterUpdate() {
        if (isFinishing || isDestroyed) return
        val versionCode = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
        } catch (_: Exception) { return }
        val prefs = getSharedPreferences("welcome_versions", MODE_PRIVATE)
        if (prefs.getInt("last_shown_code", -1) >= versionCode) return
        prefs.edit().putInt("last_shown_code", versionCode).apply()
        startActivity(Intent(this, WelcomeActivity::class.java))
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1001, 0, R.string.novidades)
        menu.add(0, 1002, 1, R.string.gravacoes)
        menu.add(0, 1003, 2, R.string.phone_add)
        menu.add(0, 1004, 3, R.string.phone_broadcast)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1003) { showPhoneDialog(); return true }
        if (item.itemId == 1004) { choosePhoneRole(); return true }
        if (item.itemId == 1002) {
            startActivity(Intent(this, RecordingsActivity::class.java)); return true
        }
        if (item.itemId == 1001) {
            startActivity(Intent(this, WelcomeActivity::class.java).putExtra("full_history", true))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun choosePhoneRole() { startActivity(Intent(this, PhoneCameraActivity::class.java)) }

    private fun startPhoneQrScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            scanPermission.launch(Manifest.permission.CAMERA)
        } else openPhoneScanDialog()
    }

    /** Leitor QR incorporado ao modal em retrato, sem CaptureActivity em tela inteira. */
    private fun openPhoneScanDialog() {
        if (isFinishing || isDestroyed || scanDialog?.isShowing == true) return
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_phone_scan, null)
        val barcode = content.findViewById<BarcodeView>(R.id.phoneScanner)
        barcode.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        val dialog = AlertDialog.Builder(this).setView(content).create()
        scanDialog = dialog
        scannerView = barcode
        barcode.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val payload = result?.text ?: return
                // A mesma Activity permanece em modo retrato; só o diálogo é fechado.
                dialog.dismiss()
                handlePhoneQr(payload)
            }
            override fun possibleResultPoints(resultPoints: List<ResultPoint>?) = Unit
        })
        content.findViewById<View>(R.id.scanCancel).setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            barcode.pause()
            if (scannerView === barcode) scannerView = null
            if (scanDialog === dialog) scanDialog = null
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.91f).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        barcode.resume()
    }

    override fun onResume() {
        super.onResume()
        cams = store.load()
        refresh()
        refreshDeviceMetadata()
        if (scanDialog?.isShowing == true) scannerView?.resume()
    }

    override fun onPause() {
        scannerView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        scanDialog?.dismiss()
        scanDialog = null
        scannerView = null
        super.onDestroy()
    }

    private fun refresh() {
        adapter.notifyDataSetChanged()
    }

    /** Consulta apenas /control/<token>/status para celulares; nunca presume capacidades RTSP. */
    private fun refreshDeviceMetadata() {
        val phones = cams.filter { isPhoneCamera(it) }.take(20)
        if (phones.isEmpty()) return
        Thread({
            NetUtil.bindToWifi(applicationContext)
            for (camera in phones) {
                val endpoint = PhoneCameraUrls.statusUrl(camera.url) ?: continue
                try {
                    val conn = java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection
                    try {
                        conn.connectTimeout = 1000; conn.readTimeout = 1000; conn.useCaches = false
                        if (conn.responseCode != 200) continue
                        val model = conn.getHeaderField("X-Device-Model")?.take(48)?.trim().orEmpty()
                        val resolution = conn.getHeaderField("X-Resolution")?.take(18)?.trim().orEmpty()
                        val fps = conn.getHeaderField("X-FPS-Target")?.toIntOrNull()
                        val details = buildList {
                            if (model.isNotBlank()) add(model)
                            if (resolution.matches(Regex("[0-9]{1,5}x[0-9]{1,5}"))) add(resolution.replace('x','×'))
                            if (fps != null && fps > 0) add("$fps FPS alvo")
                        }.joinToString(" · ")
                        if (details.isNotBlank()) runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                remoteDetails[camera.url] = details
                                refresh()
                            }
                        }
                    } finally { conn.disconnect() }
                } catch (_: Exception) { /* aparelho offline; mantém a informação previamente conhecida */ }
            }
        }, "device-metadata").apply { isDaemon = true; start() }
    }


    private fun isPhoneCamera(camera: Camera): Boolean = camera.url.startsWith("http://", true)

    private fun showPhoneDialog(position: Int? = null) {
        val nameInput = EditText(this).apply {
            hint = getString(R.string.home_device_phone)
            setSingleLine(true)
            setText(position?.let { cams[it].name }.orEmpty())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val linkInput = EditText(this).apply {
            hint = getString(R.string.phone_enter_url)
            setSingleLine(false)
            setText(position?.let { cams[it].url }.orEmpty())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val fields = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(28, 12, 28, 12)
            addView(nameInput)
            addView(linkInput)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (position == null) R.string.phone_add else R.string.editar_camera)
            .setView(fields)
            .setPositiveButton(R.string.salvar, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val normalized = PhoneCameraUrls.normalize(linkInput.text.toString())
            if (normalized == null) {
                linkInput.error = getString(R.string.phone_invalid_url)
                return@setOnClickListener
            }
            val host = Uri.parse(normalized).host.orEmpty()
            val desiredName = nameInput.text.toString().trim().take(64).ifBlank { "Celular $host" }
            if (position != null) cams[position] = cams[position].copy(name = desiredName, url = normalized)
            else cams.add(Camera(desiredName, normalized))
            store.save(cams)
            refresh()
            dialog.dismiss()
            Toast.makeText(this, R.string.phone_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPreviousCrashIfAny() {
        val prefs = getSharedPreferences("private_crash_diagnostics", MODE_PRIVATE)
        val report = prefs.getString("last", null) ?: return
        prefs.edit().remove("last").apply()
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_diagnostic_title)
            .setMessage(getString(R.string.crash_diagnostic_intro) + "\n\n" + report)
            .setPositiveButton(R.string.crash_diagnostic_copy) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Minha Camera crash", report))
                Toast.makeText(this, R.string.crash_diagnostic_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun openCamera(camera: Camera) {
        if (isPhoneCamera(camera)) {
            startActivity(Intent(this, PhoneCameraViewerActivity::class.java)
                .putExtra("name", camera.name).putExtra("url", camera.url))
            return
        }
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("name", camera.name)
                .putExtra("url", camera.url)
                .putExtra("alts", camera.alts.toTypedArray())
        )
    }

    private fun copyCameraUrl(camera: Camera) {
        CameraFormUi.copyText(this, camera.url, getString(R.string.phone_stream_url), true)
        Toast.makeText(this, if (isPhoneCamera(camera)) R.string.phone_url_copied
            else R.string.endereco_copiado, Toast.LENGTH_SHORT).show()
    }

    private fun masked(url: String): String {
        if (url.startsWith("http://", true)) {
            return try {
                val parsed = Uri.parse(url)
                "http://${parsed.host}:${parsed.port}/live/••••••"
            } catch (_: Exception) { "MJPEG • Endereço oculto" }
        }
        return try {
            val u = Uri.parse(url)
            val host = u.host ?: return url
            val port = if (u.port != -1) ":${u.port}" else ""
            "rtsp://$host$port${u.path ?: ""}${u.query?.let { "?$it" } ?: ""}"
        } catch (_: Exception) {
            url
        }
    }

    private fun infoSummary(camera: Camera): String {
        if (isPhoneCamera(camera)) {
            val known = remoteDetails[camera.url] ?: getString(R.string.home_resolution_unknown)
            return getString(R.string.home_device_phone) + " · " + known
        }
        val parsed = CameraFormUi.parseRtsp(camera.url)
        val pathLabel = CameraFormUi.normalizePathChoice(parsed.path).ifEmpty { getString(R.string.default_option) }
        val alternativos = if (camera.alts.isEmpty()) {
            getString(R.string.sem_alternativos)
        } else {
            getString(R.string.com_alternativos, camera.alts.size)
        }
        return getString(R.string.home_device_ip) + " · " +
            getString(R.string.resumo_camera_salva, parsed.user.ifBlank { "-" }, pathLabel, alternativos)
    }

    private fun splitRtspUri(u: String): Pair<Int, String> {
        val p = Uri.parse(u)
        val port = if (p.port != -1) p.port else 554
        val path = (p.encodedPath ?: "/") + (p.encodedQuery?.let { "?$it" } ?: "")
        return Pair(port, path)
    }

    private fun altRtspPaths(path: String): List<String> {
        val out = mutableListOf<String>()
        fun add(p: String) { if (p != path) out.add(p) }
        when {
            path.contains("subtype=0") -> add(path.replace("subtype=0", "subtype=1"))
            path.endsWith("/Streaming/Channels/101") -> add("/Streaming/Channels/102")
            path == "/stream1" -> add("/stream2")
            path == "/onvif1" -> add("/onvif2")
            path == "/live/ch00_0" -> add("/live/ch00_1")
            path == "/h264Preview_01_main" -> add("/h264Preview_01_sub")
            path == "/11" -> add("/12")
            path == "/ch1/main" -> add("/ch1/sub")
            path.contains("/live/main") -> add(path.replace("main", "sub"))
        }
        return out
    }

    private fun analyzeAndApplyCamera(pos: Int) {
        val camera = cams.getOrNull(pos) ?: return
        if (isPhoneCamera(camera)) return
        val parsed = CameraFormUi.parseRtsp(camera.url)
        if (parsed.host.isBlank()) {
            Toast.makeText(this, R.string.ip_invalido, Toast.LENGTH_LONG).show()
            return
        }
        val currentPath = CameraFormUi.normalizePathChoice(parsed.path)
        val paths = (listOf(currentPath).filter { it.isNotEmpty() } + RtspProbe.PATHS).distinct()
        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.rtsp_analyze_apply)
            .setMessage(getString(R.string.consultando_onvif))
            .setCancelable(false)
            .create()
        dlg.show()

        Thread {
            var okUrl: String? = null
            var okAlts: List<String> = camera.alts
            var warn = ""
            var authFails = 0
            var noReply = 0
            var firstDebug = ""
            val details = StringBuilder()

            val on = OnvifClient.findStreamUri(parsed.host, parsed.user, parsed.pass, emptyList())
            details.append("ONVIF: ").append(on.log.ifEmpty { "sem resposta" }).append('\n')
            val streams = OnvifClient.order(on.streams)
            if (streams.isNotEmpty()) {
                val urls = streams.map { st ->
                    val (pt, ph) = splitRtspUri(st.uri)
                    CameraFormUi.buildRtspUrl(parsed.host, pt, parsed.user, parsed.pass, ph)
                }
                okUrl = urls.firstOrNull()
                okAlts = urls.drop(1)
                if (streams.firstOrNull()?.encoding?.contains("264") != true)
                    warn = getString(R.string.aviso_h265)
            }

            if (okUrl == null) {
                for ((i, path) in paths.withIndex()) {
                    if (Thread.currentThread().isInterrupted) break
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            dlg.setMessage(getString(R.string.testando_caminho, i + 1, paths.size))
                        }
                    }
                    val r = RtspProbe.probe(parsed.host, parsed.port, parsed.user, parsed.pass, path)
                    if (i == 0 && firstDebug.isEmpty()) firstDebug = r.debug
                    details.append(path)
                        .append("  →  ").append(r.detail)
                        .append(if (r.videoCodec != null) " [${r.videoCodec}]" else "")
                        .append('\n')
                    when (r.status) {
                        ProbeStatus.OK -> {
                            val primary = CameraFormUi.buildRtspUrl(parsed.host, parsed.port, parsed.user, parsed.pass, path)
                            val altCandidates = altRtspPaths(path)
                            val verifiedH264 = if (r.videoCodec == "H265") {
                                altCandidates.firstOrNull { candidate ->
                                    val check = RtspProbe.probe(parsed.host, parsed.port, parsed.user, parsed.pass, candidate)
                                    details.append(candidate).append(" → ").append(check.detail)
                                        .append(" [").append(check.videoCodec ?: "?").append("]\n")
                                    check.status == ProbeStatus.OK && check.videoCodec == "H264"
                                }
                            } else null
                            okUrl = verifiedH264?.let {
                                CameraFormUi.buildRtspUrl(parsed.host, parsed.port, parsed.user, parsed.pass, it)
                            } ?: primary
                            okAlts = (listOf(primary) + altCandidates.map {
                                CameraFormUi.buildRtspUrl(parsed.host, parsed.port, parsed.user, parsed.pass, it)
                            }).filter { it != okUrl }.distinct()
                            if (r.videoCodec == "H265" && verifiedH264 == null) warn = getString(R.string.aviso_h265)
                            break
                        }
                        ProbeStatus.AUTH_FAIL -> {
                            noReply = 0
                            authFails++
                            if (authFails >= 2) break
                        }
                        ProbeStatus.NOT_FOUND -> {
                            authFails = 0
                            noReply = 0
                        }
                        ProbeStatus.ERROR -> {
                            authFails = 0
                            if (r.detail.startsWith("sem resposta")) {
                                noReply++
                                if (noReply >= 3) break
                            } else noReply = 0
                        }
                    }
                }
            }

            val report = details.toString() +
                (if (firstDebug.isNotEmpty()) "\n" + getString(R.string.detalhes_tecnicos) + "\n" + firstDebug else "")
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                dlg.dismiss()
                if (okUrl != null) {
                    cams[pos] = Camera(camera.name, okUrl!!, okAlts)
                    store.save(cams)
                    refresh()
                    val message = buildString {
                        append(getString(R.string.rtsp_apply_done))
                        if (warn.isNotEmpty()) append("\n\n").append(warn)
                        append("\n\n").append(report)
                    }
                    AlertDialog.Builder(this)
                        .setTitle(camera.name)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } else {
                    val msg = when {
                        authFails >= 2 -> R.string.falha_login
                        noReply >= 3 -> R.string.falha_sem_resposta
                        else -> R.string.falha_caminho
                    }
                    AlertDialog.Builder(this)
                        .setTitle(camera.name)
                        .setMessage(getString(msg) + "\n\n" + report)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }.start()
    }

    private fun showOptions(pos: Int) {
        val camera = cams[pos]
        val options = if (isPhoneCamera(camera)) {
            arrayOf(
                getString(R.string.editar),
                getString(R.string.copiar_rtsp_com_senha),
                getString(R.string.excluir)
            )
        } else {
            arrayOf(
                getString(R.string.editar),
                getString(R.string.rtsp_analyze_apply),
                getString(R.string.copiar_rtsp_com_senha),
                getString(R.string.excluir)
            )
        }
        AlertDialog.Builder(this)
            .setTitle(camera.name)
            .setItems(options) { _, which ->
                if (isPhoneCamera(camera)) {
                    when (which) {
                        0 -> showEditDialog(pos)
                        1 -> copyCameraUrl(camera)
                        else -> confirmDelete(pos)
                    }
                } else {
                    when (which) {
                        0 -> showEditDialog(pos)
                        1 -> analyzeAndApplyCamera(pos)
                        2 -> copyCameraUrl(camera)
                        else -> confirmDelete(pos)
                    }
                }
            }
            .show()
    }

    private fun showEditDialog(pos: Int) {
        if (isPhoneCamera(cams[pos])) { showPhoneDialog(pos); return }
        val c = cams[pos]
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_manual_camera, null)
        val etName = view.findViewById<TextInputEditText>(R.id.etName)
        val etIp = view.findViewById<TextInputEditText>(R.id.etIp)
        val etPort = view.findViewById<TextInputEditText>(R.id.etPort)
        val etUser = view.findViewById<TextInputEditText>(R.id.etUser)
        val etPass = view.findViewById<TextInputEditText>(R.id.etPass)
        val etPath = view.findViewById<MaterialAutoCompleteTextView>(R.id.etPath)
        val btnCopyPass = view.findViewById<MaterialButton>(R.id.btnCopyPass)
        val btnPastePass = view.findViewById<MaterialButton>(R.id.btnPastePass)
        val parsed = CameraFormUi.parseRtsp(c.url)

        etName.setText(c.name)
        etIp.setText(if (parsed.host.isBlank()) c.url else parsed.host)
        etPort.setText(parsed.port.toString())
        etUser.setText(parsed.user.ifBlank { "admin" })
        etPass.setText(parsed.pass)
        CameraFormUi.setupPasswordTools(this, etPass, btnCopyPass, btnPastePass)
        CameraFormUi.setupPathSuggestions(this, etPath, parsed.path)

        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.editar_camera)
            .setView(view)
            .setPositiveButton(R.string.salvar, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val target = etIp.text?.toString()?.trim().orEmpty()
            val name = etName.text?.toString()?.trim().orEmpty().ifEmpty { c.name }
            if (target.startsWith("rtsp://", ignoreCase = true)) {
                cams[pos] = Camera(name, target, if (target == c.url) c.alts else emptyList())
                store.save(cams)
                refresh()
                dlg.dismiss()
                return@setOnClickListener
            }
            if (!Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(target)) {
                Toast.makeText(this, R.string.ip_invalido, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val port = etPort.text?.toString()?.trim()?.toIntOrNull() ?: 554
            val user = etUser.text?.toString()?.trim().orEmpty().ifEmpty { "admin" }
            val pass = etPass.text?.toString().orEmpty()
            val pathChoice = etPath.text?.toString().orEmpty()
            val url = CameraFormUi.buildRtspUrl(target, port, user, pass, pathChoice)
            cams[pos] = Camera(name, url, if (url == c.url) c.alts else emptyList())
            store.save(cams)
            refresh()
            dlg.dismiss()
        }
    }

    private fun confirmDelete(pos: Int) {
        AlertDialog.Builder(this)
            .setTitle(cams[pos].name)
            .setMessage(R.string.excluir_pergunta)
            .setPositiveButton(R.string.excluir) { _, _ ->
                cams.removeAt(pos)
                store.save(cams)
                refresh()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private inner class CameraAdapter : BaseAdapter() {
        override fun getCount(): Int = cams.size
        override fun getItem(position: Int): Any = cams[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_camera, parent, false)
            val realPosition = position
            val camera = cams[realPosition]
            val phone = isPhoneCamera(camera)
            view.findViewById<TextView>(R.id.tvName).text = (if (phone) "📱 " else "📷 ") + camera.name
            view.findViewById<TextView>(R.id.tvType).text = if (phone) "Celular" else "Câmera IP"
            view.findViewById<TextView>(R.id.tvMasked).text = masked(camera.url)
            view.findViewById<TextView>(R.id.tvInfo).text = infoSummary(camera)
            view.findViewById<MaterialButton>(R.id.btnOpen).setOnClickListener { openCamera(camera) }
            view.findViewById<MaterialButton>(R.id.btnCopy).setOnClickListener { copyCameraUrl(camera) }
            view.findViewById<MaterialButton>(R.id.btnEdit).setOnClickListener { showEditDialog(realPosition) }
            view.findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener { confirmDelete(realPosition) }
            view.findViewById<MaterialButton>(R.id.btnCopy).apply {
                text = getString(R.string.ui_copy_short)
                contentDescription = if (isPhoneCamera(camera)) getString(R.string.ui_link_copy)
                    else getString(R.string.copiar_rtsp)
            }
            return view
        }
    }
}
