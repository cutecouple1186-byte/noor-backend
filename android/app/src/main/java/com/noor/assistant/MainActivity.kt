package com.noor.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Noor ka chhota sa control screen: server, password, permissions, Start/Stop. */
class MainActivity : Activity() {

    companion object {
        const val DEFAULT_URL = "https://noor-backend-nine.vercel.app"
    }

    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var urlEt: EditText
    private lateinit var tokEt: EditText

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        b.layoutParams = lp
        return b
    }

    private fun label(text: String, size: Float = 14f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(Color.WHITE)
        t.setPadding(0, dp(10), 0, dp(2))
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("noor", Context.MODE_PRIVATE)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(28), dp(18), dp(28))
        root.setBackgroundColor(Color.parseColor("#0B0014"))

        val title = label("NOOR AI", 26f)
        title.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(title)

        status = label("", 15f)
        root.addView(status)

        root.addView(label("Server (Vercel ka link)"))
        urlEt = EditText(this)
        urlEt.setText(sp.getString("url", DEFAULT_URL))
        urlEt.setTextColor(Color.WHITE)
        urlEt.inputType = InputType.TYPE_TEXT_VARIATION_URI
        root.addView(urlEt)

        root.addView(label("Password (jo Vercel mein APP_TOKEN hai)"))
        tokEt = EditText(this)
        tokEt.setText(sp.getString("token", ""))
        tokEt.setTextColor(Color.WHITE)
        tokEt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        root.addView(tokEt)

        root.addView(btn("1) Mic, contacts aur notification allow karo") { askPermissions() })
        root.addView(btn("2) Dusri app ke upar dikhne do (apps kholne ke liye)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(btn("3) Battery: background mein chalne do") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                Toast.makeText(this, "Settings se Battery > Noor > No restrictions chunein", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(btn("4) App info (Autostart / Background allow karein)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })

        toggle = btn("") { toggleService() }
        root.addView(toggle)

        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.parseColor("#0B0014"))
        sv.addView(root)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun askPermissions() {
        val list = ArrayList<String>()
        list.add(Manifest.permission.RECORD_AUDIO)
        list.add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(list.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun saveSettings() {
        getSharedPreferences("noor", Context.MODE_PRIVATE).edit()
            .putString("url", urlEt.text.toString().trim().ifEmpty { DEFAULT_URL })
            .putString("token", tokEt.text.toString().trim())
            .apply()
    }

    private fun toggleService() {
        saveSettings()
        if (NoorService.isRunning) {
            startService(Intent(this, NoorService::class.java).setAction(NoorService.ACTION_STOP))
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Pehle button 1 dabakar mic allow karein", Toast.LENGTH_LONG).show()
                askPermissions()
                return
            }
            startForegroundService(Intent(this, NoorService::class.java))
        }
        toggle.postDelayed({ refresh() }, 600)
    }

    private fun refresh() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery = pm.isIgnoringBatteryOptimizations(packageName)
        fun m(ok: Boolean) = if (ok) "OK" else "baaki"
        status.text = (if (NoorService.isRunning) "Noor chal rahi hai. Hey Noor bolein." else "Noor band hai.") +
            "\nMic: ${m(mic)}   Contacts: ${m(contacts)}" +
            "\nApps kholna: ${m(overlay)}   Background: ${m(battery)}"
        toggle.text = if (NoorService.isRunning) "Noor BAND karo" else "Noor SHURU karo"
    }
}
