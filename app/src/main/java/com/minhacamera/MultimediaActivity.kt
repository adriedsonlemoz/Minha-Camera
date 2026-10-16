package com.minhacamera

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Compatibilidade para os atalhos antigos: agora abre diretamente a galeria integrada. */
class MultimediaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, GalleryActivity::class.java))
        finish()
    }
}
