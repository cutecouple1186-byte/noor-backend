package com.noor.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.JsPromptResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Noor ki screen: Home (baat-cheet), Vision (camera), Memory, Settings. */
class MainActivity : Activity() {

    companion object {
        const val DEFAULT_URL = "https://noor-backend-nine.vercel.app"
        const val EXTRA_AUTOSTART = "autostart"

        val BG = Color.parseColor("#070B14")
        val CARD = Color.parseColor("#0E1726")
        val LINE = Color.parseColor("#1E3A52")
        val CYAN = Color.parseColor("#22D3EE")
        val PINK = Color.parseColor("#E879F9")
        val TEXT = Color.parseColor("#E6F1FF")
        val MUTED = Color.parseColor("#7F93AD")
        val RED = Color.parseColor("#EF4444")
    }

    private val ui = Handler(Looper.getMainLooper())
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var pages: FrameLayout
    private lateinit var homePage: View
    private lateinit var visionPage: FrameLayout
    private lateinit var memoryPage: View
    private lateinit var settingsPage: View
    private val navItems = ArrayList<TextView>()
    private lateinit var micBtn: TextView

    // Home
    private lateinit var orb: OrbView
    private lateinit var statePill: TextView
    private lateinit var greeting: TextView
    private lateinit var detailTv: TextView
    private lateinit var moodTv: TextView
    private lateinit var logBox: LinearLayout
    private lateinit var homeScroll: ScrollView
    private lateinit var input: EditText
    private var rendered = 0

    // Memory
    private lateinit var memTv: TextView

    // Settings
    private lateinit var statusTv: TextView
    private lateinit var autoBtn: Button
    private lateinit var urlEt: EditText
    private lateinit var tokEt: EditText

    private var web: WebView? = null
    private var current = 0

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun prefs() = getSharedPreferences("noor", Context.MODE_PRIVATE)

    private fun shape(fill: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor)
        return g
    }

    private fun tv(text: String, sizeSp: Float, color: Int = TEXT, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sizeSp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        return t
    }

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    private fun pill(label: String, onClick: () -> Unit): TextView {
        val t = tv(label, 14f, TEXT)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(10), dp(8), dp(10))
        t.background = shape(CARD, 18, LINE, 1)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setTextColor(TEXT)
        b.background = shape(CARD, 14, LINE, 1)
        b.setOnClickListener { onClick() }
        val p = lp(MATCH, WRAP)
        p.topMargin = dp(8)
        b.layoutParams = p
        return b
    }

    // ------------------------------------------------------------------ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(BG)

        pages = FrameLayout(this)
        homePage = buildHome()
        visionPage = FrameLayout(this)
        memoryPage = buildMemory()
        settingsPage = buildSettings()
        pages.addView(homePage, FrameLayout.LayoutParams(MATCH, MATCH))
        pages.addView(visionPage, FrameLayout.LayoutParams(MATCH, MATCH))
        pages.addView(memoryPage, FrameLayout.LayoutParams(MATCH, MATCH))
        pages.addView(settingsPage, FrameLayout.LayoutParams(MATCH, MATCH))

        root.addView(pages, lp(MATCH, 0, 1f))
        root.addView(buildNav(), lp(MATCH, WRAP))
        setContentView(root)
        show(0)

        // Phone on hone par BootReceiver is screen ko kholta hai; yahan se Noor shuru karke screen peeche kar dete hain.
        if (intent?.getBooleanExtra(EXTRA_AUTOSTART, false) == true) {
            if (!NoorService.isRunning && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                try {
                    startForegroundService(Intent(this, NoorService::class.java))
                } catch (e: Exception) {
                }
            }
            root.post { moveTaskToBack(true) }
        }
    }

    override fun onResume() {
        super.onResume()
        Chat.listener = { refreshHome() }
        refreshHome()
        refreshSettings()
        refreshMemory()
    }

    override fun onPause() {
        Chat.listener = null
        saveSettings()
        super.onPause()
    }

    override fun onDestroy() {
        try {
            web?.destroy()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ navigation
    private fun buildNav(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Color.parseColor("#0A1220"))
        bar.setPadding(0, dp(6), 0, dp(8))

        fun item(label: String, idx: Int): TextView {
            val t = tv(label, 12f, MUTED)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(6), 0, dp(6))
            t.setOnClickListener { show(idx) }
            navItems.add(t)
            return t
        }

        bar.addView(item("⌂\nHome", 0), lp(0, WRAP, 1f))
        bar.addView(item("◉\nVision", 1), lp(0, WRAP, 1f))

        micBtn = tv("🎙", 26f, Color.WHITE)
        micBtn.gravity = Gravity.CENTER
        micBtn.setOnClickListener { toggleService() }
        val mp = lp(dp(64), dp(64))
        mp.setMargins(dp(6), 0, dp(6), 0)
        bar.addView(micBtn, mp)

        bar.addView(item("★\nMemory", 2), lp(0, WRAP, 1f))
        bar.addView(item("⚙\nSettings", 3), lp(0, WRAP, 1f))
        return bar
    }

    private fun show(idx: Int) {
        current = idx
        homePage.visibility = if (idx == 0) View.VISIBLE else View.GONE
        visionPage.visibility = if (idx == 1) View.VISIBLE else View.GONE
        memoryPage.visibility = if (idx == 2) View.VISIBLE else View.GONE
        settingsPage.visibility = if (idx == 3) View.VISIBLE else View.GONE
        for (i in navItems.indices) navItems[i].setTextColor(if (i == idx) CYAN else MUTED)
        if (idx == 1) openVision()
        if (idx == 2) refreshMemory()
        if (idx == 3) refreshSettings()
    }

    // ------------------------------------------------------------------ Home
    private fun buildHome(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(16), dp(14), dp(16), dp(4))
        val title = tv("NOOR", 22f, CYAN, true)
        title.letterSpacing = 0.25f
        header.addView(title, lp(0, WRAP, 1f))
        statePill = tv("OFF", 11f, TEXT, true)
        statePill.setPadding(dp(12), dp(5), dp(12), dp(5))
        header.addView(statePill, lp(WRAP, WRAP))
        col.addView(header, lp(MATCH, WRAP))

        homeScroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), 0, dp(16), dp(8))

        greeting = tv("", 20f, TEXT, true)
        content.addView(greeting, lp(MATCH, WRAP))
        detailTv = tv("", 12f, MUTED)
        content.addView(detailTv, lp(MATCH, WRAP))

        orb = OrbView(this)
        content.addView(orb, lp(MATCH, dp(220)))

        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        fun chip(label: String, msg: String) {
            val c = pill(label) { sendTyped(msg) }
            val p = lp(0, WRAP, 1f)
            p.setMargins(dp(3), 0, dp(3), 0)
            chips.addView(c, p)
        }
        chip("🎵 Music", "YouTube par mere liye achhe gaane lagao")
        chip("📖 Study", "Mujhe padhai mein madad chahiye, mujhse sawal poochho")
        chip("💬 Vent", "Mera dil halka karna hai, meri baat suno")
        chip("☁ Mausam", "Aaj ka mausam batao")
        content.addView(chips, lp(MATCH, WRAP))

        val cards = LinearLayout(this)
        cards.orientation = LinearLayout.HORIZONTAL
        fun card(head: String, value: TextView, onClick: (() -> Unit)? = null) {
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(dp(12), dp(10), dp(12), dp(10))
            box.background = shape(CARD, 16, LINE, 1)
            box.addView(tv(head, 10f, MUTED, true))
            box.addView(value)
            if (onClick != null) box.setOnClickListener { onClick() }
            val p = lp(0, WRAP, 1f)
            p.setMargins(dp(3), dp(10), dp(3), 0)
            cards.addView(box, p)
        }
        card("AAJ", tv(SimpleDateFormat("d MMM, EEE", Locale.ENGLISH).format(Date()), 15f, TEXT, true))
        card("MAUSAM", tv("Poochho", 15f, TEXT, true)) { sendTyped("Aaj ka mausam batao") }
        moodTv = tv("-", 15f, PINK, true)
        card("MOOD", moodTv)
        content.addView(cards, lp(MATCH, WRAP))

        logBox = LinearLayout(this)
        logBox.orientation = LinearLayout.VERTICAL
        logBox.setPadding(0, dp(12), 0, 0)
        content.addView(logBox, lp(MATCH, WRAP))

        homeScroll.addView(content)
        col.addView(homeScroll, lp(MATCH, 0, 1f))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(6), dp(12), dp(8))
        input = EditText(this)
        input.hint = "Noor ko likhkar poochho..."
        input.setHintTextColor(MUTED)
        input.setTextColor(TEXT)
        input.textSize = 15f
        input.maxLines = 3
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.background = shape(CARD, 24, LINE, 1)
        input.setPadding(dp(16), dp(10), dp(16), dp(10))
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                doSend()
                true
            } else {
                false
            }
        }
        row.addView(input, lp(0, WRAP, 1f))
        val send = tv("➤", 20f, Color.parseColor("#04121C"), true)
        send.gravity = Gravity.CENTER
        send.background = shape(CYAN, 24)
        send.setOnClickListener { doSend() }
        val sp = lp(dp(48), dp(48))
        sp.leftMargin = dp(8)
        row.addView(send, sp)
        col.addView(row, lp(MATCH, WRAP))
        return col
    }

    private fun doSend() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        sendTyped(t)
    }

    /** Likha hua sawaal Noor ko bhejo. Service band ho to pehle shuru karo. */
    private fun sendTyped(text: String) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Pehle Settings mein button 1 dabakar ijazat dein", Toast.LENGTH_LONG).show()
            askPermissions()
            return
        }
        saveSettings()
        if (NoorService.isRunning) {
            NoorService.submit(text)
            return
        }
        try {
            startForegroundService(Intent(this, NoorService::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "Noor shuru nahi ho saki", Toast.LENGTH_LONG).show()
            return
        }
        var tries = 0
        val r = object : Runnable {
            override fun run() {
                if (NoorService.instanceReady()) {
                    NoorService.submit(text)
                } else if (tries++ < 8) {
                    ui.postDelayed(this, 400)
                }
            }
        }
        ui.postDelayed(r, 500)
    }

    private fun refreshHome() {
        val st = Chat.state
        orb.state = st
        val (label, color) = when (st) {
            "listening" -> Pair("LISTENING", CYAN)
            "thinking" -> Pair("THINKING", Color.parseColor("#FBBF24"))
            "speaking" -> Pair("SPEAKING", PINK)
            "idle" -> Pair("READY", Color.parseColor("#38BDF8"))
            else -> Pair("OFF", MUTED)
        }
        statePill.text = label
        statePill.setTextColor(color)
        statePill.background = shape(Color.parseColor("#0B1626"), 14, color, 1)
        moodTv.text = when (st) {
            "listening" -> "Sun rahi"
            "thinking" -> "Soch rahi"
            "speaking" -> "Bol rahi"
            "idle" -> "Taiyaar"
            else -> "Soyi hui"
        }
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hello = if (h < 12) "Subah bakhair" else if (h < 17) "Assalam o Alaikum" else "Shaam bakhair"
        greeting.text = "$hello, Rizwan"
        detailTv.text = if (Chat.detail.isNotEmpty()) Chat.detail
        else if (NoorService.isRunning) "Noor sun rahi hai. \"Hey Noor\" bolein ya likhein."
        else "Noor band hai. Neeche mic dabakar shuru karein."

        micBtn.text = if (NoorService.isRunning) "■" else "🎙"
        micBtn.background = shape(if (NoorService.isRunning) RED else CYAN, 32)

        val all = Chat.snapshot()
        if (all.size < rendered) {
            logBox.removeAllViews()
            rendered = 0
        }
        var added = false
        for (i in rendered until all.size) {
            addBubble(all[i].first, all[i].second)
            added = true
        }
        rendered = all.size
        if (added) homeScroll.post { homeScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addBubble(who: String, text: String) {
        val me = who == "me"
        val t = tv(text, 15f, if (me) Color.parseColor("#04121C") else TEXT)
        t.setPadding(dp(14), dp(10), dp(14), dp(10))
        t.background = shape(if (me) CYAN else CARD, 18, if (me) CYAN else LINE, 1)
        t.maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        val p = lp(WRAP, WRAP)
        p.gravity = if (me) Gravity.END else Gravity.START
        p.topMargin = dp(6)
        logBox.addView(t, p)
    }

    // ------------------------------------------------------------------ Vision (camera)
    private fun openVision() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (visionPage.childCount == 0) {
                val m = tv("Camera ki ijazat chahiye. Allow karein, phir ye tab dobara kholein.", 15f, TEXT)
                m.gravity = Gravity.CENTER
                m.setPadding(dp(24), dp(24), dp(24), dp(24))
                visionPage.addView(m, FrameLayout.LayoutParams(MATCH, MATCH))
            }
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 2)
            return
        }
        if (web != null) return
        visionPage.removeAllViews()
        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.webViewClient = WebViewClient()
        w.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    val wantsCam = request?.resources?.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) == true
                    val ok = wantsCam && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (ok) request?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else request?.deny()
                }
            }

            override fun onJsPrompt(
                view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?
            ): Boolean {
                result?.confirm(prefs().getString("token", "") ?: "")
                return true
            }
        }
        val base = (prefs().getString("url", DEFAULT_URL) ?: DEFAULT_URL).trim().trimEnd('/')
        w.loadUrl(base)
        visionPage.addView(w, FrameLayout.LayoutParams(MATCH, MATCH))
        web = w
    }

    // ------------------------------------------------------------------ Memory
    private fun buildMemory(): View {
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(24), dp(18), dp(18))
        val t = tv("Yaad rakha hua", 22f, CYAN, true)
        col.addView(t, lp(MATCH, WRAP))
        col.addView(tv("Noor ko bolo: \"ye yaad rakho ...\"", 12f, MUTED), lp(MATCH, WRAP))
        memTv = tv("", 16f, TEXT)
        memTv.setPadding(0, dp(14), 0, dp(10))
        col.addView(memTv, lp(MATCH, WRAP))
        col.addView(button("Sab mita do") {
            prefs().edit().putString("memories", "").apply()
            refreshMemory()
        })
        sv.addView(col)
        return sv
    }

    private fun refreshMemory() {
        val list = (prefs().getString("memories", "") ?: "").split("\n").filter { it.isNotBlank() }
        memTv.text = if (list.isEmpty()) "Abhi kuch yaad nahi hai." else list.joinToString("\n\n") { "• $it" }
    }

    // ------------------------------------------------------------------ Settings
    private fun buildSettings(): View {
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(24), dp(18), dp(24))

        col.addView(tv("Settings", 22f, CYAN, true), lp(MATCH, WRAP))
        statusTv = tv("", 14f, TEXT)
        statusTv.setPadding(0, dp(8), 0, dp(4))
        col.addView(statusTv, lp(MATCH, WRAP))

        col.addView(tv("Server (Vercel ka link)", 13f, MUTED), lp(MATCH, WRAP))
        urlEt = EditText(this)
        urlEt.setText(prefs().getString("url", DEFAULT_URL))
        urlEt.setTextColor(TEXT)
        urlEt.inputType = InputType.TYPE_TEXT_VARIATION_URI
        col.addView(urlEt, lp(MATCH, WRAP))

        col.addView(tv("Password (jo Vercel mein APP_TOKEN hai)", 13f, MUTED), lp(MATCH, WRAP))
        tokEt = EditText(this)
        tokEt.setText(prefs().getString("token", ""))
        tokEt.setTextColor(TEXT)
        tokEt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        col.addView(tokEt, lp(MATCH, WRAP))

        col.addView(button("1) Mic, camera, contacts aur notification allow karo") { askPermissions() })
        col.addView(button("2) Dusri app ke upar dikhne do (apps kholne ke liye)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(button("3) Battery: background mein chalne do") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                Toast.makeText(this, "Settings se Battery > Noor > No restrictions chunein", Toast.LENGTH_LONG).show()
            }
        })
        col.addView(button("4) App info (Autostart / Background allow karein)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        autoBtn = button("") {
            prefs().edit().putBoolean("autostart", !prefs().getBoolean("autostart", true)).apply()
            refreshSettings()
        }
        col.addView(autoBtn)
        col.addView(button("Save karo") {
            saveSettings()
            Toast.makeText(this, "Save ho gaya", Toast.LENGTH_SHORT).show()
        })
        sv.addView(col)
        return sv
    }

    private fun refreshSettings() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cam = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery = pm.isIgnoringBatteryOptimizations(packageName)
        fun m(ok: Boolean) = if (ok) "OK" else "baaki"
        statusTv.text = "Mic: ${m(mic)}   Camera: ${m(cam)}   Contacts: ${m(contacts)}" +
            "\nApps kholna: ${m(overlay)}   Background: ${m(battery)}"
        val auto = prefs().getBoolean("autostart", true)
        autoBtn.text = "Phone on hote hi Noor khud shuru: " + (if (auto) "ON" else "OFF")
    }

    private fun saveSettings() {
        prefs().edit()
            .putString("url", urlEt.text.toString().trim().ifEmpty { DEFAULT_URL })
            .putString("token", tokEt.text.toString().trim())
            .apply()
    }

    // ------------------------------------------------------------------ permissions / service
    private fun askPermissions() {
        val list = ArrayList<String>()
        list.add(Manifest.permission.RECORD_AUDIO)
        list.add(Manifest.permission.CAMERA)
        list.add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(list.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshSettings()
        if (requestCode == 2 && current == 1) openVision()
    }

    private fun toggleService() {
        saveSettings()
        if (NoorService.isRunning) {
            startService(Intent(this, NoorService::class.java).setAction(NoorService.ACTION_STOP))
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Pehle Settings mein button 1 dabakar mic allow karein", Toast.LENGTH_LONG).show()
                askPermissions()
                return
            }
            startForegroundService(Intent(this, NoorService::class.java))
        }
        ui.postDelayed({ refreshHome() }, 700)
    }
}
