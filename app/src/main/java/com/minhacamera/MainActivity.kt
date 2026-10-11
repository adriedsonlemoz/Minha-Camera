package com.minhacamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
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
            val c = cams[pos]
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra("name", c.name)
                    .putExtra("url", c.url)
                    .putExtra("alts", c.alts.toTypedArray())
            )
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            showOptions(pos)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1001, 0, R.string.novidades)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1001) {
            val version = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
            } catch (_: Exception) { "?" }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.novidades) + " • $version")
                .setMessage(R.string.novidades_text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
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

    private fun showOptions(pos: Int) {
        val options = arrayOf(getString(R.string.editar), getString(R.string.excluir))
        AlertDialog.Builder(this)
            .setTitle(cams[pos].name)
            .setItems(options) { _, which ->
                if (which == 0) showEditDialog(pos) else confirmDelete(pos)
            }
            .show()
    }

    private fun showEditDialog(pos: Int) {
        val c = cams[pos]
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_camera, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etUrl = view.findViewById<EditText>(R.id.etUrl)
        etName.setText(c.name)
        etUrl.setText(c.url)

        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.editar_camera)
            .setView(view)
            .setPositiveButton(R.string.salvar, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (!url.startsWith("rtsp://", ignoreCase = true)) {
                Toast.makeText(this, R.string.url_invalida, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val name = etName.text.toString().trim().ifEmpty { c.name }
            // Se o endereço mudou, os fluxos alternativos antigos deixam de valer.
            cams[pos] = Camera(name, url, if (url == c.url) c.alts else emptyList())
            store.save(cams)
            refresh()
            dlg.dismiss()
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
