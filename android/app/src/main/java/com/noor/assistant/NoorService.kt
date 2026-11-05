package com.noor.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Background mein chalne wali Noor. "Hey Noor" sunti hai, server se jawab leti hai,
 * bolti hai aur phone ke kaam karti hai.
 */
class NoorService : Service() {

    companion object {
        const val CHANNEL = "noor"
        const val ACTION_STOP = "com.noor.assistant.STOP"

        @Volatile
        var isRunning = false
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var speaking = false
    private var busy = false
    private var activeUntil = 0L
    private val history = ArrayList<Pair<String, String>>()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private var wake: PowerManager.WakeLock? = null

    private val wakeRe = Regex("(hey|hay|hi|he|ae|ai|है|हे|हेय|ऐ|ہے|اے|ہی)?\\s*(noor|nur|nour|नूर|نور)")
    private val stopRe = Regex("\\bstop\\b|band karo|sunna band|bas karo|chup ho|chup kar|सुनना बंद|बंद करो|बस करो|चुप|بند کرو|سننا بند|بس کرو|چپ")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val n = buildNote("Noor sun rahi hai. \"Hey Noor\" bolein.")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
        if (!running) {
            running = true
            isRunning = true
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "noor:listen")
                wake?.acquire(12L * 60 * 60 * 1000)
            } catch (e: Exception) {
            }
            tts = TextToSpeech(this) { st ->
                ttsReady = st == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            main.post { doneSpeaking() }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            main.post { doneSpeaking() }
                        }
                    })
                }
            }
            listen(500)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        isRunning = false
        main.removeCallbacksAndMessages(null)
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
        }
        try {
            player?.release()
        } catch (e: Exception) {
        }
        try {
            tts?.shutdown()
        } catch (e: Exception) {
        }
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    // ---------- notification ----------
    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Noor", NotificationManager.IMPORTANCE_LOW))
    }

    @Suppress("DEPRECATION")
    private fun buildNote(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, NoorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Noor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Band karo", stop)
            .build()
    }

    private fun note(text: String) {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, buildNote(text))
        } catch (e: Exception) {
        }
    }

    // ---------- sunna ----------
    private fun listen(delayMs: Long = 0) {
        main.postDelayed({ startRecognizer() }, delayMs)
    }

    private fun startRecognizer() {
        if (!running || speaking || busy) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            note("Phone mein speech service nahi mili (Google app chahiye).")
            return
        }
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
        }
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    note("Mic ki ijazat chahiye. Noor app kholkar allow karein.")
                    return
                }
                val wait = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) 1500L else 300L
                listen(wait)
            }

            override fun onResults(results: android.os.Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) {
                    listen(200)
                    return
                }
                handleHeard(t)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        try {
            r.startListening(i)
        } catch (e: Exception) {
            listen(1500)
        }
    }

    private fun handleHeard(raw: String) {
        val lower = raw.lowercase().trim()
        var text = raw.trim()
        val now = System.currentTimeMillis()
        if (wakeRe.containsMatchIn(lower)) {
            activeUntil = now + 60000
            text = lower.replace(wakeRe, "").trim()
        }
        if (now > activeUntil) {
            listen(100)
            return
        }
        if (stopRe.containsMatchIn(lower)) {
            activeUntil = 0
            say("Theek hai, jab chahein Hey Noor bolein.")
            return
        }
        if (text.isEmpty()) {
            say("Ji, boliye.")
            return
        }
        ask(text)
    }

    // ---------- server se jawab ----------
    private fun prefs() = getSharedPreferences("noor", Context.MODE_PRIVATE)

    private fun memories(): List<String> =
        prefs().getString("memories", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun context(): String {
        val time = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        var s = "Local time: $time."
        try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            s += " Battery: ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%."
        } catch (e: Exception) {
        }
        return s
    }

    private fun ask(text: String) {
        busy = true
        note("Soch rahi hoon...")
        val base = prefs().getString("url", MainActivity.DEFAULT_URL)!!.trim().trimEnd('/')
        val token = prefs().getString("token", "")!!
        val hist = ArrayList(history)
        val mem = memories()
        val ctx = context()
        Thread {
            val res = try {
                Net.chat(base, token, text, hist, mem, ctx)
            } catch (e: Exception) {
                JSONObject().put("reply", "Internet check karein, server se connection nahi ho paya.")
                    .put("intent", "none").put("slots", JSONObject())
            }
            main.post {
                busy = false
                onResult(text, res)
            }
        }.start()
    }

    private fun onResult(userText: String, j: JSONObject) {
        val reply = j.optString("reply")
        val intent = j.optString("intent", "none")
        val slots = j.optJSONObject("slots") ?: JSONObject()
        history.add(Pair("user", userText))
        history.add(Pair("assistant", reply))
        while (history.size > 12) history.removeAt(0)

        when (intent) {
            "none", "search", "weather", "" -> say(reply)
            "remember" -> {
                val t = slots.optString("text")
                if (t.isNotBlank()) {
                    val all = (memories() + t.replace("\n", " ")).takeLast(20)
                    prefs().edit().putString("memories", all.joinToString("\n")).apply()
                    say("Theek hai, yaad rakh liya.")
                } else {
                    say(reply)
                }
            }
            "camera" -> say("Camera wala kaam abhi sirf website mein hai.")
            else -> {
                val fail = Actions.execute(this, intent, slots)
                say(fail ?: reply)
            }
        }
    }

    // ---------- bolna ----------
    private var speakGen = 0

    private fun say(text: String) {
        if (text.isBlank()) {
            doneSpeaking()
            return
        }
        speaking = true
        val g = ++speakGen
        main.postDelayed({ if (speaking && g == speakGen) doneSpeaking() }, 40000)
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
        }
        note(text)
        val base = prefs().getString("url", MainActivity.DEFAULT_URL)!!.trim().trimEnd('/')
        val token = prefs().getString("token", "")!!
        Thread {
            val f = try {
                Net.tts(base, token, text, cacheDir)
            } catch (e: Exception) {
                null
            }
            main.post {
                if (!running) return@post
                if (f != null) playFile(f, text) else speakLocal(text)
            }
        }.start()
    }

    private fun playFile(f: java.io.File, text: String) {
        try {
            player?.release()
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener {
                it.release()
                player = null
                doneSpeaking()
            }
            p.setOnErrorListener { mp, _, _ ->
                mp.release()
                player = null
                speakLocal(text)
                true
            }
            p.prepare()
            p.start()
            player = p
        } catch (e: Exception) {
            speakLocal(text)
        }
    }

    private fun speakLocal(text: String) {
        val t = tts
        if (!ttsReady || t == null) {
            doneSpeaking()
            return
        }
        val arabic = text.any { it in '؀'..'ۿ' }
        t.setLanguage(if (arabic) Locale("ur", "PK") else Locale("hi", "IN"))
        t.setPitch(1.1f)
        if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "noor") != TextToSpeech.SUCCESS) doneSpeaking()
    }

    private fun doneSpeaking() {
        speaking = false
        activeUntil = maxOf(activeUntil, System.currentTimeMillis() + 60000)
        note("Noor sun rahi hai. \"Hey Noor\" bolein.")
        listen(200)
    }
}
