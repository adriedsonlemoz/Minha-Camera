package com.minhacamera

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** A preferência é compartilhada por todas as Activities. */
object CameraTheme {
    const val PREFS = "ui_appearance"
    const val KEY = "theme_mode"
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    const val AMOLED = "amoled"

    fun apply(context: Context) {
        val mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM)
        AppCompatDelegate.setDefaultNightMode(when (mode) {
            LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK, AMOLED -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
    }
}
