package com.minhacamera

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Central de configurações com navegação para telas dedicadas. */
class SettingsActivity : AppCompatActivity() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    private fun addHeader(parent: LinearLayout, title: String, detail: String) {
        parent.addView(TextView(this).apply {
            text = title; textSize = 24f; setTextColor(getColor(R.color.app_text)); setPadding(0, dp(10), 0, dp(5))
        })
        parent.addView(TextView(this).apply {
            text = detail; textSize = 14f; setTextColor(getColor(R.color.ui_muted)); setPadding(0, 0, 0, dp(14))
        })
    }

    private fun addAction(parent: LinearLayout, title: String, detail: String, action: () -> Unit) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundResource(R.drawable.bg_ui_card)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.bottomMargin = dp(12)
            layoutParams = lp
            isClickable = true
            isFocusable = true
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
        supportActionBar?.hide()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(getColor(R.color.app_background))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        addHeader(content, getString(R.string.home_settings), getString(R.string.settings_intro))
        addAction(content, "← Voltar", getString(R.string.settings_back_detail)) { finish() }
        addAction(content, getString(R.string.settings_theme), getString(R.string.settings_theme_detail)) {
            startActivity(Intent(this, ThemeSettingsActivity::class.java))
        }
        addAction(content, getString(R.string.settings_about), getString(R.string.settings_about_detail)) {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        addAction(content, getString(R.string.settings_donate), getString(R.string.settings_donate_detail)) {
            startActivity(Intent(this, SupportProjectActivity::class.java))
        }
    }
}
