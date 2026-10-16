package com.minhacamera

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class AboutActivity : AppCompatActivity() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun currentTheme() = getSharedPreferences(CameraTheme.PREFS, MODE_PRIVATE).getString(CameraTheme.KEY, CameraTheme.SYSTEM)

    private fun addBlock(parent: LinearLayout, title: String, detail: String) {
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
        supportActionBar?.title = getString(R.string.settings_about)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(getColor(R.color.app_background))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }
        content.addView(TextView(this).apply { text = "Minha Câmera $version"; textSize = 25f; setTextColor(getColor(R.color.app_text)); setPadding(0, 0, 0, dp(4)) })
        content.addView(TextView(this).apply { text = "Transmissão e visualização de câmera por rede local, sem nuvem obrigatória."; textSize = 14f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, 0, 0, dp(16)) })
        addBlock(content, "O que é o app", "Projeto auxiliar do Vigia IA voltado para transmissão local entre celulares, MJPEG/H.264 experimental, modo de tela apagada, gravação opcional e acesso remoto à multimídia.")
        addBlock(content, "Últimas versões", """1.4.54+64
• Digest corrigido para SETUP/PLAY e alternativas do LibVLC reforçadas; diagnóstico seguro.

1.4.53+63
• Segundo caminho de autenticação LibVLC e diagnóstico SETUP/PLAY/RTP sem vazar credenciais.

1.4.52+62
• URLs RTSP, HTTP/MJPEG, HLS, DASH e RTMP/RTP experimentais; descoberta ampliada, RTSP SDP validado.

1.4.51+61
• Autenticação RTSP Digest com reconexão após 401 e diagnóstico de login sem exposição de credenciais.

1.4.50+60
• Diagnóstico RTSP com estatísticas VLC; teste de VLC externo e duas opções de rota de rede.

1.4.49+59
• Novidades centralizadas, botões de diagnóstico com cores consistentes e relatório RTSP atualizado após análise.

1.4.48+58
• Diagnóstico RTSP exportável, identificação de serviços mais precisa e boas-vindas resumidas.

1.4.47+57
• Tela cheia automática ao girar o visualizador em orientação automática; correção matemática da rotação da câmera transmissora.

1.4.46+56
• Compatibilidade RTSP: tentativas TCP e UDP por fluxo e diagnóstico de conexão sem senha.

1.4.45+55
• Corrige compilação do relatório RTSP e adiciona teste preventivo para strings Kotlin inválidas.""".trimIndent())
        addBlock(content, "Privacidade e rede", "O foco permanece em rede local. O app não depende de envio obrigatório à nuvem para transmitir, assistir ou consultar a biblioteca do outro celular.")
        content.addView(MaterialButton(this).apply {
            text = getString(R.string.novidades)
            isAllCaps = false
            setOnClickListener { startActivity(Intent(this@AboutActivity, WelcomeActivity::class.java)) }
        })
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
