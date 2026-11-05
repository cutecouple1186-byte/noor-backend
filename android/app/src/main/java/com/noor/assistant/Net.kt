package com.noor.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Noor ke server (Vercel) se baat: /chat (jawab) aur /tts (awaaz). */
object Net {

    private fun fail(reply: String): JSONObject =
        JSONObject().put("reply", reply).put("intent", "none").put("slots", JSONObject())

    private fun open(url: String, token: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 25000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.setRequestProperty("x-app-token", token)
        return c
    }

    fun chat(
        base: String,
        token: String,
        message: String,
        history: List<Pair<String, String>>,
        memories: List<String>,
        context: String
    ): JSONObject {
        val body = JSONObject()
        body.put("message", message)
        val h = JSONArray()
        for ((role, content) in history.takeLast(12)) {
            h.put(JSONObject().put("role", role).put("content", content))
        }
        body.put("history", h)
        val m = JSONArray()
        for (x in memories.takeLast(20)) m.put(x)
        body.put("memories", m)
        body.put("context", context)

        var last = fail("Maaf kijiye, abhi mujhe jawab nahi mil raha.")
        for (attempt in 0..1) {
            try {
                val c = open("$base/chat", token)
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                if (code == 401) return fail("Password galat hai. App mein dobara daalein.")
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code in 200..299) return JSONObject(text)
                last = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    last
                }
            } catch (e: Exception) {
                last = fail("Internet check karein, server se connection nahi ho paya.")
            }
        }
        return last
    }

    /** Server ki Gemini awaaz (WAV). Na chale to null, tab phone ki apni awaaz use hoti hai. */
    fun tts(base: String, token: String, text: String, dir: File): File? {
        return try {
            val c = open("$base/tts", token)
            c.connectTimeout = 4000
            c.readTimeout = 7000
            c.outputStream.use {
                it.write(JSONObject().put("text", text).toString().toByteArray(Charsets.UTF_8))
            }
            if (c.responseCode != 200) return null
            val f = File(dir, "noor_tts.wav")
            c.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            if (f.length() < 100) null else f
        } catch (e: Exception) {
            null
        }
    }
}
