package com.noor.assistant

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** Rizwan ki tasveer ka ek hissa poori screen par; us par chhupe hue touch-zone aur zaroori overlay. */
class PicPage(ctx: Context, resId: Int, private val cl: Float, private val ct: Float, private val cr: Float, private val cb: Float) : View(ctx) {
    private val bm: Bitmap = BitmapFactory.decodeResource(resources, resId)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val zones = ArrayList<Pair<RectF, String>>()
    var onAction: (String) -> Unit = {}
    var overlay: (PicPage, Canvas) -> Unit = { _, _ -> }

    fun X(n: Float) = (n - cl) / (cr - cl) * width
    fun Y(n: Float) = (n - ct) / (cb - ct) * height
    fun d(v: Float) = v * resources.displayMetrics.density
    fun zone(x0: Float, y0: Float, x1: Float, y1: Float, name: String) { zones.add(Pair(RectF(x0, y0, x1, y1), name)) }

    fun cover(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, col: Int = Color.parseColor("#0A2F47")) {
        p.style = Paint.Style.FILL; p.color = col; p.alpha = 250
        c.drawRoundRect(RectF(X(x0), Y(y0), X(x1), Y(y1)), d(6f), d(6f), p)
    }

    fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int, a: Paint.Align = Paint.Align.LEFT) {
        tp.textSize = d(size); tp.color = col; tp.textAlign = a
        c.drawText(s, X(x), Y(y), tp)
    }

    override fun onDraw(c: Canvas) {
        p.style = Paint.Style.FILL; p.alpha = 255
        c.drawColor(Color.parseColor("#020B1A"))
        c.drawBitmap(bm, Rect((cl * bm.width).toInt(), (ct * bm.height).toInt(), (cr * bm.width).toInt(), (cb * bm.height).toInt()), RectF(0f, 0f, width.toFloat(), height.toFloat()), p)
        overlay(this, c)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action != MotionEvent.ACTION_UP && e.action != MotionEvent.ACTION_MOVE) return true
        val nx = cl + e.x / width * (cr - cl)
        val ny = ct + e.y / height * (cb - ct)
        for ((r, n) in zones) if (r.contains(nx, ny)) {
            if (n.startsWith("slider") || e.action == MotionEvent.ACTION_UP) onAction(n + ":" + ((nx - r.left) / r.width()))
            return true
        }
        return true
    }
}
