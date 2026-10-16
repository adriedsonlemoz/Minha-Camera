package com.minhacamera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.text.TextUtils
import android.graphics.Typeface
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Galeria local real: fotos, vídeos, gravações privadas e mídias selecionadas com SAF. */
class GalleryActivity : AppCompatActivity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var scanSerial = 0
    private var all = listOf<GalleryItem>()
    private val picked = linkedMapOf<String, GalleryItem>()
    private val selected = linkedSetOf<String>()
    private var category = "Todas"
    private var sort = 0
    private var selectMode = false
    private lateinit var grid: RecyclerView
    private lateinit var adapter: GalleryAdapter
    private lateinit var message: TextView
    private lateinit var footer: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var search: EditText
    private lateinit var count: TextView
    private lateinit var title: TextView
    private lateinit var searchButton: ImageButton
    private val filterButtons = linkedMapOf<String, Button>()
    private val preferences by lazy { getSharedPreferences("gallery_presentation", MODE_PRIVATE) }
    private var showSize = true
    private var showResolution = true
    private var showDuration = true
    private var showDate = false
    private var deleteAfterApproval: List<GalleryItem> = emptyList()
    private var legacyDeleteConsent = false

    private val permissionResult = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    private val documents = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val mime = contentResolver.getType(uri) ?: return@forEach
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) return@forEach
            val name = queryName(uri) ?: "Arquivo selecionado"
            val size = querySize(uri)
            picked[uri.toString()] = GalleryItem(uri, name, mime, size, System.currentTimeMillis() / 1000L)
        }
        showFiltered()
    }
    private val tree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) exportCopies(treeUri)
    }
    private val deleteIntent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { outcome ->
        val approved = deleteAfterApproval
        deleteAfterApproval = emptyList()
        if (outcome.resultCode == Activity.RESULT_OK) {
            if (legacyDeleteConsent) { legacyDeleteConsent = false; deleteFiles(approved) }
            else deletePrivate(approved.filter { it.privateFile != null })
        } else { legacyDeleteConsent = false; toast("Exclusão cancelada. Arquivos preservados.") }
        refresh()
    }
    private fun queryName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun querySize(uri: Uri): Long = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0).coerceAtLeast(0L) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (getSharedPreferences(CameraTheme.PREFS, MODE_PRIVATE).getString(CameraTheme.KEY, CameraTheme.SYSTEM) == CameraTheme.AMOLED)
            setTheme(R.style.Theme_MinhaCamera_Amoled)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide() // Cabeçalho compacto com busca e remoto na mesma linha.
        showSize = preferences.getBoolean("size", true)
        showResolution = preferences.getBoolean("resolution", true)
        showDuration = preferences.getBoolean("duration", true)
        showDate = preferences.getBoolean("date", false)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.app_background))
        }
        setContentView(content)
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(4), dp(6), dp(4))
            setBackgroundColor(getColor(R.color.app_surface))
            elevation = dp(2).toFloat()
        }
        val back = iconAction(R.drawable.ic_back, "Voltar") { finish() }
        top.addView(back, LinearLayout.LayoutParams(dp(44), dp(48)))
        title = TextView(this).apply {
            text = "Central multimídia"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.app_text))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        top.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        search = EditText(this).apply {
            hint = "Pesquisar arquivos"
            textSize = 14f
            setSingleLine(true)
            visibility = View.GONE
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = showFiltered()
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        top.addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))
        searchButton = iconAction(R.drawable.ic_ui_search, "Pesquisar na galeria") { toggleSearch() }
        top.addView(searchButton, LinearLayout.LayoutParams(dp(42), dp(48)))
        val remote = Button(this).apply {
            text = "Remoto"
            textSize = 11.5f
            isAllCaps = false
            setTextColor(getColor(R.color.app_text))
            setBackgroundResource(R.drawable.bg_gallery_filter)
            compoundDrawablePadding = dp(3)
            val symbol = ContextCompat.getDrawable(this@GalleryActivity, R.drawable.ic_ui_smartphone)?.mutate()
            symbol?.setBounds(0, 0, dp(17), dp(17))
            setCompoundDrawables(symbol, null, null, null)
            setPadding(dp(6), 0, dp(6), 0)
            minHeight = 0
            minWidth = 0
            contentDescription = "Abrir galeria do celular remoto por Wi-Fi"
            setOnClickListener { startActivity(Intent(this@GalleryActivity, RemoteMultimediaActivity::class.java)) }
        }
        top.addView(remote, LinearLayout.LayoutParams(dp(86), dp(40)).apply { marginEnd = dp(3) })
        top.addView(iconAction(R.drawable.ic_ui_settings, "Configurações e exibição da galeria") { showGalleryOptions() },
            LinearLayout.LayoutParams(dp(42), dp(48)))
        content.addView(top)

        // Os filtros ficam diretamente sob o cabeçalho, sem duas faixas de botões gigantes.
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(3))
        }
        listOf("Todas" to R.drawable.ic_ui_gallery, "Fotos" to R.drawable.ic_ui_camera,
            "Vídeos" to R.drawable.ic_ui_video, "Gravações" to R.drawable.ic_ui_record).forEach { (label, iconId) ->
            val button = chip(label) { category = label; showFiltered() }.apply {
                textSize = 11f
                setPadding(dp(2), 0, dp(2), 0)
                setBackgroundResource(R.drawable.bg_gallery_filter)
                minWidth = 0
                minHeight = 0
                compoundDrawablePadding = dp(3)
                val symbol = ContextCompat.getDrawable(this@GalleryActivity, iconId)?.mutate()
                symbol?.setBounds(0, 0, dp(15), dp(15))
                setCompoundDrawables(symbol, null, null, null)
            }
            filterButtons[label] = button
            filterRow.addView(button, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }
        content.addView(filterRow)
        count = TextView(this).apply { textSize = 12f; setTextColor(getColor(R.color.ui_muted)); setPadding(dp(16), dp(10), dp(16), dp(8)) }
        content.addView(count)
        val center = FrameLayout(this)
        content.addView(center, LinearLayout.LayoutParams(-1, 0, 1f))
        grid = RecyclerView(this).apply {
            val screenDp = resources.configuration.screenWidthDp
            layoutManager = GridLayoutManager(this@GalleryActivity, (screenDp / 112).coerceIn(3, 6))
            setHasFixedSize(true)
            itemAnimator = null
        }
        adapter = GalleryAdapter()
        grid.adapter = adapter
        center.addView(grid, FrameLayout.LayoutParams(-1, -1))
        message = TextView(this).apply {
            text = "Carregando galeria…"
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(getColor(R.color.ui_muted))
            setPadding(dp(25), dp(16), dp(25), dp(16))
        }
        center.addView(message, FrameLayout.LayoutParams(-1, -1))
        progress = ProgressBar(this).apply { visibility = View.GONE }
        center.addView(progress, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
        footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(6), dp(8), dp(8)); visibility = View.GONE }
        content.addView(footer)
        // Escolher arquivos, atualizar, ordenar e selecionar ficam no menu de configurações.
        maybeRequestAccess()
        refresh()
    }

    private fun iconAction(iconId: Int, description: String, action: () -> Unit): ImageButton =
        ImageButton(this).apply {
            setImageResource(iconId)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = description
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            setOnClickListener { action() }
        }

    private fun toggleSearch() {
        val open = search.visibility != View.VISIBLE
        title.visibility = if (open) View.GONE else View.VISIBLE
        search.visibility = if (open) View.VISIBLE else View.GONE
        searchButton.contentDescription = if (open) "Fechar pesquisa" else "Pesquisar na galeria"
        searchButton.setImageResource(if (open) R.drawable.ic_ui_close else R.drawable.ic_ui_search)
        if (open) {
            search.requestFocus()
            search.post {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            search.text.clear()
            search.clearFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(search.windowToken, 0)
        }
    }

    private fun showGalleryOptions() {
        val labels = arrayOf("Tamanho do arquivo", "Resolução da imagem", "Duração dos vídeos", "Data na miniatura")
        val flags = booleanArrayOf(showSize, showResolution, showDuration, showDate)
        AlertDialog.Builder(this)
            .setTitle("Exibição da galeria")
            .setMultiChoiceItems(labels, flags) { _, index, checked ->
                flags[index] = checked
                showSize = flags[0]
                showResolution = flags[1]
                showDuration = flags[2]
                showDate = flags[3]
                preferences.edit().putBoolean("size", showSize)
                    .putBoolean("resolution", showResolution)
                    .putBoolean("duration", showDuration)
                    .putBoolean("date", showDate).apply()
                showFiltered()
            }
            .setPositiveButton("Concluir", null)
            .setNeutralButton("Mais ações") { _, _ -> showGalleryActions() }
            .show()
    }

    private fun showGalleryActions() {
        val actions = arrayOf("Ordenar arquivos", if (selectMode) "Cancelar seleção" else "Selecionar vários",
            "Escolher fotos/vídeos", "Atualizar galeria")
        AlertDialog.Builder(this).setTitle("Ações da galeria")
            .setItems(actions) { _, index ->
                when (index) {
                    0 -> chooseOrder()
                    1 -> { selectMode = !selectMode; if (!selectMode) selected.clear(); showFiltered() }
                    2 -> documents.launch(arrayOf("image/*", "video/*"))
                    3 -> refresh()
                }
            }.show()
    }

    override fun onResume() { super.onResume(); if (::adapter.isInitialized) refresh() }
    override fun onDestroy() { scanSerial++; io.shutdownNow(); super.onDestroy() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    private fun chip(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false
        setBackgroundResource(R.drawable.bg_ui_card)
        setTextColor(getColor(R.color.app_text))
        minHeight = dp(44)
        setOnClickListener { action() }
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    private fun maybeRequestAccess() {
        val requested = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (requested.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionResult.launch(requested)
        }
    }
    private fun refresh() {
        val serial = ++scanSerial
        progress.visibility = View.VISIBLE
        io.execute {
            val items = runCatching { GalleryLibrary.scan(applicationContext) }.getOrDefault(emptyList())
            ui.post {
                if (isFinishing || isDestroyed || serial != scanSerial) return@post
                all = (items + picked.values).distinctBy { it.key }
                progress.visibility = View.GONE
                showFiltered()
            }
        }
    }
    private fun filtered(): List<GalleryItem> {
        val term = search.text.toString().trim()
        val base = all.filter { item ->
            when (category) {
                "Fotos" -> item.isPhoto
                "Vídeos" -> item.isVideo
                "Gravações" -> item.privateFile != null
                else -> true
            } && item.name.contains(term, ignoreCase = true)
        }
        return when (sort) {
            1 -> base.sortedBy { it.name.lowercase(Locale.ROOT) }
            2 -> base.sortedByDescending { it.size }
            else -> base.sortedByDescending { it.dateSeconds }
        }
    }
    private fun showFiltered() {
        if (!::adapter.isInitialized) return
        val visible = filtered()
        adapter.items = visible
        adapter.notifyDataSetChanged()
        message.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        message.text = if (all.isEmpty()) "Nenhum arquivo acessível ainda. Autorize fotos/vídeos ou use Escolher arquivos." else "Nenhuma mídia corresponde ao filtro."
        count.text = if (selectMode) "${visible.size} arquivo(s) · ${selected.size} selecionado(s)" else "${visible.size} arquivo(s)"
        filterButtons.forEach { (label, button) ->
            button.setBackgroundResource(if (label == category) R.drawable.bg_gallery_filter_selected else R.drawable.bg_gallery_filter)
            button.isSelected = label == category
        }
        footer.removeAllViews()
        footer.visibility = if (selectMode) View.VISIBLE else View.GONE
        if (selectMode) {
            listOf("Compartilhar", "Copiar", "Detalhes", "Excluir").forEach { label ->
                footer.addView(chip(label) {
                    when (label) {
                        "Compartilhar" -> share()
                        "Copiar" -> if (selection().isNotEmpty()) tree.launch(null)
                        "Detalhes" -> showDetails()
                        "Excluir" -> confirmDelete()
                    }
                }, LinearLayout.LayoutParams(0, dp(52), 1f))
            }
        }
    }
    private fun chooseOrder() {
        AlertDialog.Builder(this).setTitle("Ordenar por")
            .setSingleChoiceItems(arrayOf("Mais recentes", "Nome", "Tamanho"), sort) { dialog, position ->
                sort = position; dialog.dismiss(); showFiltered()
            }.setNegativeButton("Cancelar", null).show()
    }
    private fun selection(): List<GalleryItem> = all.filter { it.key in selected }
    private fun open(item: GalleryItem) {
        GallerySession.items = filtered()
        startActivity(Intent(this, GalleryViewerActivity::class.java).apply {
            putExtra("media_uri", item.uri.toString())
            putExtra("media_name", item.name)
            putExtra("media_mime", item.mime)
            putExtra("media_private", item.privateFile?.absolutePath)
        })
    }
    private fun share() {
        val items = selection()
        if (items.isEmpty()) { toast("Selecione arquivos primeiro."); return }
        val uris = ArrayList(items.map { it.uri })
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply {
            type = items.first().mime; putExtra(Intent.EXTRA_STREAM, uris.first())
        } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (items.all { it.isPhoto }) "image/*" else if (items.all { it.isVideo }) "video/*" else "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(intent, "Compartilhar mídias")) }
            .onFailure { toast("Não foi possível compartilhar estes arquivos.") }
    }
    private fun showDetails() {
        val items = selection()
        if (items.isEmpty()) { toast("Selecione um arquivo."); return }
        val info = if (items.size == 1) items.first().let {
            "Nome: ${it.name}\nTipo: ${it.mime}\nTamanho: ${if (it.size > 0) Formatter.formatFileSize(this, it.size) else "não informado"}\n" +
                "Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(it.dateSeconds * 1000))}\n" +
                "Origem: ${if (it.privateFile != null) "Gravação do app" else "Mídia autorizada"}"
        } else "${items.size} itens selecionados\n${Formatter.formatFileSize(this, items.sumOf { it.size })}"
        AlertDialog.Builder(this).setTitle("Detalhes").setMessage(info).setPositiveButton("Fechar", null).show()
    }
    private fun exportCopies(treeUri: Uri) {
        val items = selection()
        if (items.isEmpty()) return
        io.execute {
            var successes = 0
            val folder = runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)) }.getOrNull()
            if (folder != null) {
                for ((index, item) in items.withIndex()) {
                    runCatching {
                        val base = item.name.map { if (it == '/' || it == '\\' || Character.isISOControl(it)) '_' else it }.joinToString("").take(110).ifBlank { "midia" }
                        // Evita sobrescrever mesmo quando a pasta já contém nome idêntico.
                        val candidate = "${System.currentTimeMillis()}_${index}_$base"
                        val dest = DocumentsContract.createDocument(contentResolver, folder, item.mime, candidate)
                            ?: error("Destino indisponível")
                        contentResolver.openInputStream(item.uri)!!.use { input ->
                            contentResolver.openOutputStream(dest, "w")!!.use { output -> input.copyTo(output) }
                        }
                        successes++
                    }
                }
            }
            ui.post { toast("$successes de ${items.size} arquivo(s) copiado(s). Originais preservados.") }
        }
    }
    private fun confirmDelete() {
        val items = selection()
        if (items.isEmpty()) { toast("Selecione arquivos primeiro."); return }
        AlertDialog.Builder(this).setTitle("Excluir ${items.size} arquivo(s)?")
            .setMessage("A remoção pode ser permanente. O Android poderá solicitar confirmação adicional. Não será possível desfazer a exclusão de gravações privadas.")
            .setPositiveButton("Excluir") { _, _ -> deleteFiles(items) }
            .setNegativeButton("Cancelar", null).show()
    }
    private fun deleteFiles(items: List<GalleryItem>) {
        val media = items.filter { it.privateFile == null && it.uri.authority?.startsWith("media") == true }
        val other = items.filter { it.privateFile == null && it !in media }
        if (other.isNotEmpty()) toast("Arquivos escolhidos pelo seletor não serão apagados sem permissão de gerenciamento.")
        if (media.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
            deleteAfterApproval = items
            val request = runCatching { android.provider.MediaStore.createDeleteRequest(contentResolver, media.map { it.uri }) }.getOrNull()
            if (request != null) {
                deleteIntent.launch(IntentSenderRequest.Builder(request.intentSender).build())
                return
            }
            deleteAfterApproval = emptyList()
            toast("Não foi possível solicitar exclusão ao Android.")
            return
        }
        // Android 9 ou anterior: armazenamento autorizado; no 10 pode exigir aprovação separada.
        if (media.isNotEmpty()) {
            var failed = false
            for (item in media) {
                try {
                    if (contentResolver.delete(item.uri, null, null) <= 0) failed = true
                } catch (ex: SecurityException) {
                    if (Build.VERSION.SDK_INT == 29 && ex is android.app.RecoverableSecurityException) {
                        deleteAfterApproval = items
                        legacyDeleteConsent = true
                        deleteIntent.launch(IntentSenderRequest.Builder(ex.userAction.actionIntent.intentSender).build())
                        return
                    }
                    failed = true
                }
            }
            if (failed) toast("Algumas mídias já foram removidas ou exigem autorização do Android.")
        }
        deletePrivate(items.filter { it.privateFile != null })
        refresh()
    }
    private fun deletePrivate(items: List<GalleryItem>) {
        io.execute {
            val root = runCatching { RecordingStore.root(applicationContext).canonicalFile }.getOrNull()
            var count = 0
            for (item in items) {
                val file = item.privateFile ?: continue
                if (root != null && runCatching { file.canonicalFile.parentFile == root &&
                        file.name.startsWith("Camera_") && file.delete() }.getOrDefault(false)) count++
            }
            ui.post { if (!isFinishing) { selected.clear(); selectMode = false; toast("$count gravação(ões) removida(s)."); refresh() } }
        }
    }

    inner class GalleryAdapter : RecyclerView.Adapter<GalleryAdapter.Holder>() {
        var items = listOf<GalleryItem>()
        inner class Holder(val frame: FrameLayout, val thumbnail: ImageView, val overlay: TextView) : RecyclerView.ViewHolder(frame)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val frame = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(-1, dp(120)).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                setBackgroundColor(Color.rgb(22, 33, 47))
            }
            val photo = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            frame.addView(photo, FrameLayout.LayoutParams(-1, -1))
            val overlay = TextView(parent.context).apply {
                setTextColor(Color.WHITE); textSize = 10f; gravity = Gravity.END
                setPadding(dp(5), dp(3), dp(5), dp(3)); setBackgroundColor(0x990A1422.toInt())
                maxLines = 3
            }
            frame.addView(overlay, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.RIGHT))
            return Holder(frame, photo, overlay)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val marked = item.key in selected
            val dateText = if (showDate) SimpleDateFormat("dd/MM", Locale("pt", "BR"))
                .format(Date(item.dateSeconds * 1000)) else ""
            holder.overlay.text = if (marked) "✓ Selecionado" else
                listOfNotNull(if (item.isVideo) "▶" else null, dateText.takeIf { it.isNotEmpty() }).joinToString("  ")
            holder.overlay.visibility = if (marked || item.isVideo || showDate || showSize || showResolution) View.VISIBLE else View.GONE
            holder.frame.alpha = if (marked) 0.65f else 1f
            holder.frame.contentDescription = "${if (item.isVideo) "Vídeo" else "Foto"}: ${item.name}"
            // Solicita miniaturas suficientes para telas de maior densidade, sem usar o arquivo original na grade.
            val columns = (grid.layoutManager as GridLayoutManager).spanCount
            val thumbPx = (resources.displayMetrics.widthPixels * 1.4f / columns).toInt().coerceIn(360, 640)
            GalleryThumbnails.load(this@GalleryActivity, item, holder.thumbnail, thumbPx)
            holder.thumbnail.setTag(R.id.gallery_media_tag, item.key)
            if (!marked && (showSize || showResolution || (showDuration && item.isVideo))) {
                GalleryMetadata.load(this@GalleryActivity, item) { metadata ->
                    if (holder.thumbnail.getTag(R.id.gallery_media_tag) != item.key || item.key in selected) return@load
                    val bits = mutableListOf<String>()
                    if (item.isVideo) bits.add("▶")
                    if (showSize && item.size > 0) bits.add(Formatter.formatShortFileSize(this@GalleryActivity, item.size))
                    if (showResolution && metadata.width > 0 && metadata.height > 0)
                        bits.add("${metadata.width}×${metadata.height}")
                    val duration = if (metadata.durationMs > 0) metadata.durationMs else item.durationMs
                    if (item.isVideo && showDuration && duration > 0) bits.add(GalleryMetadata.formatDuration(duration))
                    if (showDate) bits.add(SimpleDateFormat("dd/MM", Locale("pt", "BR"))
                        .format(Date(item.dateSeconds * 1000)))
                    holder.overlay.text = bits.joinToString(" · ")
                    holder.overlay.visibility = if (bits.isEmpty()) View.GONE else View.VISIBLE
                }
            }
            holder.frame.setOnLongClickListener { selectMode = true; toggle(item); true }
            holder.frame.setOnClickListener { if (selectMode) toggle(item) else open(item) }
        }
    }
    private fun toggle(item: GalleryItem) {
        if (!selected.add(item.key)) selected.remove(item.key)
        showFiltered()
    }
}
