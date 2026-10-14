package com.minhacamera

import android.content.Intent
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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var store: CameraStore
    private lateinit var listView: ListView
    private lateinit var adapter: CameraAdapter
    private var cams = mutableListOf<Camera>()

    /** A leitura fica restrita ao QR do próprio aplicativo, sem abrir URLs externas. */
    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@registerForActivityResult
        val stream = PhoneCameraUrls.normalize(text)
        if (stream == null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.phone_qr_invalid_title)
                .setMessage(R.string.phone_qr_invalid_message)
                .setPositiveButton(R.string.phone_scan_again) { _, _ -> startPhoneQrScanner() }
                .setNegativeButton(R.string.cancelar, null)
                .show()
        } else {
            // Preserve os cadastros existentes. Reusar URL impede entradas duplicadas.
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = getString(R.string.minhas_cameras)
        NetUtil.bindToWifi(this)

        store = CameraStore(this)
        cams = store.load()

        listView = findViewById(R.id.list)
        listView.emptyView = findViewById(R.id.empty)
        adapter = CameraAdapter()
        listView.adapter = adapter
        refresh()

        listView.setOnItemClickListener { _, _, pos, _ ->
            openCamera(cams[pos])
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            showOptions(pos)
            true
        }

        findViewById<View>(R.id.btnPhoneBroadcast).setOnClickListener { choosePhoneRole() }
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java).putExtra("manual", true))
        }
        findViewById<View>(R.id.fabScan).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
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

    private fun choosePhoneRole() {
        AlertDialog.Builder(this)
            .setTitle(R.string.phone_role_title)
            .setItems(arrayOf(getString(R.string.phone_role_transmit), getString(R.string.phone_role_view))) { _, role ->
                if (role == 0) startActivity(Intent(this, PhoneCameraActivity::class.java))
                else startPhoneQrScanner()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun startPhoneQrScanner() {
        qrScanner.launch(
            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.phone_qr_scan_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    override fun onResume() {
        super.onResume()
        cams = store.load()
        refresh()
    }

    private fun refresh() {
        adapter.notifyDataSetChanged()
    }

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

    private inner class CameraAdapter : BaseAdapter() {
        override fun getCount(): Int = cams.size
        override fun getItem(position: Int): Any = cams[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_camera, parent, false)
            val camera = cams[position]
            view.findViewById<TextView>(R.id.tvName).text = camera.name
            view.findViewById<TextView>(R.id.tvMasked).text = masked(camera.url)
            view.findViewById<TextView>(R.id.tvInfo).text = infoSummary(camera)
            view.findViewById<MaterialButton>(R.id.btnOpen).setOnClickListener { openCamera(camera) }
            view.findViewById<MaterialButton>(R.id.btnCopy).setOnClickListener { copyCameraUrl(camera) }
            view.findViewById<MaterialButton>(R.id.btnMore).setOnClickListener { showOptions(position) }
            return view
        }
    }
}
