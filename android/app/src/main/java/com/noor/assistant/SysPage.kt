package com.noor.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.sin

/** Noor ka poora system ek jagah: kya chal raha hai, kya nahi, aur kyun. */
class SysPage(private val ctx: Context) : FrameLayout(ctx) {
    private class Item(val group: String, val name: String, var st: Int, var detail: String, val fix: (() -> Unit)?)

    private val items = ArrayList<Item>()
    private val list = LinearLayout(ctx)
    private val head = SysHead(ctx)
    private val sub = TextView(ctx)
    private val ok = Color.parseColor("#34D399")
    private val warn = Color.parseColor("#FBBF24")
    private val bad = Color.parseColor("#EF4444")
    private val cy = Color.parseColor("#22D3EE")
    private var diagGen = 0

    init {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#020B1A"), Color.parseColor("#04204A")))
        val sv = ScrollView(ctx)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(14), dp(10), dp(14), dp(24))
        list.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)))
        sub.textSize = 14f; sub.setTextColor(Color.WHITE); sub.gravity = Gravity.CENTER
        sub.setPadding(dp(8), dp(4), dp(8), dp(8))
        list.addView(sub, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val re = TextView(ctx)
        re.text = "↻  DOBARA CHECK KARO"; re.textSize = 14f; re.setTextColor(Color.parseColor("#04121C")); re.typeface = android.graphics.Typeface.DEFAULT_BOLD
        re.gravity = Gravity.CENTER; re.setPadding(dp(8), dp(12), dp(8), dp(12))
        re.background = box(cy, 14, 0)
        re.setOnClickListener { refresh() }
        list.addView(re, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(0, 0, 0, dp(6)) })
        sv.addView(list)
        addView(sv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun box(fill: Int, r: Int, stroke: Int): GradientDrawable {
        val g = GradientDrawable(); g.setColor(fill); g.cornerRadius = dp(r).toFloat(); if (stroke != 0) g.setStroke(dp(1), stroke); return g
    }
    private fun prefs() = ctx.getSharedPreferences("noor", Context.MODE_PRIVATE)
    private fun perm(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun open(i: Intent) { try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {} }
    private fun appSettings() = open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))

    private fun online(): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val n = cm.activeNetwork
        n != null && cm.getNetworkCapabilities(n)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) { true }

    fun refresh() {
        items.clear()
        val gen = ++diagGen
        val net = online()
        items.add(Item("PHONE", "Internet", if (net) 0 else 2, if (net) "Net chal raha hai" else "Net nahi hai. Wi-Fi ya data ON karein.", { open(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }))
        items.add(Item("PHONE", "Noor ON/OFF (service)", if (NoorService.isRunning) 0 else 1, if (NoorService.isRunning) "Noor sun rahi hai" else "Noor band hai. Home par mic dabayein.", null))
        items.add(Item("PHONE", "Mic ki ijazat", if (perm(Manifest.permission.RECORD_AUDIO)) 0 else 2, if (perm(Manifest.permission.RECORD_AUDIO)) "Theek hai" else "Mic ki ijazat nahi, Noor sun nahi sakti. Dabayein aur ijazat dein.", { appSettings() }))
        items.add(Item("PHONE", "Camera ki ijazat", if (perm(Manifest.permission.CAMERA)) 0 else 1, if (perm(Manifest.permission.CAMERA)) "Theek hai" else "Camera se dekhne ke liye chahiye.", { appSettings() }))
        items.add(Item("PHONE", "Contacts / Call", if (perm(Manifest.permission.READ_CONTACTS) && perm(Manifest.permission.CALL_PHONE)) 0 else 1, if (perm(Manifest.permission.READ_CONTACTS) && perm(Manifest.permission.CALL_PHONE)) "Theek hai" else "Call/message ke liye contacts aur call ki ijazat dein.", { appSettings() }))
        val acc = (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(ctx.packageName)
        items.add(Item("PHONE", "Screen control (Accessibility)", if (acc) 0 else 2, if (acc) "Noor screen par tap/type kar sakti hai" else "BAND hai. Noor apps nahi chala sakti. Dabayein aur Noor ko ON karein.", { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }))
        val nl = (Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "").contains(ctx.packageName)
        items.add(Item("PHONE", "Notifications padhna", if (nl) 0 else 1, if (nl) "Theek hai" else "Notification access band hai.", { open(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }))
        val ov = Settings.canDrawOverlays(ctx)
        items.add(Item("PHONE", "Doosri apps ke upar dikhna", if (ov) 0 else 1, if (ov) "Theek hai" else "Overlay ki ijazat dein taake Noor upar dikhe.", { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))) }))
        val bat = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
        items.add(Item("PHONE", "Battery (band na ho)", if (bat) 0 else 1, if (bat) "Theek hai" else "Phone Noor ko so sakta hai. 'No restrictions' karein.", { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }))
        val tokOk = !prefs().getString("token", "").isNullOrBlank()
        items.add(Item("SERVER", "Password (app token)", if (tokOk) 0 else 2, if (tokOk) "Set hai" else "Settings mein password daalein.", null))
        items.add(Item("SERVER", "Server (Vercel)", 3, "Check ho raha hai...", null))
        items.add(Item("SERVER", "Dimaagh (chat jawab)", 3, "Check ho raha hai...", null))
        items.add(Item("SERVER", "Noor ki awaaz (TTS)", 3, "Check ho raha hai...", null))
        items.add(Item("SERVER", "Live voice", 3, "Check ho raha hai...", null))
        items.add(Item("SERVER", "Tasveer / Hologram", 3, "Check ho raha hai...", null))
        items.add(Item("SERVER", "Net search", 3, "Check ho raha hai...", null))
        items.add(Item("SERVER", "Yaadein (cloud)", 3, "Check ho raha hai...", null))
        if (Net.ttsErr.isNotBlank()) items.add(Item("PICHLI GALTI", "Pichli awaaz ka masla", 1, Net.ttsErr, null))
        if (Net.holoErr.isNotBlank()) items.add(Item("PICHLI GALTI", "Pichla hologram", 1, Net.holoErr, null))
        render()
        if (!net || !tokOk) {
            for (n in listOf("Server (Vercel)", "Dimaagh (chat jawab)", "Noor ki awaaz (TTS)", "Live voice", "Tasveer / Hologram", "Net search", "Yaadein (cloud)")) set(n, 2, if (!net) "Net nahi, check nahi ho saka" else "Password nahi", null)
            return
        }
        val base = prefs().getString("url", MainActivity.DEFAULT_URL)!!.trim().trimEnd('/')
        val token = prefs().getString("token", "")!!
        Thread {
            var j: JSONObject? = null
            var err = ""
            try {
                val c = URL("$base/diag").openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 30000
                c.setRequestProperty("x-app-token", token)
                val code = c.responseCode
                if (code == 200) j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                else err = if (code == 401) "Password galat" else if (code == 404) "Server purana hai (Vercel Redeploy karein)" else "server code $code"
            } catch (e: Exception) {
                err = (e.message ?: e.javaClass.simpleName).take(80)
            }
            post {
                if (gen != diagGen) return@post
                if (j == null) {
                    set("Server (Vercel)", 2, "Server se jud nahi paya: $err", null)
                    for (n in listOf("Dimaagh (chat jawab)", "Noor ki awaaz (TTS)", "Live voice", "Tasveer / Hologram", "Net search", "Yaadein (cloud)")) set(n, 2, "Server nahi mila", null)
                } else {
                    set("Server (Vercel)", 0, "Chal raha hai (${j.optInt("ms")} ms)", null)
                    val l = j.optJSONObject("llm")
                    if (l != null) set("Dimaagh (chat jawab)", if (l.optBoolean("ok")) 0 else 2,
                        if (l.optBoolean("ok")) "Jawab de raha hai (${l.optInt("ms")} ms)" else "Nahi chal raha: ${l.optString("err")}" + (if (l.optInt("status") == 503 || l.optInt("status") == 429) "\nGoogle par rush ya limit hai, thodi der baad theek ho jata hai." else ""), null)
                    val t = j.optJSONObject("tts")
                    if (t != null) set("Noor ki awaaz (TTS)", if (t.optBoolean("ok")) 0 else 1, if (t.optBoolean("ok")) "Gemini awaaz chal rahi hai (${t.optInt("ms")} ms)" else "Gemini awaaz nahi: ${t.optString("err")}\nPhone ki awaaz use hogi.", null)
                    val lv = j.optJSONObject("live")
                    if (lv != null) set("Live voice", if (lv.optBoolean("ok")) 0 else 2, if (lv.optBoolean("ok")) "Key hai. Model: ${lv.optString("models")}" else "Gemini key nahi mili", null)
                    val im = j.optJSONObject("image")
                    if (im != null) set("Tasveer / Hologram", if (im.optBoolean("ok")) 0 else 2, if (im.optBoolean("ok")) "Key hai. Model: ${im.optString("models")}\n(Free limit khatam ho to watermark wali tasveer aati hai)" else "Gemini key nahi", null)
                    val se = j.optJSONObject("search")
                    if (se != null) set("Net search", if (se.optBoolean("ok")) 0 else 1, se.optString("note"), null)
                    val me = j.optJSONObject("memory")
                    if (me != null) set("Yaadein (cloud)", if (me.optBoolean("ok")) 0 else 1, if (me.optBoolean("ok")) "Upstash jura hai" else me.optString("err"), null)
                }
                render()
            }
        }.start()
    }

    private fun set(name: String, st: Int, d: String, fix: (() -> Unit)?) {
        val i = items.indexOfFirst { it.name == name }
        if (i >= 0) { val o = items[i]; items[i] = Item(o.group, o.name, st, d, fix ?: o.fix) }
    }

    private fun render() {
        while (list.childCount > 3) list.removeViewAt(3)
        var last = ""
        for (it in items) {
            if (it.group != last) {
                last = it.group
                val h = TextView(ctx); h.text = it.group; h.textSize = 12f; h.setTextColor(Color.parseColor("#7DD3FC")); h.letterSpacing = 0.15f
                h.typeface = android.graphics.Typeface.DEFAULT_BOLD; h.setPadding(dp(4), dp(14), 0, dp(4))
                list.addView(h)
            }
            val col = when (it.st) { 0 -> ok; 1 -> warn; 2 -> bad; else -> cy }
            val row = LinearLayout(ctx); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12), dp(10), dp(12), dp(10))
            row.background = box(Color.parseColor("#0A2A4A"), 14, col)
            val dot = View(ctx); dot.background = box(col, 8, 0)
            row.addView(dot, LinearLayout.LayoutParams(dp(14), dp(14)).also { p -> p.setMargins(0, 0, dp(12), 0) })
            val tx = LinearLayout(ctx); tx.orientation = LinearLayout.VERTICAL
            val a = TextView(ctx); a.text = it.name; a.textSize = 15f; a.setTextColor(Color.WHITE); a.typeface = android.graphics.Typeface.DEFAULT_BOLD
            val b = TextView(ctx); b.text = it.detail; b.textSize = 12.5f; b.setTextColor(if (it.st == 0) Color.parseColor("#9FB8D0") else col)
            tx.addView(a); tx.addView(b)
            row.addView(tx, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val tag = TextView(ctx); tag.textSize = 11f; tag.typeface = android.graphics.Typeface.DEFAULT_BOLD; tag.setTextColor(col)
            tag.text = when (it.st) { 0 -> "OK"; 1 -> "DHYAAN"; 2 -> "MASLA"; else -> "..." }
            row.addView(tag)
            if (it.fix != null && it.st != 0) row.setOnClickListener { _ -> it.fix.invoke() }
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { p -> p.setMargins(0, dp(4), 0, dp(4)) })
        }
        val done = items.count { it.st != 3 }
        val okN = items.count { it.st == 0 }
        val badN = items.count { it.st == 2 }
        val warnN = items.count { it.st == 1 }
        head.score = if (items.isEmpty()) 0f else okN * 1f / items.size
        head.label = "$okN/${items.size}"
        val firstBad = items.firstOrNull { it.st == 2 } ?: items.firstOrNull { it.st == 1 }
        sub.text = if (done < items.size) "Check ho raha hai..." else if (badN == 0 && warnN == 0) "Sab kuch theek hai" else "$badN masle, $warnN dhyaan wali baatein\nSabse pehle: ${firstBad?.name}"
        sub.setTextColor(if (badN > 0) bad else if (warnN > 0) warn else ok)
    }

    /** Ghoomta 3D hologram (wireframe cube + ring) aur score. */
    private class SysHead(c: Context) : View(c) {
        var score = 0f
        var label = ""
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var t = 0f
        private val vx = floatArrayOf(-1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f)
        private val vy = floatArrayOf(-1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f)
        private val vz = floatArrayOf(-1f, -1f, -1f, -1f, 1f, 1f, 1f, 1f)
        private val ed = intArrayOf(0, 1, 1, 2, 2, 3, 3, 0, 4, 5, 5, 6, 6, 7, 7, 4, 0, 4, 1, 5, 2, 6, 3, 7)
        override fun onDraw(c: Canvas) {
            t += 0.016f
            val w = width.toFloat(); val h = height.toFloat()
            val cx = w / 2f; val cyy = h * 0.5f; val s = h * 0.22f
            val d = resources.displayMetrics.density
            p.shader = android.graphics.RadialGradient(cx, cyy, h * 0.6f, Color.parseColor("#3322D3EE"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            p.style = Paint.Style.FILL; c.drawCircle(cx, cyy, h * 0.6f, p); p.shader = null
            val ax = t * 0.9f; val ay = t * 0.6f
            val px = FloatArray(8); val py = FloatArray(8)
            for (i in 0 until 8) {
                var x = vx[i]; var y = vy[i]; var z = vz[i]
                val x1 = x * cos(ay) + z * sin(ay); val z1 = -x * sin(ay) + z * cos(ay)
                val y1 = y * cos(ax) - z1 * sin(ax); val z2 = y * sin(ax) + z1 * cos(ax)
                val k = 3.2f / (3.2f + z2)
                px[i] = cx + x1 * s * k; py[i] = cyy + y1 * s * k
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = Color.parseColor("#22D3EE")
            for (i in ed.indices step 2) c.drawLine(px[ed[i]], py[ed[i]], px[ed[i + 1]], py[ed[i + 1]], p)
            p.strokeWidth = 5f * d; p.color = Color.parseColor("#1F3B57")
            val r = h * 0.42f
            val oval = android.graphics.RectF(cx - r, cyy - r, cx + r, cyy + r)
            c.drawArc(oval, 0f, 360f, false, p)
            p.color = if (score > 0.8f) Color.parseColor("#34D399") else if (score > 0.5f) Color.parseColor("#FBBF24") else Color.parseColor("#EF4444")
            c.drawArc(oval, -90f, 360f * score, false, p)
            p.style = Paint.Style.FILL; p.textSize = 22f * d; p.textAlign = Paint.Align.CENTER; p.color = Color.WHITE
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            c.drawText(label, cx, cyy + h * 0.5f - 6f * d - h * 0.0f + 0f, p)
            p.textSize = 11f * d; p.color = Color.parseColor("#7DD3FC"); c.drawText("NOOR SYSTEM", cx, 18f * d, p)
            // scan line
            p.color = Color.parseColor("#6622D3EE"); val sy = (t * 60f * d) % h
            c.drawRect(cx - r, sy, cx + r, sy + 2f * d, p)
            postInvalidateDelayed(28)
        }
    }
}
