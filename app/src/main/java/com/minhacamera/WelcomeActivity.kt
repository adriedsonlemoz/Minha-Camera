package com.minhacamera

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun currentTheme() = getSharedPreferences(CameraTheme.PREFS, MODE_PRIVATE).getString(CameraTheme.KEY, CameraTheme.SYSTEM)

    private fun addHighlight(parent: LinearLayout, title: String, detail: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundResource(R.drawable.bg_ui_card)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        card.addView(TextView(this).apply { text = title; textSize = 18f; setTextColor(getColor(R.color.app_text)) })
        card.addView(TextView(this).apply { text = detail; textSize = 13.5f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, dp(6), 0, 0) })
        parent.addView(card)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (currentTheme() == CameraTheme.AMOLED) setTheme(R.style.Theme_MinhaCamera_Amoled)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(getColor(R.color.app_background))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        val fullHistory = intent.getBooleanExtra("full_history", false)
        content.addView(TextView(this).apply { text = getString(R.string.welcome_latest_title); textSize = 28f; setTextColor(getColor(R.color.app_text)); setPadding(0,0,0,dp(4)) })
        content.addView(TextView(this).apply { text = "Uma atualização mais bonita e organizada para o app."; textSize = 14f; setTextColor(getColor(R.color.ui_muted)); setPadding(0,0,0,dp(16)) })
        addHighlight(content, "Visual renovado", "Configurações, sobre, apoio e novidades agora têm telas próprias, sem depender de pop-ups para tudo.")
        addHighlight(content, "Tela inicial mais clara", "Os dispositivos ficam mais organizados em cartões com ações visíveis para abrir, copiar, editar e excluir.")
        addHighlight(content, "Central multimídia reorganizada", "A biblioteca local e a do outro celular foram agrupadas em áreas mais claras, com foco em gravações, fotos, vídeos e rede local.")
        addHighlight(content, "Transmissão simplificada", "No transmissor, o topo fica mais limpo com o ícone de rede no lugar do texto de status fixo.")
        content.addView(TextView(this).apply {
            text = if (fullHistory) getString(R.string.novidades_text) else getString(R.string.welcome_latest_summary)
            textSize = 13.5f
            setTextColor(getColor(R.color.ui_muted))
            setPadding(0, dp(10), 0, dp(14))
        })
        content.addView(MaterialButton(this).apply {
            text = getString(R.string.welcome_latest_continue)
            isAllCaps = false
            setOnClickListener { finish() }
        })
    }
}
