package com.noor.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Sirf WhatsApp ka Send button dabane ke liye, aur sirf tab jab Noor ne message bhejne ko kaha ho. */
class NoorAccess : AccessibilityService() {

    companion object {
        @Volatile
        var pendingUntil = 0L

        fun arm() {
            pendingUntil = System.currentTimeMillis() + 20000
        }
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

    override fun onInterrupt() {}
}
