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
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.4.48"
        content.addView(TextView(this).apply {
            text = "Novidades $version"
            textSize = 27f
            setTextColor(getColor(R.color.app_text))
            setPadding(0, 0, 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = "Melhorias desta atualização"
            textSize = 14f
            setTextColor(getColor(R.color.ui_muted))
            setPadding(0, 0, 0, dp(18))
        })
        addHighlight(content, "RTSP e detecção mais claros",
            "Diferencia porta aberta de serviço RTSP confirmado e oferece recuperação de fluxo na rede local.")
        addHighlight(content, "Diagnóstico completo",
            "Botão discreto no visualizador para consultar, copiar e exportar os eventos de conexão.")
        addHighlight(content, "Interface mais limpa",
            "Mensagens técnicas em janela compacta e tela de novidades sem textos acumulados de outras versões.")
        content.addView(MaterialButton(this).apply {
            text = getString(R.string.welcome_latest_continue)
            isAllCaps = false
            setOnClickListener { finish() }
        })
    }
}
