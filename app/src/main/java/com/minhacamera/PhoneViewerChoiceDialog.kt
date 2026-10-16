package com.minhacamera

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Opções visuais compactas, sem bibliotecas de UI adicionais e com alvos de toque grandes. */
object PhoneViewerChoiceDialog {
    fun show(activity: AppCompatActivity, title: String, description: String,
             choices: Array<String>, icons: IntArray, selected: Int, onChoose: (Int) -> Unit) {
        val density = activity.resources.displayMetrics.density
        fun dp(number: Int) = (number * density + .5f).toInt()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
            setBackgroundResource(R.drawable.bg_ui_card)
        }
        root.addView(TextView(activity).apply {
            text = title; textSize = 19f
            setTextColor(activity.getColor(R.color.app_text))
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(activity).apply {
            text = description; textSize = 12f
            setPadding(0, dp(3), 0, dp(12))
            setTextColor(activity.getColor(R.color.ui_muted))
        })
        val items = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply { addView(items) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, -2).apply {
            val maxHeight = (activity.resources.displayMetrics.heightPixels * .64f).toInt()
            // Respeitar telas pequenas sem passar por cima da barra do sistema.
            height = minOf(choices.size * dp(60), maxHeight)
        })
        val dialog = AlertDialog.Builder(activity).setView(root).create()
        choices.forEachIndexed { index, choice ->
            val checked = index == selected
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(10), 0)
                setBackgroundResource(R.drawable.bg_ui_card)
                backgroundTintList = ColorStateList.valueOf(activity.getColor(
                    if (checked) R.color.app_background else R.color.app_surface))
                isClickable = true
                isFocusable = true
                contentDescription = choice + if (checked) ", selecionado" else ""
                setOnClickListener { dialog.dismiss(); onChoose(index) }
            }
            row.addView(ImageView(activity).apply {
                setImageResource(icons.getOrElse(index) { R.drawable.ic_ui_settings })
                imageTintList = ColorStateList.valueOf(activity.getColor(R.color.brand_primary))
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(23), dp(23)))
            row.addView(TextView(activity).apply {
                text = choice; textSize = 14f
                setTextColor(activity.getColor(R.color.app_text))
                setPadding(dp(13), 0, dp(6), 0)
                if (checked) setTypeface(null, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (checked) row.addView(TextView(activity).apply {
                text = "✓"; textSize = 22f
                setTextColor(activity.getColor(R.color.brand_primary))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(28), -2))
            items.addView(row, LinearLayout.LayoutParams(-1, dp(56)).apply {
                bottomMargin = dp(4)
            })
        }
        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.cancelar)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(activity.getColor(R.color.brand_primary))
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(-1, dp(45)))
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .90f).toInt(), -2)
    }
}
