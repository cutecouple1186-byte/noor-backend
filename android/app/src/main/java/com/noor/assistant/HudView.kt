package com.noor.assistant

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/** 3D neel hologram HUD: title, mausam/waqt, status, globe, gauges, smart control, news, hologram jagah, waveform. */
class HudView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val cy = Color.parseColor("#22D3EE")
    private val bl = Color.parseColor("#3B82F6")
    private val dim = Color.parseColor("#0E4A6B")
    private var t = 0f
    private var last = 0L

    var state = "off"
    var weather = "..."
    var ram = 0
    var batt = 0
    var disk = 0
    var news = ""
    var rox = true // true: Rox samne
    var torchOn = false
    var onAction: (String) -> Unit = {}
    var onRects: (RectF, RectF) -> Unit = { _, _ -> }
    private var pressed = ""
    private var pressAt = 0L
    private val stars = Array(40) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat()) }

    val mainRect = RectF()
    val sideRect = RectF()
    private val rGlobe = RectF()
    private val rNews = RectF()
    private val rKey = RectF()
    private val rHolo = RectF()
    private val hexes = listOf("wifi" to "WiFi", "bluetooth" to "BT", "torch" to "Torch", "airplane" to "Flight")
    private val hexC = Array(4) { PointF() }
    private var hexR = 0f
    private val steps = listOf("Analysis", "Processing", "Searching", "Connecting", "Ready")

    private fun d(v: Float) = v * resources.displayMetrics.density

    private val rLogo = RectF()
    private val rMic = RectF()
    private val rTitle = RectF()
    private val rWx = RectF()
    private val rChk = RectF()
    private val rBub = RectF()
    private val rProc = RectF()
    private val rMini = RectF()
    private val rGauge = RectF()
    private val rSmart = RectF()
    var bubble = ""
    var liveOn = false
    var logo: Bitmap? = null
    var quality = 1
    var focus = 0.8f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val lr = 0.095f * w
        rLogo.set(0.12f * w - lr, 0.065f * h - lr, 0.12f * w + lr, 0.065f * h + lr)
        val ss = lr * 1.7f
        sideRect.set(rLogo.centerX() - ss / 2, rLogo.centerY() - ss / 2, rLogo.centerX() + ss / 2, rLogo.centerY() + ss / 2)
        rTitle.set(0.27f * w, 0.012f * h, 0.70f * w, 0.105f * h)
        rWx.set(0.72f * w, 0.012f * h, 0.975f * w, 0.165f * h)
        rChk.set(0.015f * w, 0.125f * h, 0.26f * w, 0.31f * h)
        rBub.set(0.30f * w, 0.125f * h, 0.70f * w, 0.205f * h)
        val s = 0.46f * w
        val ccy = 0.35f * h
        mainRect.set(0.5f * w - s / 2, ccy - s / 2, 0.5f * w + s / 2, ccy + s / 2)
        rProc.set(0.74f * w, 0.235f * h, 0.975f * w, 0.40f * h)
        rMini.set(0.76f * w, 0.415f * h, 0.975f * w, 0.50f * h)
        rGlobe.set(0.01f * w, 0.38f * h, 0.26f * w, 0.38f * h + 0.25f * w)
        rGauge.set(0.02f * w, 0.545f * h, 0.53f * w, 0.665f * h)
        rSmart.set(0.55f * w, 0.545f * h, 0.98f * w, 0.665f * h)
        rNews.set(0.02f * w, 0.68f * h, 0.98f * w, 0.77f * h)
        rMic.set(0.5f * w - 0.09f * w, 0.865f * h - 0.09f * w, 0.5f * w + 0.09f * w, 0.865f * h + 0.09f * w)
        rKey.set(0.03f * w, 0.84f * h, 0.03f * w + 0.1f * w, 0.84f * h + 0.08f * w)
        rHolo.set(0.15f * w, 0.215f * h, 0.85f * w, 0.52f * h)
        hexR = 0.04f * w
        val x0 = rSmart.left + 0.06f * w + hexR * 0.6f
        for (i in 0..3) hexC[i].set(x0 + i * (hexR * 2.2f), rSmart.top + 0.058f * h)
        onRects(mainRect, sideRect)
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int, a: Paint.Align = Paint.Align.LEFT, alpha: Int = 255) {
        tp.textSize = d(size)
        tp.color = col
        tp.alpha = alpha
        tp.textAlign = a
        c.drawText(s, x, y, tp)
    }

    private fun panel(c: Canvas, r: RectF, title: String) {
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#0A2A4A")
        p.alpha = 90
        c.drawRoundRect(r, d(10f), d(10f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = d(1.2f)
        p.color = cy
        p.alpha = 150
        c.drawRoundRect(r, d(10f), d(10f), p)
        if (title.isNotEmpty()) txt(c, title, r.left + d(8f), r.top + d(13f), 8.5f, cy)
    }

    private fun hex(c: Canvas, x: Float, y: Float, r: Float) {
        val path = Path()
        for (i in 0..5) {
            val a = Math.toRadians((60 * i - 30).toDouble())
            val px = x + r * cos(a).toFloat()
            val py = y + r * sin(a).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, p)
    }

    private fun gauge(c: Canvas, cx: Float, cyy: Float, r: Float, pct: Int, label: String) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = d(4f)
        p.strokeCap = Paint.Cap.ROUND
        p.color = dim
        p.alpha = 255
        val o = RectF(cx - r, cyy - r, cx + r, cyy + r)
        c.drawArc(o, 135f, 270f, false, p)
        p.color = if (pct > 85) Color.parseColor("#F97316") else cy
        c.drawArc(o, 135f, 270f * pct.coerceIn(0, 100) / 100f, false, p)
        p.strokeCap = Paint.Cap.BUTT
        txt(c, "$pct%", cx, cyy + d(3f), 9f, Color.WHITE, Paint.Align.CENTER)
        txt(c, label, cx, cyy + r + d(11f), 7.5f, cy, Paint.Align.CENTER)
    }

    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        if (last != 0L) t += min(0.05f, (now - last) / 1000f)
        last = now
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = RadialGradient(w / 2, h * 0.3f, h * 0.8f, intArrayOf(Color.parseColor("#06305E"), Color.parseColor("#020B1A")), null, Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        // taare / dhool
        p.color = Color.parseColor("#7DD3FC")
        if (quality > 0) for ((si, s) in stars.withIndex()) {
            if (quality == 1 && si % 2 == 1) continue
            val y = (s[1] + t * 0.01f * (0.4f + s[2])) % 1f
            p.alpha = (60 + 120 * abs(sin(t + s[0] * 9f))).toInt()
            c.drawCircle(s[0] * w, y * h, d(0.8f + s[2]), p)
        }
        // header: logo ring (Noor/Rox side), title, mausam
        p.style = Paint.Style.STROKE
        p.strokeWidth = d(2.2f)
        p.color = cy
        p.alpha = 255
        c.drawCircle(rLogo.centerX(), rLogo.centerY(), rLogo.width() / 2f, p)
        p.color = bl
        c.save(); c.rotate(t * 70f, rLogo.centerX(), rLogo.centerY())
        c.drawArc(RectF(rLogo.left + d(4f), rLogo.top + d(4f), rLogo.right - d(4f), rLogo.bottom - d(4f)), 0f, 120f, false, p); c.restore()
        panel(c, rTitle, "")
        txt(c, "NOOR AI", rTitle.centerX(), rTitle.top + d(30f), 24f, Color.WHITE, Paint.Align.CENTER)
        txt(c, "Your Personal AI Assistant", rTitle.centerX(), rTitle.top + d(46f), 9.5f, Color.parseColor("#BAE6FD"), Paint.Align.CENTER)
        panel(c, rWx, "")
        p.style = Paint.Style.FILL; p.color = Color.parseColor("#FBBF24"); p.alpha = 255
        c.drawCircle(rWx.centerX() - d(8f), rWx.top + d(18f), d(8f), p)
        p.color = Color.WHITE; c.drawCircle(rWx.centerX() + d(5f), rWx.top + d(23f), d(9f), p)
        txt(c, weather, rWx.centerX(), rWx.top + d(46f), 10f, Color.WHITE, Paint.Align.CENTER)
        val tm = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date())
        val dt = SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH).format(Date())
        txt(c, tm, rWx.centerX(), rWx.bottom - d(24f), 19f, Color.WHITE, Paint.Align.CENTER)
        txt(c, dt, rWx.centerX(), rWx.bottom - d(8f), 8.5f, Color.parseColor("#BAE6FD"), Paint.Align.CENTER)
        // status checklist
        panel(c, rChk, "")
        val act = if (state == "thinking") ((t * 2.5f).toInt() % 5) else 5
        for (i in steps.indices) {
            val y = rChk.top + d(22f) + i * (rChk.height() - d(26f)) / 5f
            val on = state != "off" && i <= act
            p.style = Paint.Style.STROKE
            p.strokeWidth = d(1.6f)
            p.color = if (on) Color.parseColor("#2DD4BF") else dim
            p.alpha = 255
            c.drawCircle(rChk.left + d(14f), y - d(3f), d(7f), p)
            if (on) {
                val ck = Path(); ck.moveTo(rChk.left + d(10.5f), y - d(3f)); ck.lineTo(rChk.left + d(13f), y); ck.lineTo(rChk.left + d(18f), y - d(7f))
                c.drawPath(ck, p)
            }
            txt(c, steps[i] + if (i < 4) "..." else "!", rChk.left + d(27f), y, 9.5f, if (on) Color.WHITE else Color.parseColor("#5B7C99"))
        }
        // bubble
        panel(c, rBub, "")
        val bt = bubble.ifBlank { "Hello Boss,\nHow can I help you today?" }
        tp.textSize = d(10.5f)
        var ly = rBub.top + d(20f)
        var rest = bt.replace("\n", " ")
        for (ln in 0..1) {
            if (rest.isBlank()) break
            val n = tp.breakText(rest, true, rBub.width() - d(16f), null)
            var cut = n
            if (n < rest.length) { val sp = rest.lastIndexOf(' ', n); if (sp > 0) cut = sp }
            var seg = rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
            if (ln == 1 && rest.isNotEmpty()) seg = seg.dropLast(1) + "…"
            txt(c, seg, rBub.left + d(10f), ly, 10.5f, Color.WHITE)
            ly += d(17f)
        }
        // character aadhaar
        val mc = PointF(mainRect.centerX(), mainRect.bottom + d(4f))
        for (i in 0..2) {
            val k = 1f + i * 0.28f
            p.style = Paint.Style.STROKE
            p.strokeWidth = d(1.6f)
            p.color = if (i % 2 == 0) cy else bl
            p.alpha = (230 - i * 60)
            c.drawOval(RectF(mc.x - mainRect.width() * 0.55f * k, mc.y - d(7f) * k, mc.x + mainRect.width() * 0.55f * k, mc.y + d(7f) * k), p)
        }
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, mainRect.top, 0f, mc.y, Color.TRANSPARENT, Color.parseColor("#3322D3EE"), Shader.TileMode.CLAMP)
        val beam = Path(); beam.moveTo(mc.x - mainRect.width() * 0.5f, mc.y); beam.lineTo(mc.x - mainRect.width() * 0.32f, mainRect.top + mainRect.height() * 0.6f)
        beam.lineTo(mc.x + mainRect.width() * 0.32f, mainRect.top + mainRect.height() * 0.6f); beam.lineTo(mc.x + mainRect.width() * 0.5f, mc.y); beam.close()
        c.drawPath(beam, p)
        p.shader = null
        // hologram (jo bhi Noor banaye) beech mein
        drawHoloArea(c)
        // right: Processing + mini hologram card
        panel(c, rProc, "")
        p.style = Paint.Style.FILL; p.color = cy
        for (i in 0..5) { p.alpha = 90; c.drawRect(rProc.left + d(6f), rProc.top + d(8f) + i * d(7f), rProc.left + d(6f) + d(30f + ((i * 37 + (t * 3).toInt()) % 30)), rProc.top + d(11f) + i * d(7f), p) }
        for (i in 0..2) {
            val yy = rProc.top + d(62f) + i * d(22f)
            p.style = Paint.Style.STROKE; p.strokeWidth = d(1.6f); p.color = cy; p.alpha = 255
            c.save(); c.rotate(t * 220f + i * 40f, rProc.left + d(12f), yy - d(3f))
            c.drawArc(RectF(rProc.left + d(5f), yy - d(10f), rProc.left + d(19f), yy + d(4f)), 0f, 250f, false, p); c.restore()
            txt(c, "Processing...", rProc.left + d(26f), yy, 9f, Color.WHITE)
        }
        panel(c, rMini, "")
        p.style = Paint.Style.STROKE; p.strokeWidth = d(1.4f); p.color = cy; p.alpha = 255
        c.drawCircle(rMini.centerX(), rMini.centerY() - d(2f), rMini.height() * 0.33f, p)
        c.drawLine(rMini.centerX(), rMini.centerY() - d(14f), rMini.centerX(), rMini.centerY() + d(10f), p)
        c.drawOval(RectF(rMini.centerX() - d(16f), rMini.bottom - d(14f), rMini.centerX() + d(16f), rMini.bottom - d(6f)), p)
        // globe
        drawGlobe(c, rGlobe)
        // gauges
        panel(c, rGauge, "System Status")
        val gr = 0.04f * w
        gauge(c, rGauge.left + rGauge.width() * 0.17f, rGauge.centerY() + d(5f), gr, ram, "RAM")
        gauge(c, rGauge.left + rGauge.width() * 0.5f, rGauge.centerY() + d(5f), gr, disk, "Storage")
        gauge(c, rGauge.left + rGauge.width() * 0.83f, rGauge.centerY() + d(5f), gr, batt, "Battery")
        // smart control
        panel(c, rSmart, "Smart Control")
        val names = listOf("WiFi", "Bluetooth", "Flashlight", "Airplane")
        for (i in 0..3) {
            val key = hexes[i].first
            val hot = (pressed == key && now - pressAt < 400) || (key == "torch" && torchOn)
            p.style = Paint.Style.FILL
            p.color = if (hot) cy else Color.parseColor("#0B3A5C")
            p.alpha = if (hot) 200 else 255
            hex(c, hexC[i].x, hexC[i].y, hexR)
            p.style = Paint.Style.STROKE
            p.strokeWidth = d(1.5f)
            p.color = cy
            hex(c, hexC[i].x, hexC[i].y, hexR)
            txt(c, names[i], hexC[i].x, hexC[i].y + hexR + d(12f), 6.5f, Color.WHITE, Paint.Align.CENTER)
            drawIcon(c, key, hexC[i].x, hexC[i].y, hexR * 0.5f, if (hot) Color.parseColor("#04121C") else cy)
        }
        // news
        panel(c, rNews, "News Feed")
        var line = news.ifBlank { "Khabrein load ho rahi hain..." }
        tp.textSize = d(10f)
        val maxW = rNews.width() - d(16f)
        if (tp.measureText(line) > maxW) {
            val n = tp.breakText(line, true, maxW - d(12f), null)
            line = line.take(n) + "…"
        }
        txt(c, line, rNews.left + d(8f), rNews.centerY() + d(6f), 10f, Color.WHITE)
        txt(c, "Read More >", rNews.left + d(8f), rNews.bottom - d(8f), 9f, cy)
        // waveform + mic (LIVE)
        drawWave(c, 0.17f * w, 0.865f * h, 0.27f * w)
        drawWave(c, 0.56f * w, 0.865f * h, 0.40f * w)
        drawMic(c)
        p.style = Paint.Style.STROKE
        p.strokeWidth = d(1.4f)
        p.color = cy
        p.alpha = 255
        c.drawRoundRect(rKey, d(6f), d(6f), p)
        for (r in 0..1) for (k in 0..3) c.drawCircle(rKey.left + rKey.width() * (0.2f + 0.2f * k), rKey.top + rKey.height() * (0.35f + 0.3f * r), d(0.9f), p)
        // logo (Noor/Rox) niche se banaya HoloView hai; yahan sirf label
        postInvalidateOnAnimation()
    }

    private fun drawMic(c: Canvas) {
        val cx = rMic.centerX(); val cyy = rMic.centerY(); val r = rMic.width() / 2f
        val pulse = if (liveOn) 1f + 0.08f * sin(t * 6f) else 1f
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cyy, r * 1.6f * pulse, Color.parseColor("#5522D3EE"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cyy, r * 1.6f * pulse, p)
        p.shader = null
        p.color = Color.parseColor("#031A33"); p.alpha = 255
        c.drawCircle(cx, cyy, r, p)
        p.style = Paint.Style.STROKE
        for ((wd, al) in listOf(Pair(6f, 90), Pair(2.5f, 255))) { p.strokeWidth = d(wd); p.color = if (liveOn) Color.parseColor("#34D399") else cy; p.alpha = al; c.drawCircle(cx, cyy, r * pulse, p) }
        p.strokeWidth = d(2f); p.color = Color.WHITE; p.alpha = 255
        c.drawRoundRect(RectF(cx - r * 0.2f, cyy - r * 0.5f, cx + r * 0.2f, cyy + r * 0.15f), r * 0.2f, r * 0.2f, p)
        c.drawArc(RectF(cx - r * 0.38f, cyy - r * 0.3f, cx + r * 0.38f, cyy + r * 0.38f), 0f, 180f, false, p)
        c.drawLine(cx, cyy + r * 0.38f, cx, cyy + r * 0.58f, p)
        c.drawLine(cx - r * 0.2f, cyy + r * 0.58f, cx + r * 0.2f, cyy + r * 0.58f, p)
    }

    private fun drawIcon(c: Canvas, key: String, x: Float, y: Float, r: Float, col: Int) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = d(1.8f)
        p.color = col
        p.alpha = 255
        when (key) {
            "wifi" -> for (i in 1..3) c.drawArc(RectF(x - r * i / 3f, y - r * i / 3f + r * 0.4f, x + r * i / 3f, y + r * i / 3f + r * 0.4f), 225f, 90f, false, p)
            "bluetooth" -> { val b = Path(); b.moveTo(x - r * 0.6f, y - r * 0.5f); b.lineTo(x + r * 0.6f, y + r * 0.5f); b.lineTo(x, y + r); b.lineTo(x, y - r); b.lineTo(x + r * 0.6f, y - r * 0.5f); b.lineTo(x - r * 0.6f, y + r * 0.5f); c.drawPath(b, p) }
            "torch" -> { c.drawRect(x - r * 0.4f, y - r * 0.9f, x + r * 0.4f, y + r * 0.1f, p); c.drawLine(x, y + r * 0.1f, x, y + r * 0.9f, p) }
            else -> { c.drawLine(x - r, y + r * 0.2f, x + r, y - r * 0.2f, p); c.drawLine(x - r * 0.2f, y, x - r * 0.7f, y - r * 0.7f, p); c.drawLine(x - r * 0.2f, y, x - r * 0.7f, y + r * 0.8f, p) }
        }
    }

    private fun drawGlobe(c: Canvas, r: RectF) {
        val cx = r.centerX(); val cyy = r.centerY(); val rad = r.width() / 2f
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cyy, rad, Color.parseColor("#2A22D3EE"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cyy, rad * 1.1f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = d(1.2f)
        p.color = cy
        p.alpha = 255
        c.drawCircle(cx, cyy, rad, p)
        p.alpha = 150
        for (k in 0..3) {
            val sx = abs(cos(t * 0.8f + k * PI.toFloat() / 4f)) * rad
            c.drawOval(RectF(cx - sx, cyy - rad, cx + sx, cyy + rad), p)
        }
        for (k in -2..2) {
            val yy = cyy + k * rad * 0.33f
            val half = sqrt(max(0f, rad * rad - (k * rad * 0.33f).pow(2)))
            c.drawLine(cx - half, yy, cx + half, yy, p)
        }
        p.alpha = 255
    }

    private fun drawHoloArea(c: Canvas) {
        val r = rHolo
        val b = Hud.holo
        val cx = r.centerX()
        if (Hud.busy) {
            c.save(); c.rotate(t * 200f, cx, r.centerY())
            p.style = Paint.Style.STROKE
            p.color = cy; p.alpha = 255; p.strokeWidth = d(3f)
            c.drawArc(RectF(cx - d(24f), r.centerY() - d(24f), cx + d(24f), r.centerY() + d(24f)), 0f, 250f, false, p)
            c.restore()
        }
        if (b != null) {
            val side = min(r.width(), r.height() * 1.05f)
            val sway = 0.9f + 0.1f * cos(t * 1.4f)
            val dst = RectF(cx - side / 2f * sway, r.bottom - side, cx + side / 2f * sway, r.bottom)
            p.style = Paint.Style.FILL
            p.alpha = ((205 + 25 * sin(t * 9f)) * focus.coerceIn(0.4f, 1f)).toInt()
            c.drawBitmap(b, null, dst, p)
            p.color = Color.parseColor("#22D3EE")
            var y = dst.top + (t * 25f) % d(6f)
            p.alpha = 22
            while (y < dst.bottom) { c.drawRect(dst.left, y, dst.right, y + d(1f), p); y += d(6f) }
            p.alpha = 255
        }
    }

    private fun drawWave(c: Canvas, x0: Float, y: Float, wd: Float) {
        val amp = when (state) { "speaking" -> 1f; "listening" -> 0.5f; "thinking" -> 0.3f; else -> 0.12f }
        p.style = Paint.Style.STROKE
        p.strokeWidth = d(1.8f)
        p.color = cy
        p.alpha = 255
        val path = Path()
        var i = 0
        while (i <= 80) {
            val f = i / 80f
            val env = sin(f * PI.toFloat())
            val yy = y + sin(f * 28f + t * 8f) * d(11f) * amp * env * (0.6f + 0.4f * sin(t * 3f + f * 6f))
            if (i == 0) path.moveTo(x0, yy) else path.lineTo(x0 + wd * f, yy)
            i++
        }
        c.drawPath(path, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP) return e.action == MotionEvent.ACTION_DOWN
        val x = e.x; val y = e.y
        for (i in 0..3) if (hypot(x - hexC[i].x, y - hexC[i].y) < hexR * 1.2f) {
            pressed = hexes[i].first; pressAt = System.currentTimeMillis(); onAction(pressed); return true
        }
        if (rKey.contains(x, y)) { onAction("keyboard"); return true }
        if (rNews.contains(x, y)) { onAction("news"); return true }
        if (sideRect.contains(x, y) || rLogo.contains(x, y)) { onAction("swap"); return true }
        if (mainRect.contains(x, y)) { onAction("main"); return true }
        if (rMic.contains(x, y)) { onAction("live"); return true }
        if (rHolo.contains(x, y) && Hud.holo != null) { onAction("holo_clear"); return true }
        return true
    }
}
