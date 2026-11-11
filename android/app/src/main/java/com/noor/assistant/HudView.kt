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

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val s = min(0.42f * w, 0.33f * h)
        val cx = 0.5f * w
        val ccy = 0.085f * h + 0.185f * h
        mainRect.set(cx - s / 2, ccy - s / 2, cx + s / 2, ccy + s / 2)
        val sw = 0.27f * w
        sideRect.set(0.70f * w, 0.1f * h, 0.70f * w + sw, 0.1f * h + sw)
        rGlobe.set(0.71f * w, sideRect.bottom + d(8f), 0.96f * w, sideRect.bottom + d(8f) + 0.25f * w)
        rNews.set(0.03f * w, 0.625f * h, 0.97f * w, 0.70f * h)
        rHolo.set(0.05f * w, 0.715f * h, 0.95f * w, 0.915f * h)
        rKey.set(0.86f * w, 0.925f * h, 0.97f * w, 0.925f * h + 0.11f * w)
        hexR = 0.044f * w
        val x0 = 0.545f * w + hexR
        for (i in 0..3) hexC[i].set(x0 + i * (hexR * 2.15f), 0.535f * h)
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
        for (s in stars) {
            val y = (s[1] + t * 0.01f * (0.4f + s[2])) % 1f
            p.alpha = (60 + 120 * abs(sin(t + s[0] * 9f))).toInt()
            c.drawCircle(s[0] * w, y * h, d(0.8f + s[2]), p)
        }
        // header
        val lc = PointF(0.09f * w, 0.045f * h)
        p.style = Paint.Style.STROKE
        p.strokeWidth = d(2f)
        p.color = cy
        p.alpha = 255
        c.drawCircle(lc.x, lc.y, d(15f), p)
        p.color = bl
        c.save(); c.rotate(t * 90f, lc.x, lc.y); c.drawArc(RectF(lc.x - d(11f), lc.y - d(11f), lc.x + d(11f), lc.y + d(11f)), 0f, 100f, false, p); c.restore()
        p.style = Paint.Style.FILL
        p.color = cy
        c.drawCircle(lc.x, lc.y, d(4f), p)
        txt(c, "NOOR AI", 0.17f * w, 0.058f * h, 19f, Color.WHITE)
        val tm = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date())
        val dt = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(Date())
        val wr = RectF(0.66f * w, 0.012f * h, 0.97f * w, 0.082f * h)
        panel(c, wr, "")
        txt(c, tm, wr.left + d(8f), wr.top + d(19f), 13f, Color.WHITE)
        txt(c, dt, wr.left + d(8f), wr.top + d(31f), 8f, cy)
        txt(c, weather, wr.right - d(8f), wr.top + d(19f), 10f, Color.WHITE, Paint.Align.RIGHT)
        // status checklist
        val sx = 0.04f * w
        val act = when (state) { "listening" -> 4; "thinking" -> ((t * 2.5f).toInt() % 3); "speaking" -> 3; "idle" -> 4; else -> -1 }
        for (i in steps.indices) {
            val y = 0.14f * h + i * 0.045f * h
            val on = if (state == "off") false else if (state == "thinking") i <= act else i <= 4
            p.style = Paint.Style.STROKE
            p.strokeWidth = d(1.4f)
            p.color = if (on) cy else dim
            p.alpha = 255
            c.drawCircle(sx + d(6f), y - d(3f), d(6f), p)
            if (on) {
                val ck = Path(); ck.moveTo(sx + d(3f), y - d(3f)); ck.lineTo(sx + d(5.5f), y); ck.lineTo(sx + d(9.5f), y - d(6f))
                c.drawPath(ck, p)
            }
            txt(c, steps[i], sx + d(18f), y, 9.5f, if (on) Color.WHITE else Color.parseColor("#5B7C99"))
        }
        // hologram aadhaar (main)
        val mc = PointF(mainRect.centerX(), mainRect.bottom + d(6f))
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
        // side card frame
        panel(c, RectF(sideRect.left - d(3f), sideRect.top - d(3f), sideRect.right + d(3f), sideRect.bottom + d(15f)), "")
        txt(c, if (rox) "NOOR" else "ROX", sideRect.centerX(), sideRect.bottom + d(11f), 8.5f, cy, Paint.Align.CENTER)
        // globe
        drawGlobe(c, rGlobe)
        // gauges
        val gp = RectF(0.03f * w, 0.455f * h, 0.52f * w, 0.60f * h)
        panel(c, gp, "SYSTEM STATUS")
        val gr = 0.045f * w
        gauge(c, gp.left + gp.width() * 0.17f, gp.centerY() + d(2f), gr, ram, "RAM")
        gauge(c, gp.left + gp.width() * 0.5f, gp.centerY() + d(2f), gr, batt, "BATTERY")
        gauge(c, gp.left + gp.width() * 0.83f, gp.centerY() + d(2f), gr, disk, "STORAGE")
        // smart control
        val sp = RectF(0.535f * w, 0.455f * h, 0.97f * w, 0.60f * h)
        panel(c, sp, "SMART CONTROL")
        for (i in 0..3) {
            val key = hexes[i].first
            val hot = pressed == key && now - pressAt < 400 || (key == "torch" && torchOn)
            p.style = Paint.Style.FILL
            p.color = if (hot) cy else Color.parseColor("#0B3A5C")
            p.alpha = if (hot) 200 else 255
            hex(c, hexC[i].x, hexC[i].y, hexR)
            p.style = Paint.Style.STROKE
            p.strokeWidth = d(1.5f)
            p.color = cy
            hex(c, hexC[i].x, hexC[i].y, hexR)
            txt(c, hexes[i].second, hexC[i].x, hexC[i].y + hexR + d(12f), 7.5f, Color.WHITE, Paint.Align.CENTER)
            drawIcon(c, key, hexC[i].x, hexC[i].y, hexR * 0.5f, if (hot) Color.parseColor("#04121C") else cy)
        }
        // news
        panel(c, rNews, "NEWS FEED")
        var line = news.ifBlank { "Khabrein load ho rahi hain..." }
        tp.textSize = d(10f)
        val maxW = rNews.width() - d(16f)
        if (tp.measureText(line) > maxW) {
            val n = tp.breakText(line, true, maxW - d(12f), null)
            line = line.take(n) + "…"
        }
        txt(c, line, rNews.left + d(8f), rNews.bottom - d(9f), 10f, Color.WHITE)
        // hologram jagah
        drawHoloArea(c)
        // waveform + keyboard
        drawWave(c, 0.06f * w, 0.945f * h, 0.78f * w)
        p.style = Paint.Style.STROKE
        p.strokeWidth = d(1.4f)
        p.color = cy
        p.alpha = 255
        c.drawRoundRect(rKey, d(6f), d(6f), p)
        for (r in 0..1) for (k in 0..3) c.drawCircle(rKey.left + rKey.width() * (0.2f + 0.2f * k), rKey.top + rKey.height() * (0.35f + 0.3f * r), d(0.9f), p)
        postInvalidateOnAnimation()
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
        // dais
        p.style = Paint.Style.STROKE
        p.strokeWidth = d(1.6f)
        for (i in 0..2) {
            p.color = if (i == 1) bl else cy
            p.alpha = 220 - i * 60
            val k = 1f + i * 0.25f
            c.drawOval(RectF(cx - r.width() * 0.3f * k, r.bottom - d(10f) * k, cx + r.width() * 0.3f * k, r.bottom + d(10f) * k), p)
        }
        if (Hud.busy) {
            c.save(); c.rotate(t * 200f, cx, r.centerY())
            p.color = cy; p.alpha = 255; p.strokeWidth = d(3f)
            c.drawArc(RectF(cx - d(24f), r.centerY() - d(24f), cx + d(24f), r.centerY() + d(24f)), 0f, 250f, false, p)
            c.restore()
        }
        if (b != null) {
            val side = min(r.width(), r.height()) * 0.95f
            val sway = 0.86f + 0.14f * cos(t * 1.4f)
            val dst = RectF(cx - side / 2f * sway, r.bottom - side - d(4f), cx + side / 2f * sway, r.bottom - d(4f))
            p.style = Paint.Style.FILL
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            p.alpha = (225 + 25 * sin(t * 9f)).toInt()
            c.drawBitmap(b, null, dst, p)
            p.xfermode = null
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
        if (sideRect.contains(x, y)) { onAction("swap"); return true }
        if (mainRect.contains(x, y)) { onAction("main"); return true }
        if (rHolo.contains(x, y) && Hud.holo != null) { onAction("holo_clear"); return true }
        return true
    }
}
