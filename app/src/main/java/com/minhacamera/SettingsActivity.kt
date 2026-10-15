package com.minhacamera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Ajustes locais, informações e apoio voluntário. Sem simulação de checkout. */
class SettingsActivity : AppCompatActivity() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun addHeader(parent: LinearLayout, title: String, detail: String) {
        parent.addView(TextView(this).apply {
            text = title; textSize = 23f; setTextColor(getColor(R.color.app_text)); setPadding(0, dp(10), 0, dp(5))
        })
        parent.addView(TextView(this).apply {
            text = detail; textSize = 14f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, 0, 0, dp(12))
        })
    }
    private fun addAction(parent: LinearLayout, title: String, detail: String, action: () -> Unit) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.bg_ui_card)
            val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(10); layoutParams = lp
            isClickable = true; isFocusable = true
            setOnClickListener { action() }
        }
        panel.addView(TextView(this).apply { text = title; textSize = 17f; setTextColor(getColor(R.color.app_text)) })
        panel.addView(TextView(this).apply { text = detail; textSize = 13f; setTextColor(getColor(R.color.ui_muted)) })
        parent.addView(panel)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        if (getSharedPreferences(CameraTheme.PREFS, MODE_PRIVATE).getString(CameraTheme.KEY, CameraTheme.SYSTEM) == CameraTheme.AMOLED)
            setTheme(R.style.Theme_MinhaCamera_Amoled)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(28)); setBackgroundColor(getColor(R.color.app_background)) }
        val scroll = ScrollView(this).apply { addView(content) }; setContentView(scroll)
        addHeader(content, getString(R.string.home_settings), "Aparência, informações e apoio ao aplicativo")
        addAction(content, "← Voltar", "Retornar às câmeras", { finish() })
        addAction(content, getString(R.string.settings_theme), "Sistema, claro, escuro ou preto AMOLED", { chooseTheme() })
        addAction(content, getString(R.string.settings_about), "1.4.33 · 1.4.32 · 1.4.31", { showAbout() })
        addAction(content, getString(R.string.settings_donate), "Pix e contribuições opcionais", { showDonate() })
    }
    private fun chooseTheme() {
        val labels = arrayOf(getString(R.string.settings_theme_system), getString(R.string.settings_theme_light),
            getString(R.string.settings_theme_dark), getString(R.string.settings_theme_amoled))
        val ids = arrayOf(CameraTheme.SYSTEM, CameraTheme.LIGHT, CameraTheme.DARK, CameraTheme.AMOLED)
        val prefs = getSharedPreferences(CameraTheme.PREFS, MODE_PRIVATE)
        AlertDialog.Builder(this).setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, ids.indexOf(prefs.getString(CameraTheme.KEY, CameraTheme.SYSTEM))) { dialog, index ->
                dialog.dismiss()
                prefs.edit().putString(CameraTheme.KEY, ids[index]).apply()
                CameraTheme.apply(this)
                recreate()
            }.setNegativeButton(R.string.cancelar, null).show()
    }
    private fun showAbout() {
        AlertDialog.Builder(this).setTitle(R.string.settings_about)
            .setMessage("Minha Câmera 1.4.33+43\n• Tela inicial unificada, Central Multimídia, controles remotos e configurações.\n\n1.4.32+42\n• MJPEG/H.264 experimental, telemetria e diagnóstico.\n\n1.4.31+41\n• Correções de compilação Kotlin e comandos de captura.\n\nDesenvolvimento independente. Rede local, sem envio obrigatório à nuvem.")
            .setPositiveButton(R.string.novidades) { _, _ -> AlertDialog.Builder(this)
                .setTitle(R.string.novidades).setMessage(R.string.novidades_text).setPositiveButton(android.R.string.ok, null).show() }
            .setNegativeButton(R.string.ui_close, null).show()
    }
    private fun showDonate() {
        val items = arrayOf("Pix • drilemoz@gmail.com", "Compartilhar chave Pix", "Google Play • valores sugeridos")
        AlertDialog.Builder(this).setTitle(R.string.settings_donate).setItems(items) { _, which ->
            when (which) {
                0 -> {
                    val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("Pix", "drilemoz@gmail.com"))
                    Toast.makeText(this, R.string.settings_pix_copied, Toast.LENGTH_SHORT).show()
                }
                1 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Pix: drilemoz@gmail.com")
                }, getString(R.string.settings_pix)))
                2 -> AlertDialog.Builder(this).setTitle(R.string.settings_google)
                    .setMessage(getString(R.string.settings_google_values) + "\n\n" + getString(R.string.settings_google_unavailable))
                    .setPositiveButton(android.R.string.ok, null).show()
            }
        }.setNegativeButton(R.string.cancelar, null).show()
    }
}
