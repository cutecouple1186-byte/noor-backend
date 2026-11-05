package com.noor.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import org.json.JSONObject
import java.util.Calendar

/** Phone ke kaam: app kholna, torch, volume, alarm, call waghera. */
object Actions {

    // Hindi/Urdu naam -> English naam (baaki apps ke liye label seedha phone se padha jata hai)
    private val ALIASES = mapOf(
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वाट्सएप" to "whatsapp",
        "واٹس ایپ" to "whatsapp", "واٹس اپ" to "whatsapp",
        "यूट्यूब" to "youtube", "यू ट्यूब" to "youtube", "یوٹیوب" to "youtube",
        "टिकटॉक" to "tiktok", "टिक टॉक" to "tiktok", "ٹک ٹاک" to "tiktok", "ٹیک ٹاک" to "tiktok",
        "इंस्टाग्राम" to "instagram", "انسٹاگرام" to "instagram",
        "फेसबुक" to "facebook", "فیس بک" to "facebook",
        "स्नैपचैट" to "snapchat", "سنیپ چیٹ" to "snapchat",
        "टेलीग्राम" to "telegram", "ٹیلیگرام" to "telegram",
        "जीमेल" to "gmail", "جی میل" to "gmail",
        "क्रोम" to "chrome", "کروم" to "chrome",
        "कैमरा" to "camera", "کیمرہ" to "camera",
        "गैलरी" to "gallery", "گیلری" to "gallery",
        "सेटिंग" to "settings", "سیٹنگ" to "settings",
        "कैलकुलेटर" to "calculator",
        "घड़ी" to "clock",
        "प्ले स्टोर" to "play store", "پلے اسٹور" to "play store",
        "जैज़कैश" to "jazzcash", "جاز کیش" to "jazzcash",
        "ईज़ीपैसा" to "easypaisa", "ایزی پیسہ" to "easypaisa"
    )

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    fun launch(ctx: Context, i: Intent): Boolean {
        return try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Phone ki sab launcher apps mein se naam se sab se qareebi app dhoondho. */
    fun findPackage(ctx: Context, spoken: String): String? {
        var q = spoken.lowercase()
        for ((k, v) in ALIASES) if (q.contains(k)) q = q.replace(k, v)
        q = norm(q)
        if (q.isEmpty()) return null
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        var best: String? = null
        var bestScore = 0
        var bestLen = Int.MAX_VALUE
        for (ri in pm.queryIntentActivities(main, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName) continue
            val label = norm(ri.loadLabel(pm).toString())
            if (label.isEmpty()) continue
            val score = when {
                label == q -> 100
                label.startsWith(q) || q.startsWith(label) -> 80
                q.length >= 3 && label.contains(q) -> 60
                label.length >= 3 && q.contains(label) -> 50
                q.length >= 4 && pkg.contains(q.replace(" ", "")) -> 40
                else -> 0
            }
            if (score > bestScore || (score == bestScore && score > 0 && label.length < bestLen)) {
                best = pkg
                bestScore = score
                bestLen = label.length
            }
        }
        return best
    }

    fun openApp(ctx: Context, name: String): Boolean {
        val pkg = findPackage(ctx, name) ?: return false
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return launch(ctx, i)
    }

    private fun torch(ctx: Context, on: Boolean): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                val ch = cm.getCameraCharacteristics(it)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return false
            cm.setTorchMode(id, on)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun volume(ctx: Context, level: Int, direction: String): Boolean {
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (level in 0..100) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, level * max / 100, AudioManager.FLAG_SHOW_UI)
            } else {
                val d = if (direction == "down") AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI)
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun contactNumber(ctx: Context, name: String): String? {
        if (name.isBlank()) return null
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$name%"),
                null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    private fun digits(s: String): String = s.replace(Regex("[^0-9+]"), "")

    /** Pakistan: 03xx... -> 923xx... (WhatsApp ko country code chahiye) */
    private fun intl(n: String): String {
        val d = n.replace(Regex("[^0-9]"), "")
        return if (d.startsWith("0")) "92" + d.substring(1) else d
    }

    private fun view(ctx: Context, uri: String, pkg: String?): Boolean {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        if (pkg != null) {
            i.setPackage(pkg)
            if (launch(ctx, i)) return true
            i.setPackage(null)
        }
        return launch(ctx, i)
    }

    /** Kaam karo. Agar na ho paye to bolne ke liye jawab lauta do, warna null. */
    fun execute(ctx: Context, intent: String, s: JSONObject): String? {
        when (intent) {
            "open_app" -> {
                val name = s.optString("name")
                if (name.isBlank()) return "Kaun si app kholni hai?"
                if (!openApp(ctx, name)) return "Mujhe $name naam ki app nahi mili."
            }
            "torch" -> {
                if (!torch(ctx, s.optBoolean("on", true))) return "Torch nahi chal saki."
            }
            "volume" -> {
                if (!volume(ctx, s.optInt("level", -1), s.optString("direction"))) return "Volume nahi badal saki."
            }
            "set_alarm" -> {
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, s.optInt("hour", 7))
                    .putExtra(AlarmClock.EXTRA_MINUTES, s.optInt("minute", 0))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, s.optString("label", "Noor"))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (!launch(ctx, i)) return "Alarm nahi laga saki."
            }
            "set_timer" -> {
                val i = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, s.optInt("seconds", 60))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (!launch(ctx, i)) return "Timer nahi laga saki."
            }
            "call" -> {
                var num = digits(s.optString("number"))
                if (num.isEmpty()) num = digits(contactNumber(ctx, s.optString("name")) ?: "")
                if (num.isEmpty()) return "Is naam ka number mujhe nahi mila."
                if (!launch(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))) return "Dialer nahi khul saka."
            }
            "play_youtube" -> {
                val q = Uri.encode(s.optString("query"))
                if (!view(ctx, "https://www.youtube.com/results?search_query=$q", "com.google.android.youtube")) return "YouTube nahi khul saka."
            }
            "maps" -> {
                val place = Uri.encode(s.optString("place"))
                val uri = if (s.optBoolean("navigate", false)) "google.navigation:q=$place" else "geo:0,0?q=$place"
                if (!view(ctx, uri, "com.google.android.apps.maps")) return "Maps nahi khul saka."
            }
            "open_settings" -> {
                val action = when (s.optString("target")) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                if (!launch(ctx, Intent(action))) return "Settings nahi khul saki."
            }
            "add_event" -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, s.optInt("day_offset", 0))
                cal.set(Calendar.HOUR_OF_DAY, s.optInt("hour", 9))
                cal.set(Calendar.MINUTE, s.optInt("minute", 0))
                cal.set(Calendar.SECOND, 0)
                val begin = cal.timeInMillis
                val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, s.optString("title", "Noor"))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 3600000L)
                if (!launch(ctx, i)) return "Calendar nahi khul saka."
            }
            "send_message" -> {
                val text = s.optString("text")
                var num = digits(s.optString("number"))
                if (num.isEmpty()) num = digits(contactNumber(ctx, s.optString("name")) ?: "")
                if (num.isEmpty()) return "Is naam ka number mujhe nahi mila."
                val ok = if (s.optString("app").lowercase() == "whatsapp") {
                    view(ctx, "https://wa.me/${intl(num)}?text=${Uri.encode(text)}", "com.whatsapp")
                } else {
                    launch(
                        ctx,
                        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", text)
                    )
                }
                if (!ok) return "Message nahi khul saka."
            }
        }
        return null
    }
}
