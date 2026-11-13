package com.noor.assistant

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Asli 3D wireframe hologram: chhote shapes (box, sphere, ...) se bana model, har taraf se ghoomta hai. */
class Holo3D(val name: String) {
    /** har segment: x1,y1,z1,x2,y2,z2 */
    val seg = ArrayList<FloatArray>()
    val part = ArrayList<Int>()
    val partNames = ArrayList<String>()

    companion object {
        fun fromJson(j: JSONObject): Holo3D? {
            val arr: JSONArray = j.optJSONArray("parts") ?: return null
            val m = Holo3D(j.optString("name", ""))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val s = o.optString("s", "box")
                val pp = o.optJSONArray("p"); val zz = o.optJSONArray("z"); val rr = o.optJSONArray("r")
                fun g(a: JSONArray?, k: Int, d: Double) = if (a != null && k < a.length()) a.optDouble(k, d).toFloat() else d.toFloat()
                val sx = g(zz, 0, 0.2).coerceIn(0.02f, 1.2f); val sy = g(zz, 1, 0.2).coerceIn(0.02f, 1.2f); val sz = g(zz, 2, 0.2).coerceIn(0.02f, 1.2f)
                val px = g(pp, 0, 0.0); val py = g(pp, 1, 0.0); val pz = g(pp, 2, 0.0)
                val rx = Math.toRadians(g(rr, 0, 0.0).toDouble()).toFloat(); val ry = Math.toRadians(g(rr, 1, 0.0).toDouble()).toFloat(); val rz = Math.toRadians(g(rr, 2, 0.0).toDouble()).toFloat()
                val idx = m.partNames.size
                m.partNames.add(o.optString("n", s))
                val local = ArrayList<FloatArray>()
                when (s) {
                    "sphere" -> ring3(local, 14)
                    "cylinder" -> cyl(local, 14, false)
                    "cone" -> cyl(local, 14, true)
                    "torus" -> torus(local, 18, sy / max(sx, 0.001f))
                    else -> box(local)
                }
                for (l in local) {
                    val a = tf(l[0] * sx, l[1] * sy, l[2] * sz, rx, ry, rz, px, py, pz)
                    val b = tf(l[3] * sx, l[4] * sy, l[5] * sz, rx, ry, rz, px, py, pz)
                    m.seg.add(floatArrayOf(a[0], a[1], a[2], b[0], b[1], b[2])); m.part.add(idx)
                }
            }
            return if (m.seg.isEmpty()) null else m
        }

        private fun tf(x0: Float, y0: Float, z0: Float, rx: Float, ry: Float, rz: Float, px: Float, py: Float, pz: Float): FloatArray {
            var x = x0; var y = y0; var z = z0
            var t = x * cos(rz) - y * sin(rz); y = x * sin(rz) + y * cos(rz); x = t
            t = x * cos(ry) + z * sin(ry); z = -x * sin(ry) + z * cos(ry); x = t
            t = y * cos(rx) - z * sin(rx); z = y * sin(rx) + z * cos(rx); y = t
            return floatArrayOf(x + px, y + py, z + pz)
        }
        private fun box(o: ArrayList<FloatArray>) {
            val v = arrayOf(floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f), floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f), floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f))
            val e = intArrayOf(0, 1, 1, 2, 2, 3, 3, 0, 4, 5, 5, 6, 6, 7, 7, 4, 0, 4, 1, 5, 2, 6, 3, 7)
            for (i in e.indices step 2) o.add(floatArrayOf(v[e[i]][0], v[e[i]][1], v[e[i]][2], v[e[i + 1]][0], v[e[i + 1]][1], v[e[i + 1]][2]))
        }
        private fun circ(o: ArrayList<FloatArray>, n: Int, f: (Float) -> FloatArray) {
            var prev = f(0f)
            for (i in 1..n) { val a = (i.toFloat() / n) * (2 * PI.toFloat()); val q = f(a); o.add(floatArrayOf(prev[0], prev[1], prev[2], q[0], q[1], q[2])); prev = q }
        }
        private fun ring3(o: ArrayList<FloatArray>, n: Int) {
            circ(o, n) { a -> floatArrayOf(cos(a), sin(a), 0f) }
            circ(o, n) { a -> floatArrayOf(cos(a), 0f, sin(a)) }
            circ(o, n) { a -> floatArrayOf(0f, cos(a), sin(a)) }
            val k = 0.7f; val h = 0.714f
            circ(o, n) { a -> floatArrayOf(cos(a) * k, h, sin(a) * k) }
            circ(o, n) { a -> floatArrayOf(cos(a) * k, -h, sin(a) * k) }
        }
        private fun cyl(o: ArrayList<FloatArray>, n: Int, cone: Boolean) {
            circ(o, n) { a -> floatArrayOf(cos(a), -1f, sin(a)) }
            if (!cone) circ(o, n) { a -> floatArrayOf(cos(a), 1f, sin(a)) }
            for (i in 0 until 6) { val a = i / 6f * 2 * PI.toFloat(); if (cone) o.add(floatArrayOf(cos(a), -1f, sin(a), 0f, 1f, 0f)) else o.add(floatArrayOf(cos(a), -1f, sin(a), cos(a), 1f, sin(a))) }
        }
        private fun torus(o: ArrayList<FloatArray>, n: Int, tube: Float) {
            val R = 1f - tube; val r = tube
            circ(o, n) { a -> floatArrayOf(cos(a) * (R + r), 0f, sin(a) * (R + r)) }
            circ(o, n) { a -> floatArrayOf(cos(a) * (R - r), 0f, sin(a) * (R - r)) }
            circ(o, n) { a -> floatArrayOf(cos(a) * R, r, sin(a) * R) }
            circ(o, n) { a -> floatArrayOf(cos(a) * R, -r, sin(a) * R) }
        }
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    /** cx,cy = model ka centre, s = pixels per unit, build = 0..1 (parts ek ek kar ke jud-te hain) */
    fun draw(c: Canvas, cx: Float, cy: Float, s: Float, yaw: Float, pitch: Float, t: Float, build: Float, dens: Float) {
        val n = partNames.size
        val cyaw = cos(yaw); val syaw = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
        val scanY = sin(t * 1.2f) * 1.1f
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        for (pass in 0..1) {
            for (i in seg.indices) {
                val pi = part[i]
                val appear = (build * (n + 2) - pi).coerceIn(0f, 1f)
                if (appear <= 0f) continue
                val g = seg[i]
                fun proj(x: Float, y0: Float, z0: Float): FloatArray {
                    val dropY = (1f - appear) * 0.9f
                    val y = y0 + dropY
                    val x1 = x * cyaw + z0 * syaw; val z1 = -x * syaw + z0 * cyaw
                    val y2 = y * cp - z1 * sp; val z2 = y * sp + z1 * cp
                    val k = 3.4f / (3.4f + z2)
                    return floatArrayOf(cx + x1 * s * k, cy - y2 * s * k, z2)
                }
                val a = proj(g[0], g[1], g[2]); val b = proj(g[3], g[4], g[5])
                val depth = ((a[2] + b[2]) / 2f + 1.3f) / 2.6f // 0 door .. 1 paas? (z2 negative = paas)
                val near = 1f - depth.coerceIn(0f, 1f)
                val ym = (g[1] + g[4]) / 2f
                val hot = (1f - min(1f, abs(ym - scanY) * 4f))
                val al = ((60 + 150 * near) * appear * dens + 120 * hot).coerceIn(0f, 255f)
                if (pass == 0) { p.strokeWidth = 5f * dens.coerceAtLeast(0.6f) * (0.8f + near); p.color = Color.argb((al * 0.22f).toInt(), 34, 211, 238) }
                else { p.strokeWidth = 1.6f * (0.8f + near); p.color = Color.argb(al.toInt(), (120 + 135 * hot).toInt(), (220 + 35 * hot).toInt(), 255) }
                c.drawLine(a[0], a[1], b[0], b[1], p)
            }
        }
    }
}
