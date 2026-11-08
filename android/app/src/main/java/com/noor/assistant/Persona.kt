package com.noor.assistant

import android.content.Context

/** Noor (ladki, "Rizwan") ya Rox (ladka, "Sir"). Dono ka apna andaz, par yaadein ek. */
object Persona {
    @Volatile
    var id = "noor"

    val isRox: Boolean get() = id == "rox"
    val name: String get() = if (isRox) "ROX" else "NOOR"
    val title: String get() = if (isRox) "Rox" else "Noor"
    val you: String get() = if (isRox) "Sir" else "Rizwan"

    fun load(ctx: Context) {
        id = ctx.getSharedPreferences("noor", Context.MODE_PRIVATE).getString("persona", "noor") ?: "noor"
    }

    fun set(ctx: Context, v: String) {
        id = if (v == "rox") "rox" else "noor"
        ctx.getSharedPreferences("noor", Context.MODE_PRIVATE).edit().putString("persona", id).apply()
    }
}
