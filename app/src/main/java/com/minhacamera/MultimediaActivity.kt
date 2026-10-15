package com.minhacamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** Central multimídia com seções para arquivos locais e do outro aparelho. */
class MultimediaActivity : AppCompatActivity() {
    private var selectedMime = "video/*"
    private fun dp(v: Int) = (resources.displayMetrics.density * v + .5f).toInt()
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val mime = contentResolver.getType(uri) ?: selectedMime
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (_: Exception) {
                Toast.makeText(this, "Não há aplicativo disponível para abrir este arquivo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addSection(parent: LinearLayout, title: String, detail: String) {
        parent.addView(TextView(this).apply { text = title; textSize = 20f; setTextColor(getColor(R.color.app_text)); setPadding(0, dp(4), 0, dp(2)) })
        parent.addView(TextView(this).apply { text = detail; textSize = 13f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, 0, 0, dp(12)) })
    }

    private fun addCard(parent: LinearLayout, title: String, detail: String, action: () -> Unit) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundResource(R.drawable.bg_ui_card)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            isClickable = true; isFocusable = true
            setOnClickListener { action() }
        }
        panel.addView(TextView(this).apply { text = title; textSize = 18f; setTextColor(getColor(R.color.app_text)) })
        panel.addView(TextView(this).apply { text = detail; textSize = 13f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, dp(4), 0, 0) })
        parent.addView(panel)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (getSharedPreferences(CameraTheme.PREFS, MODE_PRIVATE).getString(CameraTheme.KEY, CameraTheme.SYSTEM) == CameraTheme.AMOLED)
            setTheme(R.style.Theme_MinhaCamera_Amoled)
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.home_multimedia)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(getColor(R.color.app_background))
        }
        val scroller = ScrollView(this).apply { addView(content) }
        setContentView(scroller)
        content.addView(TextView(this).apply {
            text = getString(R.string.media_intro)
            setTextColor(getColor(R.color.ui_muted))
            textSize = 14f
            setPadding(0, 0, 0, dp(18))
        })
        addSection(content, "Neste aparelho", "Biblioteca local do próprio telefone.")
        addCard(content, getString(R.string.media_recordings), "Abrir a biblioteca de gravações feitas pelo aplicativo.") {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }
        addCard(content, getString(R.string.media_videos), "Escolher um vídeo salvo no dispositivo para abrir em outro aplicativo.") {
            openPicker("video/*")
        }
        addCard(content, getString(R.string.media_photos), "Escolher uma foto salva no dispositivo.") {
            openPicker("image/*")
        }
        addSection(content, "Outro aparelho", "Acesso por Wi‑Fi à biblioteca do transmissor compatível.")
        addCard(content, "Biblioteca do outro celular (Wi‑Fi)", "Consultar, reproduzir e baixar fotos e vídeos do transmissor pela rede local.") {
            startActivity(Intent(this, RemoteMultimediaActivity::class.java))
        }
        content.addView(TextView(this).apply {
            setText(R.string.media_file_help)
            setTextColor(getColor(R.color.ui_muted))
            setPadding(0, dp(10), 0, 0)
        })
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun openPicker(mime: String) {
        selectedMime = mime
        openFile.launch(arrayOf(mime))
    }
}
