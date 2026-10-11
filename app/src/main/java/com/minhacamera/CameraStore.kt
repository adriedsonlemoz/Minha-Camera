package com.minhacamera

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Camera(val name: String, val url: String)

class CameraStore(context: Context) {
    private val prefs = context.getSharedPreferences("cameras", Context.MODE_PRIVATE)

    fun load(): MutableList<Camera> {
        val out = mutableListOf<Camera>()
        try {
            val arr = JSONArray(prefs.getString("list", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Camera(o.getString("name"), o.getString("url")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun save(list: List<Camera>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs.edit().putString("list", arr.toString()).apply()
    }
}
