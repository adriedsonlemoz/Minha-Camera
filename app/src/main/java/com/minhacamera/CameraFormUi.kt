package com.minhacamera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText

object CameraFormUi {
    const val DEFAULT_PATH = "Default"
    val pathSuggestions = listOf(
        DEFAULT_PATH,
        "/stream1",
        "/stream2",
        "/onvif1",
        "/onvif2",
        "/live/ch00_0",
        "/live/ch00_1",
        "/Streaming/Channels/101",
        "/Streaming/Channels/102",
        "/h264Preview_01_main",
        "/h264Preview_01_sub",
        "/cam/realmonitor?channel=1&subtype=0",
        "/cam/realmonitor?channel=1&subtype=1"
    )

    data class ParsedRtsp(
        val host: String,
        val port: Int,
        val user: String,
        val pass: String,
        val path: String
    )

    fun setupPathSuggestions(
        context: Context,
        pathView: AutoCompleteTextView,
        chipGroup: ChipGroup,
        initialValue: String = DEFAULT_PATH
    ) {
        pathView.setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, pathSuggestions))
        pathView.setText(initialValue.ifBlank { DEFAULT_PATH }, false)
        chipGroup.removeAllViews()
        pathSuggestions.forEach { suggestion ->
            val chip = Chip(context).apply {
                text = suggestion
                isCheckable = false
                setOnClickListener { pathView.setText(suggestion, false) }
            }
            chipGroup.addView(chip)
        }
    }

    fun setupPasswordTools(
        context: Context,
        passwordView: TextInputEditText,
        btnCopy: MaterialButton,
        btnPaste: MaterialButton
    ) {
        btnCopy.setOnClickListener {
            val text = passwordView.text?.toString().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(context, R.string.senha_vazia, Toast.LENGTH_SHORT).show()
            } else {
                copyText(context, text, context.getString(R.string.senha))
                Toast.makeText(context, R.string.senha_copiada, Toast.LENGTH_SHORT).show()
            }
        }
        btnPaste.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val pasted = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            if (pasted.isBlank()) {
                Toast.makeText(context, R.string.area_transferencia_vazia, Toast.LENGTH_SHORT).show()
            } else {
                passwordView.setText(pasted)
                passwordView.setSelection(passwordView.text?.length ?: 0)
                Toast.makeText(context, R.string.senha_colada, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun normalizePathChoice(raw: String): String {
        val value = raw.trim()
        if (value.isBlank() || value.equals(DEFAULT_PATH, ignoreCase = true)) return ""
        return if (value.startsWith("/")) value else "/$value"
    }

    fun buildRtspUrl(host: String, port: Int, user: String, pass: String, pathChoice: String): String {
        val auth = if (user.isBlank() && pass.isBlank()) "" else "${android.net.Uri.encode(user)}:${android.net.Uri.encode(pass)}@"
        val path = normalizePathChoice(pathChoice)
        return "rtsp://$auth$host:$port$path"
    }

    fun parseRtsp(url: String): ParsedRtsp {
        return try {
            val uri = android.net.Uri.parse(url)
            val encodedUserInfo = uri.encodedUserInfo.orEmpty()
            val encodedUser = encodedUserInfo.substringBefore(':', "")
            val encodedPass = encodedUserInfo.substringAfter(':', "")
            val path = (uri.encodedPath ?: "") + (uri.encodedQuery?.let { "?$it" } ?: "")
            ParsedRtsp(
                host = uri.host.orEmpty(),
                port = if (uri.port != -1) uri.port else 554,
                user = android.net.Uri.decode(encodedUser),
                pass = android.net.Uri.decode(encodedPass),
                path = path.ifBlank { DEFAULT_PATH }
            )
        } catch (_: Exception) {
            ParsedRtsp("", 554, "admin", "", DEFAULT_PATH)
        }
    }

    fun copyText(context: Context, text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
