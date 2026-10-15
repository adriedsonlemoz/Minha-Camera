package com.minhacamera

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ThemeSettingsActivity : AppCompatActivity() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun currentTheme() = getSharedPreferences(CameraTheme.PREFS, MODE_PRIVATE).getString(CameraTheme.KEY, CameraTheme.SYSTEM)

    private fun addThemeCard(parent: LinearLayout, title: String, detail: String, key: String) {
        val isSelected = currentTheme() == key
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundResource(if (isSelected) R.drawable.bg_ui_selected else R.drawable.bg_ui_card)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            isClickable = true; isFocusable = true
            setOnClickListener {
                getSharedPreferences(CameraTheme.PREFS, MODE_PRIVATE).edit().putString(CameraTheme.KEY, key).apply()
                CameraTheme.apply(this@ThemeSettingsActivity)
                recreate()
            }
        }
        card.addView(TextView(this).apply { text = title + if (isSelected) "  •  Atual" else ""; textSize = 18f; setTextColor(getColor(R.color.app_text)) })
        card.addView(TextView(this).apply { text = detail; textSize = 13f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, dp(4), 0, 0) })
        parent.addView(card)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (currentTheme() == CameraTheme.AMOLED) setTheme(R.style.Theme_MinhaCamera_Amoled)
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.settings_theme)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(getColor(R.color.app_background))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        content.addView(TextView(this).apply { text = getString(R.string.settings_theme_detail); textSize = 14f; setTextColor(getColor(R.color.ui_muted)); setPadding(0,0,0,dp(14)) })
        addThemeCard(content, getString(R.string.settings_theme_system), "Usa o tema do sistema Android.", CameraTheme.SYSTEM)
        addThemeCard(content, getString(R.string.settings_theme_light), "Fundo claro e contraste equilibrado para o dia.", CameraTheme.LIGHT)
        addThemeCard(content, getString(R.string.settings_theme_dark), "Escuro confortável para ambientes com pouca luz.", CameraTheme.DARK)
        addThemeCard(content, getString(R.string.settings_theme_amoled), "Preto profundo para telas AMOLED e economia visual.", CameraTheme.AMOLED)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
