package com.minhacamera

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ScanActivity : AppCompatActivity() {

    private data class Found(val ip: String, val port: Int)

    private val ports = listOf(554, 8554)
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var listView: ListView
    private lateinit var store: CameraStore
    private val found = mutableListOf<Found>()
    private var pool: ExecutorService? = null
    private var attempt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        title = getString(R.string.buscar_cameras)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        NetUtil.bindToWifi(this)

        store = CameraStore(this)
        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        listView = findViewById(R.id.list)
        listView.setOnItemClickListener { _, _, pos, _ -> showCredentialsDialog(found[pos]) }

        findViewById<Button>(R.id.btnRescan).setOnClickListener { startScan(false) }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnManual).setOnClickListener { showManualDialog() }

        startScan(false)
        if (intent.getBooleanExtra("manual", false) && savedInstanceState == null) {
            showManualDialog()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        pool?.shutdownNow()
    }

    private fun refreshList() {
        val items = found.map { "${it.ip}   •   RTSP porta ${it.port}" }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
    }

    private fun startScan(auto: Boolean) {
        pool?.shutdownNow()
        if (!auto) attempt = 0
        found.clear()
        refreshList()

        val prefix = NetUtil.localPrefix(this)
        if (prefix == null) {
            status.setText(R.string.sem_wifi)
            progress.progress = 0
            return
        }
        status.text = getString(
            if (auto) R.string.buscando_2 else R.string.buscando, "${prefix}1 - ${prefix}254"
        )
        progress.max = 254
        progress.progress = 0

        val p = Executors.newFixedThreadPool(64)
        pool = p
        val done = AtomicInteger(0)
        for (i in 1..254) {
            val ip = "$prefix$i"
            p.execute {
                // Timeouts generosos: câmeras em economia de energia demoram a responder.
                val openPort = ports.firstOrNull {
                    RtspProbe.isPortOpen(ip, it, if (it == 554) 3000 else 1500)
                }
                val d = done.incrementAndGet()
                runOnUiThread {
                    if (pool !== p || isFinishing) return@runOnUiThread
                    if (openPort != null) {
                        found.add(Found(ip, openPort))
                        found.sortBy { it.ip.substringAfterLast('.').toInt() }
                        refreshList()
                    }
                    progress.progress = d
                    if (d == 254) {
                        if (found.isEmpty() && attempt == 0) {
                            attempt = 1
                            startScan(true) // segunda tentativa automática
                        } else {
                            status.text = if (found.isEmpty()) getString(R.string.nenhuma)
                            else getString(R.string.encontradas, found.size)
                        }
                    }
                }
            }
        }
    }

    // ---------- Câmera achada na busca ----------
    private fun showCredentialsDialog(f: Found) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_scan_camera, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etUser = view.findViewById<EditText>(R.id.etUser)
        val etPass = view.findViewById<EditText>(R.id.etPass)
        val etPath = view.findViewById<EditText>(R.id.etPath)
        etName.setText("Câmera ${f.ip.substringAfterLast('.')}")

        AlertDialog.Builder(this)
            .setTitle("${f.ip}:${f.port}")
            .setView(view)
            .setPositiveButton(R.string.testar_salvar) { _, _ ->
                val name = etName.text.toString().trim().ifEmpty { "Câmera ${f.ip}" }
                val user = etUser.text.toString().trim().ifEmpty { "admin" }
                val pass = etPass.text.toString()
                testAndSave(f, name, user, pass, normPath(etPath.text.toString()))
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // ---------- Adicionar manualmente (IP da rede já pré-preenchido) ----------
    private fun showManualDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_manual_camera, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etIp = view.findViewById<EditText>(R.id.etIp)
        val etPort = view.findViewById<EditText>(R.id.etPort)
        val etUser = view.findViewById<EditText>(R.id.etUser)
        val etPass = view.findViewById<EditText>(R.id.etPass)
        val etPath = view.findViewById<EditText>(R.id.etPath)

        etIp.setText(NetUtil.localPrefix(this) ?: "")
        etIp.setSelection(etIp.text.length)

        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.adicionar_manual)
            .setView(view)
            .setPositiveButton(R.string.testar_salvar, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
        etIp.requestFocus()

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val ipText = etIp.text.toString().trim()
            val name = etName.text.toString().trim()

            // Aceita também uma URL RTSP completa no campo IP.
            if (ipText.startsWith("rtsp://", ignoreCase = true)) {
                val list = store.load()
                list.add(Camera(name.ifEmpty { "Câmera" }, ipText))
                store.save(list)
                Toast.makeText(this, getString(R.string.salva, name.ifEmpty { "Câmera" }), Toast.LENGTH_LONG).show()
                dlg.dismiss()
                finish()
                return@setOnClickListener
            }
            if (!Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(ipText)) {
                Toast.makeText(this, R.string.ip_invalido, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val port = etPort.text.toString().trim().toIntOrNull() ?: 554
            val user = etUser.text.toString().trim().ifEmpty { "admin" }
            val pass = etPass.text.toString()
            dlg.dismiss()
            testAndSave(
                Found(ipText, port),
                name.ifEmpty { "Câmera ${ipText.substringAfterLast('.')}" },
                user, pass, normPath(etPath.text.toString())
            )
        }
    }

    private fun normPath(raw: String): String {
        val t = raw.trim()
        return if (t.isEmpty() || t.startsWith("/")) t else "/$t"
    }

    private fun saveCamera(f: Found, name: String, user: String, pass: String, path: String) {
        val url = "rtsp://${Uri.encode(user)}:${Uri.encode(pass)}@${f.ip}:${f.port}$path"
        val list = store.load()
        list.add(Camera(name, url))
        store.save(list)
        Toast.makeText(this, getString(R.string.salva, name), Toast.LENGTH_LONG).show()
    }

    private fun testAndSave(f: Found, name: String, user: String, pass: String, customPath: String) {
        val paths = if (customPath.isNotEmpty()) listOf(customPath) else RtspProbe.PATHS
        val total = paths.size
        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.testando)
            .setMessage(getString(R.string.consultando_onvif))
            .setCancelable(false)
            .create()
        dlg.show()

        Thread {
            var okPath: String? = null
            var okPort = f.port
            var authFails = 0
            var noReply = 0
            var firstDebug = ""
            val details = StringBuilder()

            // 1) ONVIF: a própria câmera informa o endereço exato do vídeo.
            if (customPath.isEmpty()) {
                val on = OnvifClient.findStreamUri(f.ip, user, pass)
                details.append("ONVIF: ").append(on.log.ifEmpty { "sem resposta" }).append('\n')
                val u = on.uri
                if (u != null) {
                    val parsed = Uri.parse(u)
                    okPort = if (parsed.port != -1) parsed.port else 554
                    okPath = (parsed.encodedPath ?: "/") +
                        (parsed.encodedQuery?.let { "?$it" } ?: "")
                }
            }

            // 2) Teste dos caminhos conhecidos
            if (okPath == null) {
                for ((i, path) in paths.withIndex()) {
                    runOnUiThread {
                        if (!isFinishing) dlg.setMessage(getString(R.string.testando_caminho, i + 1, total))
                    }
                    val r = RtspProbe.probe(f.ip, f.port, user, pass, path)
                    if (i == 0) firstDebug = r.debug
                    details.append(path).append("  →  ").append(r.detail).append('\n')
                    when (r.status) {
                        ProbeStatus.OK -> {
                            okPath = path
                            break
                        }
                        ProbeStatus.AUTH_FAIL -> {
                            noReply = 0
                            authFails++
                            // Para cedo: muitas senhas erradas podem bloquear a câmera.
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
                            } else {
                                noReply = 0
                            }
                        }
                    }
                }
            }

            val foundPath = okPath
            val foundPort = okPort
            val wrongLogin = authFails >= 2
            val silent = noReply >= 3
            val report = details.toString() +
                (if (firstDebug.isNotEmpty()) "\n" + getString(R.string.detalhes_tecnicos) + "\n" + firstDebug else "")
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                dlg.dismiss()
                if (foundPath != null) {
                    saveCamera(Found(f.ip, foundPort), name, user, pass, foundPath)
                    finish()
                } else {
                    val msg = when {
                        wrongLogin -> R.string.falha_login
                        silent -> R.string.falha_sem_resposta
                        else -> R.string.falha_caminho
                    }
                    AlertDialog.Builder(this)
                        .setMessage(getString(msg) + "\n\n" + report)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton(R.string.salvar_mesmo_assim) { _, _ ->
                            saveCamera(f, name, user, pass, customPath.ifEmpty { "/stream1" })
                            finish()
                        }
                        .show()
                }
            }
        }.start()
    }
}
