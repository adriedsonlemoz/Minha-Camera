package com.minhacamera

import android.content.Context
import java.security.SecureRandom

/** Código de pareamento estável para o mesmo telefone, revogável pelo usuário. */
object PhoneCameraAccess {
    private const val PREFS = "phone_camera_access"
    private const val KEY = "token"
    private fun randomToken(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun token(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.takeIf { it.matches(Regex("[0-9a-f]{32}")) }?.let { return it }
        val generated = randomToken()
        check(prefs.edit().putString(KEY, generated).commit()) { "Falha ao armazenar código de acesso" }
        return generated
    }
    fun revoke(ctx: Context) {
        val generated = randomToken()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(prefs.edit().putString(KEY, generated).commit()) { "Falha ao renovar código de acesso" }
    }
}
