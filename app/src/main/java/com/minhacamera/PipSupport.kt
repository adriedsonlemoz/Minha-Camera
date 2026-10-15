package com.minhacamera

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import android.view.View

/** PiP explícito, somente em Activities de recepção: nunca mantém a câmera transmissora em background. */
internal object PipSupport {
    fun available(activity: Activity): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun enter(activity: Activity, preview: View, ratio: Rational = Rational(16, 9)): Boolean {
        if (!available(activity) || activity.isFinishing || activity.isDestroyed) return false
        return try {
            val builder = PictureInPictureParams.Builder().setAspectRatio(ratio)
            val rect = Rect()
            if (preview.getGlobalVisibleRect(rect) && !rect.isEmpty) builder.setSourceRectHint(rect)
            // MJPEG usa Bitmap/ImageView; a transição sem resize contínuo evita distorções.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setSeamlessResizeEnabled(false)
            activity.enterPictureInPictureMode(builder.build())
        } catch (_: IllegalStateException) { false }
          catch (_: IllegalArgumentException) { false }
          catch (_: SecurityException) { false }
    }
}
