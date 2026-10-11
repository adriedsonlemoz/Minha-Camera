package com.minhacamera

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// alts = endereços alternativos da mesma câmera (ex.: fluxo secundário em H.264).
data class Camera(val name: String, val url: String, val alts: List<String> = emptyList())

class CameraStore(context: Context) {
    private val prefs = context.getSharedPreferences("cameras", Context.MODE_PRIVATE)

    fun load(): MutableList<Camera> {
        val out = mutableListOf<Camera>()
        try {
            val arr = JSONArray(prefs.getString("list", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val alts = mutableListOf<String>()
                o.optJSONArray("alts")?.let { a ->
                    for (j in 0 until a.length()) alts.add(a.getString(j))
                }
                out.add(Camera(o.getString("name"), o.getString("url"), alts))
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun save(list: List<Camera>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("url", it.url)
                    .put("alts", JSONArray(it.alts))
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }
}
