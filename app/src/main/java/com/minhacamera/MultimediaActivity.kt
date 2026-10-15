package com.minhacamera

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Entrada única para gravações internas e fotos/vídeos do dispositivo. */
class MultimediaActivity : AppCompatActivity() {
    private var selectedMime = "video/*"
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
    private fun dp(v: Int) = (resources.displayMetrics.density * v + .5f).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(20))
            setBackgroundColor(getColor(R.color.app_background))
        }
        val scroller = ScrollView(this); scroller.addView(content); setContentView(scroller)
        content.addView(TextView(this).apply {
            text = getString(R.string.media_title); textSize = 24f; setTextColor(getColor(R.color.app_text))
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.media_intro); setTextColor(getColor(R.color.ui_muted))
            setPadding(0, dp(6), 0, dp(18))
        })
        fun item(label: Int, action: () -> Unit) {
            content.addView(Button(this).apply {
                setText(label); isAllCaps = false; minHeight = dp(64)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
                setOnClickListener { action() }
            })
        }
        item(R.string.ui_close) { finish() }
        item(R.string.media_recordings) { startActivity(Intent(this, RecordingsActivity::class.java)) }
        item(R.string.media_videos) { openPicker("video/*") }
        item(R.string.media_photos) { openPicker("image/*") }
        content.addView(TextView(this).apply {
            setText(R.string.media_file_help); setTextColor(getColor(R.color.ui_muted)); setPadding(0, dp(10), 0, 0)
        })
    }
    private fun openPicker(mime: String) {
        selectedMime = mime
        openFile.launch(arrayOf(mime))
    }
}
