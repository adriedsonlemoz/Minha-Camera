package com.minhacamera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Relatório local minimalista. Exportação fica a cargo da Activity (SAF). */
class CameraReportDialog(
    private val activity: AppCompatActivity,
    heading: String,
    initialText: String,
    private val export: (String) -> Unit
) {
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + 0.5f).toInt()
    private val body = TextView(activity).apply {
        text = initialText
        textSize = 13f
        setTextColor(activity.getColor(R.color.app_text))
        setTextIsSelectable(true)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private val container = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(14))
        setBackgroundResource(R.drawable.bg_ui_card)
    }
    private val dialog = AlertDialog.Builder(activity).setView(container).create()

    init {
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(activity).apply {
            text = heading
            textSize = 19f
            setTextColor(activity.getColor(R.color.app_text))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = TextView(activity).apply {
            text = "✕"
            textSize = 23f
            gravity = Gravity.CENTER
            contentDescription = "Fechar"
            setTextColor(activity.getColor(R.color.ui_muted))
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))
        container.addView(header)
        container.addView(TextView(activity).apply {
            text = "Diagnóstico local • sem senhas ou tokens"
            textSize = 12f
            setTextColor(activity.getColor(R.color.ui_muted))
        })
        val scroller = ScrollView(activity).apply {
            isFillViewport = false
            setPadding(0, dp(16), 0, dp(12))
            addView(body)
        }
        container.addView(scroller, LinearLayout.LayoutParams(-1,
            (activity.resources.displayMetrics.heightPixels * .40f).toInt()))
        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(title: String, click: () -> Unit) {
            actions.addView(MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = title
                textSize = 12f
                isAllCaps = false
                minWidth = 0
                setPadding(0, 0, 0, 0)
                setOnClickListener { click() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        }
        action("Fechar") { dialog.dismiss() }
        action("Copiar") {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Minha Câmera", body.text))
            Toast.makeText(activity, "Relatório copiado", Toast.LENGTH_SHORT).show()
        }
        action("Exportar") { export(body.text.toString()) }
        container.addView(actions)
    }

    fun show(): CameraReportDialog {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .92f).toInt(), -2)
        return this
    }
    fun update(message: String) {
        if (dialog.isShowing) body.text = message
    }
    val isShowing: Boolean get() = dialog.isShowing
}
