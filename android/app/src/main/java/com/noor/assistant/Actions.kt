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

    // Jaani-pehchani apps: bolne ka naam -> mumkin package. Jo phone mein ho wahi khulti hai.
    private val KNOWN = listOf(
        listOf("whatsapp business", "business whatsapp", "w4b") to listOf("com.whatsapp.w4b"),
        listOf("whatsapp", "whats app") to listOf("com.whatsapp", "com.whatsapp.w4b"),
        listOf("tiktok lite", "tik tok lite") to listOf("com.zhiliaoapp.musically.go"),
        listOf("tiktok", "tik tok") to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"),
        listOf("instagram", "insta") to listOf("com.instagram.android"),
        listOf("snapchat", "snap chat") to listOf("com.snapchat.android"),
        listOf("facebook", "fb") to listOf("com.facebook.katana", "com.facebook.lite"),
        listOf("youtube studio", "yt studio", "studio") to listOf("com.google.android.apps.youtube.creator"),
        listOf("youtube", "you tube") to listOf("com.google.android.youtube"),
        listOf("chrome", "browser") to listOf("com.android.chrome"),
        listOf("gmail", "mail") to listOf("com.google.android.gm"),
        listOf("drive") to listOf("com.google.android.apps.docs"),
        listOf("photos", "google photos") to listOf("com.google.android.apps.photos"),
        listOf("meet") to listOf("com.google.android.apps.tachyon"),
        listOf("maps", "google maps") to listOf("com.google.android.apps.maps"),
        listOf("play store", "playstore") to listOf("com.android.vending"),
        listOf("chatgpt", "chat gpt", "gpt") to listOf("com.openai.chatgpt"),
        listOf("gemini") to listOf("com.google.android.apps.bard"),
        listOf("claude") to listOf("com.anthropic.claude"),
        listOf("pinterest") to listOf("com.pinterest"),
        listOf("picsart", "pics art") to listOf("com.picsart.studio"),
        listOf("capcut", "cap cut") to listOf("com.lemon.lvoverseas", "com.lemon.lvoverseas.lite"),
        listOf("wps", "wps office") to listOf("cn.wps.moffice_eng"),
        listOf("snaptube", "snap tube") to listOf("com.snaptube.premium"),
        listOf("github", "git hub") to listOf("com.github.android"),
        listOf("free fire", "freefire") to listOf("com.dts.freefireth", "com.dts.freefiremax"),
        listOf("settings", "setting") to listOf("com.android.settings"),
        listOf("camera") to listOf("com.android.camera", "com.oplus.camera", "com.coloros.camera"),
        listOf("contacts", "contact") to listOf("com.google.android.contacts", "com.android.contacts"),
        listOf("phone", "dialer") to listOf("com.google.android.dialer", "com.android.dialer"),
        listOf("messages", "message", "sms") to listOf("com.google.android.apps.messaging", "com.android.mms")
    )

    private fun installed(ctx: Context, pkg: String): Boolean =
        try {
            ctx.packageManager.getLaunchIntentForPackage(pkg) != null
        } catch (e: Exception) {
            false
        }

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
        for ((names, pkgs) in KNOWN) {
            if (names.any { q == it }) {
                val hit = pkgs.firstOrNull { installed(ctx, it) }
                if (hit != null) return hit
            }
        }
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
                val canCall = ctx.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                val act = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
                if (!launch(ctx, Intent(act, Uri.parse("tel:$num")))) return "Call nahi lag saki."
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
                    NoorAccess.arm()
                    view(ctx, "https://wa.me/${intl(num)}?text=${Uri.encode(text)}", "com.whatsapp")
                } else if (ctx.checkSelfPermission(android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val sm = if (android.os.Build.VERSION.SDK_INT >= 31) ctx.getSystemService(android.telephony.SmsManager::class.java)
                        else @Suppress("DEPRECATION") android.telephony.SmsManager.getDefault()
                        sm.sendMultipartTextMessage(num, null, sm.divideMessage(text), null, null)
                        true
                    } catch (e: Exception) {
                        false
                    }
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
