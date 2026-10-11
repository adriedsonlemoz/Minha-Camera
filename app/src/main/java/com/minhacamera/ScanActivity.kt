package com.minhacamera

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class ScanActivity : AppCompatActivity() {

    private data class Found(
        val ip: String,
        val ports: MutableSet<Int> = linkedSetOf(),
        val onvifUrls: MutableSet<String> = linkedSetOf()
    ) {
        val port: Int? get() = ports.firstOrNull()
    }

    // Busca TCP complementar; dispositivos ONVIF são encontrados independentemente da porta.
    private val ports = listOf(554, 8554, 10554, 8555, 7447)
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var listView: ListView
    private lateinit var store: CameraStore
    private val found = mutableListOf<Found>()
    private var pool: ExecutorService? = null
    private var attempt = 0
    private var discoveryThread: Thread? = null

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
        discoveryThread?.interrupt()
    }

    private fun refreshList() {
        val items = found.map { f ->
            val services = mutableListOf<String>()
            if (f.onvifUrls.isNotEmpty()) services.add("ONVIF")
            if (f.ports.isNotEmpty()) services.add("TCP ${f.ports.joinToString(", ")}")
            "${f.ip}   •   ${services.joinToString(" + ").ifEmpty { "Dispositivo" }}"
        }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
    }

    private fun addFound(ip: String, port: Int? = null, xaddrs: List<String> = emptyList()) {
        val entry = found.firstOrNull { it.ip == ip }
        if (entry != null) {
            if (port != null) entry.ports.add(port)
            entry.onvifUrls.addAll(xaddrs)
        } else {
            found.add(Found(ip, if (port == null) linkedSetOf() else linkedSetOf(port), xaddrs.toMutableSet()))
        }
        found.sortWith(compareBy { it.ip.split('.').fold(0L) { a, b -> (a shl 8) + (b.toLongOrNull() ?: 0L) } })
        refreshList()
    }

    private fun startScan(auto: Boolean) {
        pool?.shutdownNow()
        discoveryThread?.interrupt()
        if (!auto) attempt = 0
        found.clear()
        refreshList()

        val range = NetUtil.scanRange(this)
        if (range == null) {
            status.setText(R.string.sem_wifi)
            progress.progress = 0
            return
        }
        val hosts = range.hosts
        status.text = getString(
            if (auto) R.string.buscando_2 else R.string.buscando, range.description
        )
        progress.max = maxOf(1, hosts.size)
        progress.progress = 0

        val worker = Executors.newFixedThreadPool(20)
        pool = worker
        val done = AtomicInteger(0)
        val onvifDone = AtomicBoolean(false)
        val finished = AtomicBoolean(false)

        fun completeIfReady() {
            if (pool !== worker || isFinishing || isDestroyed) return
            if (done.get() != hosts.size || !onvifDone.get()) return
            if (!finished.compareAndSet(false, true)) return
            if (found.isEmpty() && attempt == 0) {
                attempt++
                startScan(true)
            } else {
                status.text = if (found.isEmpty()) getString(R.string.nenhuma)
                    else getString(R.string.encontradas, found.size)
                if (range.limited) status.append("\n" + getString(R.string.busca_limitada))
                worker.shutdown()
            }
        }

        // Executar WS-Discovery em paralelo com a varredura TCP.
        discoveryThread = Thread {
            val matches = WsDiscovery.search(applicationContext)
            runOnUiThread {
                if (pool !== worker || isFinishing || isDestroyed) return@runOnUiThread
                matches.forEach { addFound(it.ip, xaddrs = it.xaddrs) }
                onvifDone.set(true)
                completeIfReady()
            }
        }.apply { name = "MinhaCamera-WSDiscovery"; start() }

        for (ip in hosts) {
            worker.execute {
                if (Thread.currentThread().isInterrupted) return@execute
                val openPorts = ports.filter { port ->
                    RtspProbe.isPortOpen(ip, port, if (port == 554) 900 else 500)
                }
                val count = done.incrementAndGet()
                runOnUiThread {
                    if (pool !== worker || isFinishing || isDestroyed) return@runOnUiThread
                    openPorts.forEach { addFound(ip, port = it) }
                    progress.progress = count
                    completeIfReady()
                }
            }
        }
        if (hosts.isEmpty()) runOnUiThread { completeIfReady() }
    }

    // ---------- Câmera achada na busca ----------
    private fun showCredentialsDialog(f: Found) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_scan_camera, null)
        val etName = view.findViewById<TextInputEditText>(R.id.etName)
        val etUser = view.findViewById<TextInputEditText>(R.id.etUser)
        val etPass = view.findViewById<TextInputEditText>(R.id.etPass)
        val etPath = view.findViewById<MaterialAutoCompleteTextView>(R.id.etPath)
        val btnCopyPass = view.findViewById<MaterialButton>(R.id.btnCopyPass)
        val btnPastePass = view.findViewById<MaterialButton>(R.id.btnPastePass)
        etName.setText("Câmera ${f.ip.substringAfterLast('.')}")
        CameraFormUi.setupPasswordTools(this, etPass, btnCopyPass, btnPastePass)
        CameraFormUi.setupPathSuggestions(this, etPath)

        AlertDialog.Builder(this)
            .setTitle(f.ip + (f.port?.let { ":$it" } ?: " (ONVIF)"))
            .setView(view)
            .setPositiveButton(R.string.testar_salvar) { _, _ ->
                val name = etName.text?.toString()?.trim().orEmpty().ifEmpty { "Câmera ${f.ip}" }
                val user = etUser.text?.toString()?.trim().orEmpty().ifEmpty { "admin" }
                val pass = etPass.text?.toString().orEmpty()
                testAndSave(f, name, user, pass, CameraFormUi.normalizePathChoice(etPath.text?.toString().orEmpty()))
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // ---------- Adicionar manualmente (IP da rede já pré-preenchido) ----------
    private fun showManualDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_manual_camera, null)
        val etName = view.findViewById<TextInputEditText>(R.id.etName)
        val etIp = view.findViewById<TextInputEditText>(R.id.etIp)
        val etPort = view.findViewById<TextInputEditText>(R.id.etPort)
        val etUser = view.findViewById<TextInputEditText>(R.id.etUser)
        val etPass = view.findViewById<TextInputEditText>(R.id.etPass)
        val etPath = view.findViewById<MaterialAutoCompleteTextView>(R.id.etPath)
        val btnCopyPass = view.findViewById<MaterialButton>(R.id.btnCopyPass)
        val btnPastePass = view.findViewById<MaterialButton>(R.id.btnPastePass)

        etIp.setText(NetUtil.localPrefix(this) ?: "")
        etIp.setSelection(etIp.text?.length ?: 0)
        CameraFormUi.setupPasswordTools(this, etPass, btnCopyPass, btnPastePass)
        CameraFormUi.setupPathSuggestions(this, etPath)

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
            val ipText = etIp.text?.toString()?.trim().orEmpty()
            val name = etName.text?.toString()?.trim().orEmpty()

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
            val port = etPort.text?.toString()?.trim()?.toIntOrNull() ?: 554
            val user = etUser.text?.toString()?.trim().orEmpty().ifEmpty { "admin" }
            val pass = etPass.text?.toString().orEmpty()
            dlg.dismiss()
            testAndSave(
                Found(ipText, linkedSetOf(port)),
                name.ifEmpty { "Câmera ${ipText.substringAfterLast('.')}" },
                user, pass, CameraFormUi.normalizePathChoice(etPath.text?.toString().orEmpty())
            )
        }
    }

    private fun buildUrl(ip: String, port: Int, user: String, pass: String, path: String) =
        CameraFormUi.buildRtspUrl(ip, port, user, pass, path)

    // Separa porta e caminho (com query) de um endereço RTSP devolvido pela câmera.
    private fun splitUri(u: String): Pair<Int, String> {
        val p = Uri.parse(u)
        val port = if (p.port != -1) p.port else 554
        val path = (p.encodedPath ?: "/") + (p.encodedQuery?.let { "?$it" } ?: "")
        return Pair(port, path)
    }

    // Caminhos do fluxo secundário (geralmente H.264 e mais leve) para tentar se o principal falhar.
    private fun altPaths(path: String): List<String> {
        val out = mutableListOf<String>()
        fun add(p: String) {
            if (p != path) out.add(p)
        }
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

    private fun saveCamera(name: String, url: String, alts: List<String>) {
        val list = store.load()
        list.add(Camera(name, url, alts))
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
            var okUrl: String? = null
            var okAlts: List<String> = emptyList()
            var warn = ""
            var authFails = 0
            var noReply = 0
            var firstDebug = ""
            val details = StringBuilder()

            // 1) ONVIF: a própria câmera informa os fluxos de vídeo (H.264 primeiro).
            if (customPath.isEmpty()) {
                val on = OnvifClient.findStreamUri(f.ip, user, pass, f.onvifUrls.toList())
                details.append("ONVIF: ").append(on.log.ifEmpty { "sem resposta" }).append('\n')
                val streams = OnvifClient.order(on.streams)
                if (streams.isNotEmpty()) {
                    val urls = streams.map { st ->
                        val (pt, ph) = splitUri(st.uri)
                        buildUrl(f.ip, pt, user, pass, ph)
                    }
                    okUrl = urls.first()
                    okAlts = urls.drop(1)
                    if (!streams.first().encoding.contains("264")) warn = getString(R.string.aviso_h265)
                }
            }

            // 2) Teste dos caminhos conhecidos em TODAS as portas encontradas.
            // No caso de detecção exclusivamente ONVIF, usar portas RTSP padrão como fallback.
            if (okUrl == null) {
                val candidatePorts = f.ports.toList().ifEmpty { listOf(554, 8554) }
                portLoop@ for (rtspPort in candidatePorts) {
                    noReply = 0
                    authFails = 0
                    for ((i, path) in paths.withIndex()) {
                        if (Thread.currentThread().isInterrupted) break@portLoop
                        runOnUiThread {
                            if (!isFinishing) dlg.setMessage("Porta $rtspPort: " + getString(R.string.testando_caminho, i + 1, total))
                        }
                        val r = RtspProbe.probe(f.ip, rtspPort, user, pass, path)
                        if (i == 0 && firstDebug.isEmpty()) firstDebug = r.debug
                        details.append(":").append(rtspPort).append(path)
                            .append("  →  ").append(r.detail)
                            .append(if (r.videoCodec != null) " [${r.videoCodec}]" else "")
                            .append('\n')
                        when (r.status) {
                            ProbeStatus.OK -> {
                                val primary = buildUrl(f.ip, rtspPort, user, pass, path)
                                val altCandidates = altPaths(path)
                                // Uma resposta 200 não garante H.264: ler o SDP e
                                // priorizar o secundário quando ele CONFIRMA H.264.
                                val verifiedH264 = if (customPath.isEmpty() && r.videoCodec == "H265") {
                                    altCandidates.firstOrNull { candidate ->
                                        val check = RtspProbe.probe(f.ip, rtspPort, user, pass, candidate)
                                        details.append(":").append(rtspPort).append(candidate)
                                            .append(" → ").append(check.detail)
                                            .append(" [").append(check.videoCodec ?: "?").append("]\n")
                                        check.status == ProbeStatus.OK && check.videoCodec == "H264"
                                    }
                                } else null
                                okUrl = verifiedH264?.let { buildUrl(f.ip, rtspPort, user, pass, it) } ?: primary
                                okAlts = (listOf(primary) + altCandidates.map {
                                    buildUrl(f.ip, rtspPort, user, pass, it)
                                }).filter { it != okUrl }.distinct()
                                if (r.videoCodec == "H265" && verifiedH264 == null)
                                    warn = getString(R.string.aviso_h265)
                                break@portLoop
                            }
                            ProbeStatus.AUTH_FAIL -> {
                                noReply = 0
                                authFails++
                                // Não multiplicar tentativas com credenciais incorretas.
                                if (authFails >= 2) break@portLoop
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
            }

            val foundUrl = okUrl
            val foundAlts = okAlts
            val foundWarn = warn
            val wrongLogin = authFails >= 2
            val silent = noReply >= 3
            val report = details.toString() +
                (if (firstDebug.isNotEmpty()) "\n" + getString(R.string.detalhes_tecnicos) + "\n" + firstDebug else "")
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                dlg.dismiss()
                if (foundUrl != null) {
                    saveCamera(name, foundUrl, foundAlts)
                    if (foundWarn.isNotEmpty()) Toast.makeText(this, foundWarn, Toast.LENGTH_LONG).show()
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
                            val p = customPath.ifEmpty { "/stream1" }
                            saveCamera(
                                name,
                                buildUrl(f.ip, f.port ?: 554, user, pass, p),
                                altPaths(p).map { buildUrl(f.ip, f.port ?: 554, user, pass, it) }
                            )
                            finish()
                        }
                        .show()
                }
            }
        }.start()
    }
}
