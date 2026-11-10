package com.noor.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Chamakte dots se bana hologram chehra. Bolte waqt hont awaaz ke saath hilte hain. */
class HoloView(ctx: Context) : View(ctx) {
    var state = "off"
    private var rox = false
    private var built = false

    private var px = FloatArray(0)
    private var py = FloatArray(0)
    private var ph = FloatArray(0)
    private var bucket = IntArray(0)
    private var xs = arrayOf(FloatArray(0), FloatArray(0), FloatArray(0))
    private var counts = IntArray(3)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private var mouth = 0f

    fun setPersona(r: Boolean) {
        if (!built || r != rox) {
            rox = r
            build()
        }
    }

    private fun build() {
        val bmp = BitmapFactory.decodeResource(resources, if (rox) R.drawable.holo_rox else R.drawable.holo_noor)
        val n = 128
        val sc = Bitmap.createScaledBitmap(bmp, n, n, true)
        val rnd = Random(7)
        val lx = ArrayList<Float>()
        val ly = ArrayList<Float>()
        val lb = ArrayList<Int>()
        for (y in 0 until n) for (x in 0 until n) {
            val c = sc.getPixel(x, y)
            if (Color.alpha(c) < 120) continue
            val lum = (Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f) / 255f
            if (rnd.nextFloat() > 0.28f + 0.72f * lum) continue
            lx.add((x + rnd.nextFloat()) / n)
            ly.add((y + rnd.nextFloat()) / n)
            lb.add(if (lum > 0.72f) 2 else if (lum > 0.35f) 1 else 0)
        }
        px = lx.toFloatArray()
        py = ly.toFloatArray()
        bucket = lb.toIntArray()
        ph = FloatArray(px.size) { rnd.nextFloat() * 6.28f }
        xs = Array(3) { FloatArray(px.size * 2) }
        built = true
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (!built) build()
        t += 0.04f
        val s = min(width, height) * 0.86f * (1f + 0.008f * sin(t))
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f + height * 0.01f
        val col = when (state) {
            "speaking" -> Color.parseColor("#E879F9")
            "thinking" -> Color.parseColor("#FBBF24")
            "listening" -> Color.parseColor("#22D3EE")
            "idle" -> Color.parseColor("#38BDF8")
            else -> Color.parseColor("#475569")
        }
        val cx = width / 2f
        val cy = height / 2f

        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        for (i in 1..3) {
            p.color = col
            p.alpha = 90 / i
            c.drawCircle(cx, cy, s * (0.5f + 0.07f * i + 0.01f * sin(t * 2 + i)), p)
        }

        counts[0] = 0; counts[1] = 0; counts[2] = 0
        val amp = 1.1f
        for (i in px.indices) {
            val b = bucket[i]
            val k = counts[b]
            xs[b][k] = ox + px[i] * s + cos(t * 1.7f + ph[i]) * amp
            xs[b][k + 1] = oy + py[i] * s + sin(t * 1.3f + ph[i]) * amp
            counts[b] = k + 2
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val dim = if (rox) "#1E4FA8" else "#0E5A8A"
        val mid = if (rox) "#38BDF8" else "#22D3EE"
        val hi = if (rox) "#FDE68A" else "#E0FBFF"
        p.strokeWidth = 2.4f; p.color = Color.parseColor(dim); p.alpha = 190
        c.drawPoints(xs[0], 0, counts[0], p)
        p.strokeWidth = 2.6f; p.color = Color.parseColor(mid); p.alpha = 230
        c.drawPoints(xs[1], 0, counts[1], p)
        p.strokeWidth = 3f; p.color = Color.parseColor(hi); p.alpha = 255
        c.drawPoints(xs[2], 0, counts[2], p)

        // scan line
        val sy = oy + ((t * 0.35f) % 1f) * s
        p.strokeWidth = 2f
        p.color = col
        p.alpha = 70
        c.drawLine(ox, sy, ox + s, sy, p)

        // hont
        val target = if (state == "speaking") (abs(sin(t * 7.3f)) * 0.6f + abs(sin(t * 3.1f + 1.3f)) * 0.4f) else 0f
        mouth += (target - mouth) * 0.35f
        val mx = ox + s * 0.5f
        val my = oy + s * 0.654f
        val w = s * 0.095f
        val h = s * (0.008f + 0.062f * mouth)
        val r = RectF(mx - w, my - h, mx + w, my + h)
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#06111F")
        p.alpha = 235
        c.drawOval(r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3.4f
        p.color = Color.parseColor(if (rox) "#FDE68A" else "#FFB4D0")
        p.alpha = 255
        c.drawOval(r, p)

        postInvalidateOnAnimation()
    }
}
