package com.noor.assistant

import android.app.Application
import java.io.File

/** Crash hua to wajah file mein likh leta hai, agli baar app dikhati hai. */
class NoorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                File(filesDir, "crash.txt").writeText("[" + t.name + "] " + sw.toString().take(1800))
            } catch (x: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
    }
}
