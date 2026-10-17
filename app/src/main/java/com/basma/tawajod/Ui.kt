package com.basma.tawajod

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Colors for light and dark mode. */
class Palette(dark: Boolean) {
    val bg = if (dark) c("#0F1614") else c("#F3F6F5")
    val surface = if (dark) c("#18221F") else c("#FFFFFF")
    val fg = if (dark) c("#E7EFEC") else c("#14201D")
    val muted = if (dark) c("#93A6A0") else c("#5E6F6A")
    val line = if (dark) c("#26332F") else c("#DDE6E3")
    val accent = if (dark) c("#3CC0B0") else c("#0F766E")
    val accentSoft = if (dark) c("#173430") else c("#E0F2EF")
    val onAccent = if (dark) c("#05201C") else c("#FFFFFF")
    val warnSoft = if (dark) c("#332810") else c("#FDF1DE")
    val warn = if (dark) c("#F3B556") else c("#8A5300")
    val bad = if (dark) c("#F28B82") else c("#B42318")
    private fun c(s: String) = Color.parseColor(s)
}

/** Shared helpers so every screen looks the same. */
abstract class BaseActivity : Activity() {
    lateinit var pal: Palette
    lateinit var p: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        pal = Palette(dark)
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        if (!dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    fun rounded(color: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    fun column(padH: Int = 20, padV: Int = 20) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padH), dp(padV), dp(padH), dp(padV))
    }

    fun text(s: String, size: Float = 16f, color: Int = pal.fg, bold: Boolean = false, center: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            setLineSpacing(0f, 1.25f)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (center) gravity = Gravity.CENTER
        }

    fun primary(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(pal.onAccent); background = rounded(pal.accent, 16)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    fun secondary(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f
        setTextColor(pal.accent); background = rounded(pal.accentSoft, 16)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    fun link(label: String, color: Int = pal.muted, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 15f; setTextColor(color); gravity = Gravity.CENTER
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { onClick() }
    }

    fun LinearLayout.add(v: View, h: Int = -2, top: Int = 0) {
        addView(v, LinearLayout.LayoutParams(-1, if (h > 0) dp(h) else h).apply { topMargin = dp(top) })
    }

    // ---- What's already set up ----
    fun has(perm: String) = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    fun notificationsOn() = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
    fun ringoReaderOn(): Boolean {
        val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return on.contains("$packageName/") && on.contains("RingoReader")
    }
    fun batteryOn() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    fun locationOn() = has(Manifest.permission.ACCESS_FINE_LOCATION) &&
        (Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    /** The things the app can't work without. */
    fun essentialsMissing() = !notificationsOn() || !ringoReaderOn() || !batteryOn()

    // ---- Asking for each one (system pop-ups where Android allows it) ----
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
    }

    fun askBattery() = startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))

    /** Android has no pop-up for this one, so we open the right settings page with one line of help. */
    fun askRingo() {
        AlertDialog.Builder(this)
            .setTitle("آخر خطوة 👆")
            .setMessage("عشان نقرأ بصمتك من Ringo:\n\nاضغط «فتح» ← «التطبيقات المثبتة» ← «بصمة التواجد» ← شغّله")
            .setPositiveButton("فتح") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNeutralButton("الزر مقفل؟") { _, _ ->
                toast("اضغط ⋮ فوق ← «السماح بالإعدادات المحظورة» وارجع")
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .setNegativeButton("بعدين", null)
            .show()
    }

    fun askLocation() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 12)
        } else if (Build.VERSION.SDK_INT >= 30 && !locationOn()) {
            toast("اختر «الموقع» ← «السماح طوال الوقت»")
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else if (Build.VERSION.SDK_INT == 29 && !locationOn()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 13)
        }
    }
}
