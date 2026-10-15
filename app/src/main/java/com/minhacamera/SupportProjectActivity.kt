package com.minhacamera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class SupportProjectActivity : AppCompatActivity() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun currentTheme() = getSharedPreferences(CameraTheme.PREFS, MODE_PRIVATE).getString(CameraTheme.KEY, CameraTheme.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (currentTheme() == CameraTheme.AMOLED) setTheme(R.style.Theme_MinhaCamera_Amoled)
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.settings_donate)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(getColor(R.color.app_background))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        content.addView(TextView(this).apply {
            text = "Seu apoio é opcional e ajuda a manter o desenvolvimento do Minha Câmera e do Vigia IA."
            textSize = 14f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, 0, 0, dp(16))
        })
        val pixCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundResource(R.drawable.bg_ui_card)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        pixCard.addView(TextView(this).apply { text = "Pix"; textSize = 18f; setTextColor(getColor(R.color.app_text)) })
        pixCard.addView(TextView(this).apply { text = "drilemoz@gmail.com"; textSize = 16f; setTextColor(getColor(R.color.brand_primary)); setPadding(0, dp(6), 0, 0) })
        pixCard.addView(TextView(this).apply { text = "Use a chave por e-mail para contribuir quando quiser."; textSize = 13f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, dp(4), 0, 0) })
        content.addView(pixCard)
        content.addView(MaterialButton(this).apply {
            text = "Copiar chave Pix"
            isAllCaps = false
            setOnClickListener {
                val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText("Pix", "drilemoz@gmail.com"))
                Toast.makeText(this@SupportProjectActivity, R.string.settings_pix_copied, Toast.LENGTH_SHORT).show()
            }
        })
        content.addView(MaterialButton(this).apply {
            text = "Compartilhar chave Pix"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Pix: drilemoz@gmail.com")
                }, getString(R.string.settings_pix)))
            }
        })
        val googleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundResource(R.drawable.bg_ui_card)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        }
        googleCard.addView(TextView(this).apply { text = getString(R.string.settings_google); textSize = 18f; setTextColor(getColor(R.color.app_text)) })
        googleCard.addView(TextView(this).apply { text = getString(R.string.settings_google_values); textSize = 16f; setTextColor(getColor(R.color.brand_primary)); setPadding(0, dp(6), 0, 0) })
        googleCard.addView(TextView(this).apply { text = getString(R.string.settings_google_unavailable); textSize = 13f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, dp(6), 0, 0) })
        content.addView(googleCard)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
