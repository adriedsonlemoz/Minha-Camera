package com.minhacamera

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingsActivity : AppCompatActivity() {
    private val recordingsChanged: () -> Unit = { if (!isFinishing && !isDestroyed) refresh() }
    override fun onStart() { super.onStart(); RecordingStore.observe(recordingsChanged) }
    override fun onStop() { RecordingStore.unobserve(recordingsChanged); super.onStop() }
    private var pendingExport: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recordings)
        title = getString(R.string.gravacoes)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        pendingExport = savedInstanceState?.getString("export")
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("export", pendingExport)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); refresh() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun refresh() {
        val container = findViewById<LinearLayout>(R.id.recordingList)
        container.removeAllViews()
        val files = runCatching { RecordingFiles.clips(RecordingStore.root(this)) }.getOrDefault(emptyList())
        findViewById<View>(R.id.emptyRecordings).visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        val dayFormat = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR"))
        val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
        var lastDay = ""
        for (file in files) {
            val day = dayFormat.format(Date(file.lastModified()))
            if (day != lastDay) {
                lastDay = day
                container.addView(TextView(this).apply {
                    text = day.replaceFirstChar { it.uppercase() }
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                    setPadding(dp(4), dp(20), dp(4), dp(10))
                })
            }
            val card = MaterialCardView(this).apply {
                radius = dp(16).toFloat()
                strokeWidth = dp(1)
                setStrokeColor(getColor(R.color.app_outline))
                setCardBackgroundColor(getColor(R.color.app_surface))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            }
            card.addView(TextView(this).apply {
                text = "▶  ${timeFormat.format(Date(file.lastModified()))}  •  ${file.extension.uppercase()}\n${Formatter.formatFileSize(this@RecordingsActivity, file.length())}  ·  Toque para abrir ou salvar"
                setTextColor(getColor(R.color.app_text)); textSize = 15f
                setPadding(dp(20), dp(18), dp(20), dp(18))
            })
            card.setOnClickListener { showActions(file) }
            container.addView(card)
        }
    }
    private fun uri(file: File) = FileProvider.getUriForFile(this, "$packageName.recordings", file)
    private fun mime(file: File) = when (file.extension.lowercase()) {
        "mp4", "m4v", "mov" -> "video/mp4"
        "ts" -> "video/mp2t"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "webm" -> "video/webm"
        else -> "video/*"
    }
    private fun showActions(file: File) {
        AlertDialog.Builder(this).setTitle(getString(R.string.gravacoes)).setItems(arrayOf(
            getString(R.string.abrir_video), getString(R.string.compartilhar_video),
            getString(R.string.salvar_copia), getString(R.string.excluir)
        )) { _, which ->
            when (which) {
                0 -> try {
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(file), mime(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.video_sem_player, Toast.LENGTH_LONG).show() }
                1 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime(file))
                    .putExtra(Intent.EXTRA_STREAM, uri(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), getString(R.string.compartilhar_video)))
                2 -> {
                    pendingExport = file.name
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(mime(file)).putExtra(Intent.EXTRA_TITLE, file.name), 301)
                }
                3 -> AlertDialog.Builder(this).setMessage(R.string.video_excluir)
                    .setPositiveButton(R.string.excluir) { _, _ -> file.delete(); refresh() }
                    .setNegativeButton(R.string.cancelar, null).show()
            }
        }.show()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 301) return
        val name = pendingExport; pendingExport = null
        val destination = data?.data
        if (resultCode != RESULT_OK || name == null || destination == null) return
        val source = RecordingFiles.clips(RecordingStore.root(this)).firstOrNull { it.name == name } ?: return
        val app = applicationContext
        RecordingStore.io.execute {
            val success = runCatching {
                app.contentResolver.openOutputStream(destination, "w")!!.use { out -> source.inputStream().use { it.copyTo(out) } }
            }.isSuccess
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(app, if (success) R.string.exportacao_ok else R.string.exportacao_falhou, Toast.LENGTH_LONG).show()
            }
        }
    }
}
