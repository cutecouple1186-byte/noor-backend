package com.noor.assistant

import android.graphics.Bitmap

/** HUD ki saanjhi halat: bana hua hologram, "bana raha hai" nishan. */
object Hud {
    @Volatile var holo: Bitmap? = null
    @Volatile var busy = false
    var listener: (() -> Unit)? = null

    fun setHolo(b: Bitmap?) {
        holo = b
        ping()
    }

    fun ping() {
        listener?.invoke()
    }
}
