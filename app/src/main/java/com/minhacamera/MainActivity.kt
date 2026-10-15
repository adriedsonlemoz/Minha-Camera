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
    private lateinit var phoneAdapter: CameraAdapter
    private var cams = mutableListOf<Camera>()

    private var scanDialog: AlertDialog? = null
    private var scannerView: BarcodeView? = null
    private var phoneTabSelected = false

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
        val phoneList = findViewById<ListView>(R.id.phoneSavedList)
        phoneList.emptyView = findViewById(R.id.phoneEmpty)
        adapter = CameraAdapter(phoneOnly = false)
        phoneAdapter = CameraAdapter(phoneOnly = true)
        listView.adapter = adapter
        phoneList.adapter = phoneAdapter
        refresh()
        listView.setOnItemClickListener { _, _, pos, _ ->
            openCamera(cams[indices(phoneOnly = false)[pos]])
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            showOptions(indices(phoneOnly = false)[pos]); true
        }
        phoneList.setOnItemClickListener { _, _, pos, _ ->
            openCamera(cams[indices(phoneOnly = true)[pos]])
        }
        phoneList.setOnItemLongClickListener { _, _, pos, _ ->
            showOptions(indices(phoneOnly = true)[pos]); true
        }

        findViewById<View>(R.id.mainMore).setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menu.add(0, 1001, 0, R.string.novidades)
                menu.add(0, 1002, 1, R.string.gravacoes)
                menu.add(0, 1003, 2, R.string.phone_add)
                setOnMenuItemClickListener { item -> onOptionsItemSelected(item) }
                show()
            }
        }
        findViewById<View>(R.id.mainTabCameras).setOnClickListener { showTab(false) }
        findViewById<View>(R.id.mainTabPhone).setOnClickListener { showTab(true) }
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
        showTab(savedInstanceState?.getBoolean("phoneTabSelected") == true)
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
        val text = getString(R.string.welcome_latest_summary)
        AlertDialog.Builder(this)
            .setTitle(R.string.welcome_latest_title)
            .setMessage(text)
            .setPositiveButton(R.string.welcome_latest_continue) { _, _ ->
                prefs.edit().putInt("last_shown_code", versionCode).apply()
            }
            .setOnCancelListener {
                prefs.edit().putInt("last_shown_code", versionCode).apply()
            }
            .show()
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
            val version = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
            } catch (_: Exception) { "?" }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.novidades) + " • $version")
                .setMessage(R.string.novidades_text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun choosePhoneRole() = showTab(true)

    private fun showTab(phone: Boolean) {
        phoneTabSelected = phone
        findViewById<View>(R.id.camerasSection).visibility = if (phone) View.GONE else View.VISIBLE
        findViewById<View>(R.id.phoneSection).visibility = if (phone) View.VISIBLE else View.GONE
        findViewById<View>(R.id.mainTabCameras).setBackgroundResource(
            if (phone) R.drawable.bg_ui_card else R.drawable.bg_ui_selected)
        findViewById<View>(R.id.mainTabPhone).setBackgroundResource(
            if (phone) R.drawable.bg_ui_selected else R.drawable.bg_ui_card)
        findViewById<TextView>(R.id.mainSubtitle).setText(
            if (phone) R.string.ui_phone_title else R.string.ui_home_subtitle)
    }

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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("phoneTabSelected", phoneTabSelected)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        cams = store.load()
        refresh()
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
        phoneAdapter.notifyDataSetChanged()
    }

    private fun indices(phoneOnly: Boolean): List<Int> =
        cams.indices.filter { isPhoneCamera(cams[it]) == phoneOnly }

    private fun isPhoneCamera(camera: Camera): Boolean = camera.url.startsWith("http://", true)

    private fun showPhoneDialog(position: Int? = null) {
        val text = EditText(this).apply {
            hint = getString(R.string.phone_enter_url)
            setSingleLine(false)
            setText(position?.let { cams[it].url }.orEmpty())
            setPadding(28, 22, 28, 22)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (position == null) R.string.phone_add else R.string.editar_camera)
            .setView(text)
            .setPositiveButton(R.string.salvar, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val normalized = PhoneCameraUrls.normalize(text.text.toString())
            if (normalized == null) {
                text.error = getString(R.string.phone_invalid_url)
                return@setOnClickListener
            }
            if (position != null) cams[position] = cams[position].copy(url = normalized)
            else {
                val host = Uri.parse(normalized).host.orEmpty()
                cams.add(Camera("Celular $host", normalized))
            }
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
        if (isPhoneCamera(camera)) return getString(R.string.phone_mjpeg_label)
        val parsed = CameraFormUi.parseRtsp(camera.url)
        val pathLabel = CameraFormUi.normalizePathChoice(parsed.path).ifEmpty { getString(R.string.default_option) }
        val alternativos = if (camera.alts.isEmpty()) {
            getString(R.string.sem_alternativos)
        } else {
            getString(R.string.com_alternativos, camera.alts.size)
        }
        return getString(R.string.resumo_camera_salva, parsed.user.ifBlank { "-" }, pathLabel, alternativos)
    }

    private fun showOptions(pos: Int) {
        val options = arrayOf(
            getString(R.string.editar),
            getString(R.string.copiar_rtsp_com_senha),
            getString(R.string.excluir)
        )
        AlertDialog.Builder(this)
            .setTitle(cams[pos].name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(pos)
                    1 -> copyCameraUrl(cams[pos])
                    else -> confirmDelete(pos)
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

    private inner class CameraAdapter(private val phoneOnly: Boolean) : BaseAdapter() {
        override fun getCount(): Int = indices(phoneOnly).size
        override fun getItem(position: Int): Any = cams[indices(phoneOnly)[position]]
        override fun getItemId(position: Int): Long = indices(phoneOnly)[position].toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_camera, parent, false)
            val realPosition = indices(phoneOnly)[position]
            val camera = cams[realPosition]
            view.findViewById<TextView>(R.id.tvName).text = camera.name
            view.findViewById<TextView>(R.id.tvMasked).text = masked(camera.url)
            view.findViewById<TextView>(R.id.tvInfo).text = infoSummary(camera)
            view.findViewById<MaterialButton>(R.id.btnOpen).setOnClickListener { openCamera(camera) }
            view.findViewById<MaterialButton>(R.id.btnCopy).setOnClickListener { copyCameraUrl(camera) }
            view.findViewById<MaterialButton>(R.id.btnMore).setOnClickListener { showOptions(realPosition) }
            return view
        }
    }
}
