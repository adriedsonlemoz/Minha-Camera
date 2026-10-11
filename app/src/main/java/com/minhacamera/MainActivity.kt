package com.minhacamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var store: CameraStore
    private lateinit var listView: ListView
    private var cams = mutableListOf<Camera>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = getString(R.string.minhas_cameras)
        NetUtil.bindToWifi(this)

        store = CameraStore(this)
        cams = store.load()

        listView = findViewById(R.id.list)
        listView.emptyView = findViewById(R.id.empty)
        refresh()

        listView.setOnItemClickListener { _, _, pos, _ ->
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra("name", cams[pos].name)
                    .putExtra("url", cams[pos].url)
            )
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            confirmDelete(pos)
            true
        }

        // "+" abre a busca já com o diálogo manual (IP da rede pré-preenchido).
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java).putExtra("manual", true))
        }
        findViewById<View>(R.id.fabScan).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        cams = store.load()
        refresh()
    }

    private fun refresh() {
        val items = cams.map { "${it.name}\n${masked(it.url)}" }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
    }

    // Esconde usuário e senha na lista.
    private fun masked(url: String): String {
        return try {
            val u = Uri.parse(url)
            val host = u.host ?: return url
            val port = if (u.port != -1) ":${u.port}" else ""
            "rtsp://$host$port${u.path ?: ""}"
        } catch (_: Exception) {
            url
        }
    }

    private fun confirmDelete(pos: Int) {
        AlertDialog.Builder(this)
            .setTitle(cams[pos].name)
            .setMessage(R.string.excluir_pergunta)
            .setPositiveButton(R.string.excluir) { _, _ ->
                cams.removeAt(pos)
                store.save(cams)
                refresh()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }
}
