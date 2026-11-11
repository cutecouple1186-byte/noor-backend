package com.noor.assistant

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/** Home: Rizwan ki apni banayi hui tasveer background hai; us par asli waqt/mausam/battery/news aur ON/OFF likha jata hai. */
class HudView(ctx: Context) : View(ctx) {
    private val bm: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.home_bg)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val cy = Color.parseColor("#22D3EE")
    private val dark = Color.parseColor("#030D26")
    private val cl = 0.06f
    private val cr = 0.94f
    private var t = 0f
    private var last = 0L

    var state = "off"
    var weather = "..."
    var ram = 0
    var batt = 0
    var disk = 0
    var news = ""
    var rox = true
    var torchOn = false
    var quality = 1
    var focus = 1f
    var bubble = ""
    var liveOn = false // ab matlab: Noor ON hai
    var onAction: (String) -> Unit = {}
    var onRects: (RectF, RectF) -> Unit = { _, _ -> }
    val mainRect = RectF()
    val sideRect = RectF()
    private var pressed = ""
    private var pressAt = 0L
    private val hexX = floatArrayOf(0.636f, 0.739f, 0.842f, 0.937f)
    private val hexKey = listOf("wifi", "bluetooth", "torch", "airplane")

    private fun X(n: Float) = (n - cl) / (cr - cl) * width
    private fun Y(n: Float) = n * height
    private fun d(v: Float) = v * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val ms = X(0.5f + 0.25f * (cr - cl)) - X(0.5f - 0.25f * (cr - cl))
        mainRect.set(X(0.5f) - ms / 2, Y(0.40f) - ms / 2, X(0.5f) + ms / 2, Y(0.40f) + ms / 2)
        val ss = (X(0.134f + 0.062f) - X(0.134f - 0.062f))
        sideRect.set(X(0.134f) - ss / 2, Y(0.075f) - ss / 2, X(0.134f) + ss / 2, Y(0.075f) + ss / 2)
        onRects(mainRect, sideRect)
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int, a: Paint.Align = Paint.Align.LEFT) {
        tp.textSize = d(size); tp.color = col; tp.textAlign = a; tp.alpha = 255
        c.drawText(s, x, y, tp)
    }

    private fun cover(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
        p.style = Paint.Style.FILL; p.shader = null; p.color = dark; p.alpha = 245
        c.drawRoundRect(RectF(X(x0), Y(y0), X(x1), Y(y1)), d(6f), d(6f), p)
        p.alpha = 255
    }

    private fun wrap(s: String, w: Float, lines: Int): List<String> {
        val out = ArrayList<String>()
        var rest = s.trim()
        for (i in 0 until lines) {
            if (rest.isEmpty()) break
            val n = tp.breakText(rest, true, w, null)
            var cut = n
            if (n < rest.length) { val sp = rest.lastIndexOf(' ', n); if (sp > 0) cut = sp }
            var seg = rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
            if (i == lines - 1 && rest.isNotEmpty()) seg = seg.dropLast(1).trimEnd() + "…"
            out.add(seg)
        }
        return out
    }

    private fun gauge(c: Canvas, cx: Float, cyy: Float, r: Float, pct: Int, label: String) {
        p.style = Paint.Style.STROKE; p.strokeWidth = d(4f); p.strokeCap = Paint.Cap.ROUND
        p.color = Color.parseColor("#0E4A6B"); p.alpha = 255
        val o = RectF(cx - r, cyy - r, cx + r, cyy + r)
        c.drawArc(o, 135f, 270f, false, p)
        p.color = if (pct > 85) Color.parseColor("#F97316") else cy
        c.drawArc(o, 135f, 270f * pct.coerceIn(0, 100) / 100f, false, p)
        p.strokeCap = Paint.Cap.BUTT
        txt(c, "$pct%", cx, cyy + d(4f), 10f, Color.WHITE, Paint.Align.CENTER)
        txt(c, label, cx, cyy + r + d(14f), 9f, Color.WHITE, Paint.Align.CENTER)
    }

    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        if (last != 0L) t += min(0.05f, (now - last) / 1000f)
        last = now
        val w = width.toFloat(); val h = height.toFloat()
        p.style = Paint.Style.FILL; p.alpha = 255; p.shader = null
        c.drawBitmap(bm, Rect((cl * bm.width).toInt(), 0, (cr * bm.width).toInt(), bm.height), RectF(0f, 0f, w, h), p)

        // character badla: Noor samne ho ya hologram ho to cartoon ladke par parda
        val hologram = Hud.holo
        if (!rox || hologram != null) {
            val cx = X(0.49f); val cyy = Y(0.52f)
            p.shader = RadialGradient(cx, cyy, Y(0.30f), intArrayOf(Color.parseColor("#02081C"), Color.parseColor("#02081C"), Color.TRANSPARENT), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
            c.save(); c.scale(0.62f, 1f, cx, cyy)
            c.drawCircle(cx, cyy, Y(0.30f), p)
            c.restore()
            p.shader = null
        }
        if (hologram != null) {
            val side = X(0.5f + 0.36f * (cr - cl)) - X(0.5f - 0.36f * (cr - cl))
            val sway = 0.92f + 0.08f * cos(t * 1.4f)
            val bw = side * sway
            val dst = RectF(X(0.49f) - bw / 2f, Y(0.52f) - side * 0.55f, X(0.49f) + bw / 2f, Y(0.52f) + side * 0.55f)
            p.alpha = (235 + 20 * sin(t * 9f)).toInt()
            c.drawBitmap(hologram, null, dst, p)
            p.alpha = 255
        }
        if (Hud.busy) {
            c.save(); c.rotate(t * 200f, X(0.49f), Y(0.45f))
            p.style = Paint.Style.STROKE; p.color = cy; p.strokeWidth = d(3f)
            c.drawArc(RectF(X(0.49f) - d(24f), Y(0.45f) - d(24f), X(0.49f) + d(24f), Y(0.45f) + d(24f)), 0f, 250f, false, p)
            c.restore()
        }

        // mausam aur waqt
        cover(c, 0.744f, 0.064f, 0.963f, 0.163f)
        val cx0 = X(0.853f)
        txt(c, weather, cx0, Y(0.088f), 10.5f, Color.WHITE, Paint.Align.CENTER)
        txt(c, SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date()), cx0, Y(0.131f), 21f, Color.WHITE, Paint.Align.CENTER)
        txt(c, SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH).format(Date()), cx0, Y(0.152f), 9.5f, Color.parseColor("#BAE6FD"), Paint.Align.CENTER)

        // bubble
        cover(c, 0.345f, 0.14f, 0.70f, 0.188f)
        val bt = bubble.ifBlank { "Hello Boss, How can I help you today?" }
        tp.textSize = d(11f)
        val lines = wrap(bt.replace("\n", " "), X(0.70f) - X(0.355f) - d(6f), 2)
        for ((i, ln) in lines.withIndex()) txt(c, ln, X(0.36f), Y(0.157f) + i * d(17f), 11f, Color.WHITE)

        // system status
        cover(c, 0.045f, 0.642f, 0.53f, 0.725f)
        val gr = d(19f)
        gauge(c, X(0.115f), Y(0.680f), gr, ram, "RAM")
        gauge(c, X(0.265f), Y(0.680f), gr, disk, "Storage")
        gauge(c, X(0.415f), Y(0.680f), gr, batt, "Battery")
        // battery bar
        val bx = X(0.47f); val by = Y(0.655f)
        p.style = Paint.Style.STROKE; p.strokeWidth = d(1.6f); p.color = cy
        c.drawRoundRect(RectF(bx, by, bx + d(20f), by + d(40f)), d(3f), d(3f), p)
        p.style = Paint.Style.FILL; p.alpha = 220
        val fh = d(36f) * batt.coerceIn(0, 100) / 100f
        c.drawRect(bx + d(3f), by + d(38f) - fh, bx + d(17f), by + d(38f), p)
        p.alpha = 255

        // smart control (torch halat)
        for (i in 0..3) {
            val hot = (pressed == hexKey[i] && now - pressAt < 400) || (hexKey[i] == "torch" && torchOn)
            if (hot) {
                p.style = Paint.Style.FILL; p.color = cy; p.alpha = 90
                c.drawCircle(X(hexX[i]), Y(0.658f), d(25f), p); p.alpha = 255
            }
        }

        // news
        cover(c, 0.278f, 0.765f, 0.50f, 0.838f)
        tp.textSize = d(9.5f)
        val nl = wrap(news.ifBlank { "Khabrein load ho rahi hain..." }, X(0.50f) - X(0.285f) - d(4f), 3)
        for ((i, ln) in nl.withIndex()) txt(c, ln, X(0.285f), Y(0.783f) + i * d(14f), 9.5f, Color.WHITE)
        txt(c, "Read More →", X(0.285f), Y(0.83f), 9.5f, cy)

        // mic = ON/OFF
        val mx = X(0.5f); val my = Y(0.882f)
        val mr = (X(0.5f + 0.078f * (cr - cl)) - X(0.5f)).coerceAtLeast(d(30f))
        p.style = Paint.Style.FILL; p.color = Color.parseColor("#020B22"); p.alpha = 255
        c.drawCircle(mx, my, mr, p)
        val onC = if (liveOn) Color.parseColor("#34D399") else Color.parseColor("#EF4444")
        if (liveOn) {
            p.style = Paint.Style.STROKE; p.color = onC; p.strokeWidth = d(3f); p.alpha = (140 + 100 * sin(t * 5f)).toInt()
            c.drawCircle(mx, my, mr + d(4f), p); p.alpha = 255
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = d(2f); p.color = Color.WHITE
        val ir = mr * 0.5f
        c.drawRoundRect(RectF(mx - ir * 0.22f, my - ir * 1.2f, mx + ir * 0.22f, my - ir * 0.2f), ir * 0.22f, ir * 0.22f, p)
        c.drawArc(RectF(mx - ir * 0.45f, my - ir * 0.9f, mx + ir * 0.45f, my + ir * 0.35f), 0f, 180f, false, p)
        c.drawLine(mx, my + ir * 0.35f, mx, my + ir * 0.55f, p)
        txt(c, if (liveOn) "ON" else "OFF", mx, my + mr * 0.82f, 13f, onC, Paint.Align.CENTER)

        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP) return true
        val nx = cl + e.x / width * (cr - cl)
        val ny = e.y / height
        fun near(a: Float, b: Float, r: Float) = abs(nx - a) < r && abs(ny - b) < r * 0.55f
        if (hypot((nx - 0.5f) * 1f, (ny - 0.882f) * 0.56f) < 0.1f) { onAction("live"); return true }
        for (i in 0..3) if (near(hexX[i], 0.658f, 0.05f)) { pressed = hexKey[i]; pressAt = System.currentTimeMillis(); onAction(pressed); return true }
        if (ny > 0.925f) {
            val i = when { nx < 0.24f -> 0; nx < 0.50f -> 1; nx < 0.76f -> 2; else -> 3 }
            onAction("nav$i"); return true
        }
        if (nx in 0.28f..0.5f && ny in 0.76f..0.84f) { onAction("news"); return true }
        if (nx in 0.04f..0.23f && ny in 0.015f..0.135f) { onAction("swap"); return true }
        if (Hud.holo != null && nx in 0.25f..0.72f && ny in 0.22f..0.8f) { onAction("holo_clear"); return true }
        if (nx in 0.3f..0.7f && ny in 0.22f..0.62f) { onAction("main"); return true }
        return true
    }
}
