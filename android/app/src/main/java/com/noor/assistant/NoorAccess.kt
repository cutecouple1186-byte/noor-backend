package com.noor.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Do kaam: (1) WhatsApp ka Send button dabana (sirf jab Noor ne message bhejne ko kaha ho),
 * (2) tumhare bolne par screen chalana: back, home, scroll, swipe, tap.
 */
class NoorAccess : AccessibilityService() {

    companion object {
        @Volatile
        var pendingUntil = 0L

        @Volatile
        var instance: NoorAccess? = null

        fun arm() {
            pendingUntil = System.currentTimeMillis() + 20000
        }

        /** Screen ka kaam. null = ho gaya, warna wajah (bolne ke liye). */
        fun screen(action: String, text: String, x: Int, y: Int): String? {
            val s = instance ?: return "Pehle Settings mein Accessibility mein Noor ko ON karein."
            return s.doAction(action, text, x, y)
        }
    }

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (System.currentTimeMillis() > pendingUntil) return
        val root = rootInActiveWindow ?: return
        val btn = findSend(root) ?: return
        if (btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            pendingUntil = 0L
        }
    }

    private fun findSend(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in listOf("com.whatsapp:id/send", "com.whatsapp.w4b:id/send")) {
            val l = root.findAccessibilityNodeInfosByViewId(id)
            if (!l.isNullOrEmpty()) return l[0]
        }
        for (d in listOf("Send", "send", "بھیجیں", "भेजें")) {
            val l = root.findAccessibilityNodeInfosByText(d)
            val hit = l?.firstOrNull { it.isClickable || it.contentDescription?.toString().equals(d, true) }
            if (hit != null) return hit
        }
        return null
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean {
        val p = Path()
        p.moveTo(x1, y1)
        p.lineTo(x2, y2)
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build()
        return dispatchGesture(g, null, null)
    }

    private fun doAction(action: String, text: String, x: Int, y: Int): String? {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val ok = when (action) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "scroll_down" -> swipe(w * 0.5f, h * 0.72f, w * 0.5f, h * 0.28f, 300)
            "scroll_up" -> swipe(w * 0.5f, h * 0.28f, w * 0.5f, h * 0.72f, 300)
            "swipe_left" -> swipe(w * 0.8f, h * 0.5f, w * 0.2f, h * 0.5f, 250)
            "swipe_right" -> swipe(w * 0.2f, h * 0.5f, w * 0.8f, h * 0.5f, 250)
            "tap" -> swipe(w * x.coerceIn(0, 100) / 100f, h * y.coerceIn(0, 100) / 100f, w * x.coerceIn(0, 100) / 100f + 1f, h * y.coerceIn(0, 100) / 100f + 1f, 60)
            "tap_text" -> return tapText(text)
            else -> return "Ye screen ka kaam mujhe samajh nahi aaya."
        }
        return if (ok) null else "Screen ka kaam nahi ho saka."
    }

    private fun tapText(text: String): String? {
        if (text.isBlank()) return "Kis button par dabana hai?"
        val root = rootInActiveWindow ?: return "Screen padh nahi saki."
        val list = root.findAccessibilityNodeInfosByText(text)
        var n: AccessibilityNodeInfo? = list?.firstOrNull() ?: return "Screen par \"$text\" nahi mila."
        while (n != null && !n.isClickable) n = n.parent
        if (n == null) return "\"$text\" dabane layak nahi hai."
        return if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) null else "Dabane mein masla aaya."
    }

    override fun onInterrupt() {}
}
