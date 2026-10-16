package com.basma.tawajod

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var p: Prefs
    private lateinit var statusText: TextView
    private lateinit var countdownText: TextView
    private lateinit var locationText: TextView
    private lateinit var rulesBox: LinearLayout
    private lateinit var examplesText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateText: TextView
    private lateinit var updateBtn: Button
    private var pendingRelease: Updater.Release? = null

    private val accent = Color.parseColor("#0F766E")
    private val danger = Color.parseColor("#B91C1C")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        Scheduler.createChannels(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        buildUi()
        Scheduler.startWatching(this)
        Updater.scheduleDaily(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.post(ticker)
        Updater.checkAsync(this, notify = false) { rel -> runOnUiThread { showUpdate(rel) } }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 1000)
        }
    }

    // ------------------------------------------------------------------ UI

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
            setBackgroundColor(Color.parseColor("#F5F5F4"))
        }

        root.addView(TextView(this).apply {
            text = "بصمة التواجد"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1917"))
        })
        root.addView(small("يحسب لك وقت بصمة التواجد أول ما توصل الدوام ويرن لك وقتها"))

        // --- Today
        val today = card(root, "اليوم")
        statusText = body("")
        countdownText = TextView(this).apply {
            textSize = 34f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        today.addView(statusText)
        today.addView(countdownText)
        today.addView(button("بصمت دخول الحين") { arrivedAt(System.currentTimeMillis()) }, secondary = false)
        today.addView(button("أدخل وقت الدخول يدوي") { pickArrivalTime() }, secondary = true)
        today.addView(button("إلغاء تنبيه اليوم") {
            Scheduler.cancelAlarm(this); p.lastArrivalMillis = 0; refresh()
        }, secondary = true)

        // --- Location
        val loc = card(root, "موقع الدوام")
        locationText = body("")
        loc.addView(locationText)
        loc.addView(button("أنا الحين في الدوام — احفظ الموقع") { saveCurrentLocation() }, secondary = false)
        loc.addView(button("تغيير نطاق الموقع (بالمتر)") {
            askNumber("نطاق الموقع بالمتر", p.radiusM) { p.radiusM = it.coerceIn(50, 2000); Scheduler.startWatching(this); refresh() }
        }, secondary = true)

        // --- Permissions
        val perm = card(root, "الصلاحيات (مرة وحدة بس)")
        perm.addView(small("لازم كلها عشان يشتغل التطبيق والجوال مقفل"))
        perm.addView(button("⭐ قراءة بصمة Ringo HR (الأدق)") { openRingoSetting() }, secondary = false)
        perm.addView(small("بعدها كل ما تبصم دخول في Ringo وطلع \"تم تسجيل الدخول\" ياخذ الوقت بالضبط. الموقع يبقى احتياط."))
        perm.addView(button("١. صلاحية الموقع") { requestLocation() }, secondary = true)
        perm.addView(button("٢. الموقع \"السماح طوال الوقت\"") { requestBackgroundLocation() }, secondary = true)
        perm.addView(button("٣. الإشعارات") { requestNotifications() }, secondary = true)
        perm.addView(button("٤. استثناء من توفير البطارية") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            toast("اختر \"كل التطبيقات\" ثم بصمة التواجد ← لا تحسّن")
        }, secondary = true)

        // --- Rules
        val rules = card(root, "قواعد الحساب")
        rulesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rules.addView(rulesBox)
        examplesText = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#44403C"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.parseColor("#F0FDFA"), 10)
        }
        rules.addView(TextView(this).apply { text = "أمثلة بالإعدادات الحالية:"; setPadding(0, dp(12), 0, dp(6)) })
        rules.addView(examplesText)

        // --- Version / update
        val upd = card(root, "التحديثات")
        updateText = body("الإصدار ${BuildConfig.VERSION_NAME}")
        upd.addView(updateText)
        updateBtn = button("تحقق من التحديث") {
            updateText.text = "جاري التحقق…"
            Updater.checkAsync(this, notify = false) { rel -> runOnUiThread { showUpdate(rel, userAsked = true) } }
        }
        upd.addView(updateBtn, secondary = true)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun buildRules() {
        rulesBox.removeAllViews()
        rulesBox.addView(row("قبل هالوقت تنحسب نفسه", Rules.fmt(p.earliestStart)) {
            pickTime(p.earliestStart) { p.earliestStart = it; refresh() }
        })
        rulesBox.addView(CheckBox(this).apply {
            text = "نافذة التسامح (تنحسب من بدايتها)"
            isChecked = p.graceEnabled
            setOnCheckedChangeListener { _, c -> p.graceEnabled = c; refresh() }
        })
        if (p.graceEnabled) {
            rulesBox.addView(row("  من", Rules.fmt(p.graceFrom)) { pickTime(p.graceFrom) { p.graceFrom = it; refresh() } })
            rulesBox.addView(row("  إلى", Rules.fmt(p.graceTo)) { pickTime(p.graceTo) { p.graceTo = it; refresh() } })
        }
        rulesBox.addView(row("المدة بعد الدخول (دقيقة)", "${p.gapMinutes}") {
            askNumber("المدة بالدقائق (ساعتين ودقيقة = 121)", p.gapMinutes) { p.gapMinutes = it; refresh() }
        })
        rulesBox.addView(row("هامش أمان (دقيقة)", "${p.marginMinutes}") {
            askNumber("هامش الأمان بالدقائق", p.marginMinutes) { p.marginMinutes = it.coerceIn(0, 60); refresh() }
        })
        rulesBox.addView(row("أيام الإجازة", offDaysLabel()) { pickOffDays() })
    }

    private fun refresh() {
        // Today
        val arrival = p.lastArrivalMillis
        val now = System.currentTimeMillis()
        statusText.text = if (arrival != 0L && Rules.isSameDay(arrival, now)) {
            val counted = Rules.countedEntry(Rules.minuteOf(arrival), p)
            val at = Rules.presenceTime(arrival, p)
            val src = when (p.lastSource) { "RINGO" -> "من Ringo"; "MANUAL" -> "يدوي"; else -> "من الموقع" }
            "دخول: ${Rules.fmtMillis(arrival)} ($src)\nينحسب الدخول من: ${Rules.fmt(counted)}\nبصمة التواجد: ${Rules.fmtMillis(at)}"
        } else {
            if (Rules.isOffDay(now, p)) "اليوم إجازة 🌴" else "ما وصلت الدوام اليوم للحين"
        }
        // Location
        val fine = has(Manifest.permission.ACCESS_FINE_LOCATION)
        val bg = Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        locationText.text = buildString {
            if (p.hasLocation) append("✅ محفوظ — نطاق ${p.radiusM} متر\n")
            else append("❌ ما حفظت موقع الدوام\n")
            append(if (fine) "✅ صلاحية الموقع\n" else "❌ صلاحية الموقع\n")
            append(if (bg) "✅ يشتغل بالخلفية\n" else "❌ لازم \"السماح طوال الوقت\"\n")
            append(if (ringoReaderOn()) "✅ يقرأ بصمة Ringo" else "⚪ قراءة Ringo مطفية (فعّلها من الصلاحيات)")
        }
        buildRules()
        examplesText.text = listOf(6 * 60 + 45, 7 * 60, 7 * 60 + 40, 8 * 60 + 10, 8 * 60 + 30, 8 * 60 + 45)
            .joinToString("\n") { "دخول ${Rules.fmt(it)}  ←  تواجد ${Rules.fmt(Rules.presenceMinute(it, p))}" }
        updateCountdown()
    }

    private fun updateCountdown() {
        val at = p.alarmAtMillis
        val left = at - System.currentTimeMillis()
        countdownText.visibility = if (at > 0 && left > 0) View.VISIBLE else View.GONE
        if (left > 0) {
            val s = left / 1000
            countdownText.text = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        }
    }

    private fun showUpdate(rel: Updater.Release?, userAsked: Boolean = false) {
        pendingRelease = rel
        if (rel != null) {
            updateText.text = "فيه نسخة جديدة ✨ (${rel.name})" + if (rel.notes.isNotBlank()) "\n${rel.notes}" else ""
            updateBtn.text = "نزّل التحديث"
            updateBtn.setOnClickListener {
                startActivity(Updater.downloadIntent(rel))
                toast("بعد ما ينزل افتحه واضغط تثبيت — إعداداتك تبقى")
            }
        } else if (userAsked) {
            updateText.text = "الإصدار ${BuildConfig.VERSION_NAME} — عندك آخر نسخة ✅"
        }
    }

    // ------------------------------------------------------------------ Actions

    private fun arrivedAt(millis: Long) {
        val at = Scheduler.onArrived(this, millis, Scheduler.Source.MANUAL) ?: return
        toast("بنبهك الساعة ${Rules.fmtMillis(at)}")
        refresh()
    }

    private fun pickArrivalTime() {
        val c = Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0)
            arrivedAt(c.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
    }

    private fun saveCurrentLocation() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) { requestLocation(); return }
        val lm = getSystemService(LocationManager::class.java)
        toast("جاري تحديد موقعك…")
        val onLoc: (Location?) -> Unit = { l ->
            if (l == null) toast("ما قدرت أحدد موقعك، شغّل GPS وجرب مرة ثانية")
            else {
                p.lat = l.latitude; p.lng = l.longitude; p.hasLocation = true
                Scheduler.startWatching(this)
                toast("تم حفظ موقع الدوام ✅ (دقة ${l.accuracy.toInt()} م)")
                if (Build.VERSION.SDK_INT >= 29 && !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) requestBackgroundLocation()
                refresh()
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(LocationManager.GPS_PROVIDER, CancellationSignal(), mainExecutor) { l ->
                    onLoc(l ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, object : LocationListener {
                    override fun onLocationChanged(l: Location) = onLoc(l)
                }, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            requestLocation()
        }
    }

    private fun ringoReaderOn(): Boolean {
        val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return on.contains("$packageName/") && on.contains("RingoReader")
    }

    private fun openRingoSetting() {
        AlertDialog.Builder(this)
            .setTitle("قراءة بصمة Ringo")
            .setMessage("في الصفحة الجاية: اختر \"التطبيقات المثبتة\" ثم \"بصمة التواجد\" وفعّله.\n\n" +
                "التطبيق يقرأ شاشة Ringo HR بس، وما يشوف أي تطبيق ثاني.\n\n" +
                "لو طلع لك \"إعداد محظور\": روح معلومات التطبيق ← ⋮ فوق ← \"السماح بالإعدادات المحظورة\" وارجع جرّب.")
            .setPositiveButton("تمام") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNeutralButton("معلومات التطبيق") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .show()
    }

    private fun requestLocation() = requestPermissions(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1
    )

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < 29) { toast("جوالك ما يحتاجها ✅"); return }
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) { requestLocation(); return }
        AlertDialog.Builder(this)
            .setTitle("الموقع طوال الوقت")
            .setMessage("عشان يعرف التطبيق إنك وصلت والجوال في جيبك، اختر في الصفحة الجاية: \"السماح طوال الوقت\"")
            .setPositiveButton("تمام") { _, _ ->
                if (Build.VERSION.SDK_INT >= 30) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                } else {
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
                }
            }
            .show()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
        else toast("الإشعارات مفعلة ✅")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Scheduler.startWatching(this)
        refresh()
    }

    // ------------------------------------------------------------------ Dialogs

    private fun pickTime(current: Int, done: (Int) -> Unit) =
        TimePickerDialog(this, { _, h, m -> done(h * 60 + m) }, current / 60, current % 60, false).show()

    private fun askNumber(title: String, current: Int, done: (Int) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            setSelection(text.length)
        }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("حفظ") { _, _ -> input.text.toString().toIntOrNull()?.let(done) }
            .setNegativeButton("إلغاء", null).show()
    }

    private val dayNames = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

    private fun offDaysLabel() = (1..7).filter { p.offDays.contains(it.toString()) }
        .joinToString("، ") { dayNames[it - 1] }.ifEmpty { "لا يوجد" }

    private fun pickOffDays() {
        val checked = BooleanArray(7) { p.offDays.contains((it + 1).toString()) }
        AlertDialog.Builder(this).setTitle("أيام الإجازة")
            .setMultiChoiceItems(dayNames, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("حفظ") { _, _ ->
                p.offDays = (0..6).filter { checked[it] }.map { (it + 1).toString() }.toSet(); refresh()
            }.show()
    }

    // ------------------------------------------------------------------ View helpers

    private fun has(perm: String) = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.WHITE, 16)
            elevation = dp(1).toFloat()
        }
        c.addView(TextView(this).apply {
            text = title; textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1917"))
            setPadding(0, 0, 0, dp(8))
        })
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        return c
    }

    private fun body(s: String) = TextView(this).apply {
        text = s; textSize = 16f; setLineSpacing(0f, 1.3f)
        setTextColor(Color.parseColor("#292524"))
    }

    private fun small(s: String) = TextView(this).apply {
        text = s; textSize = 13f; setTextColor(Color.parseColor("#78716C"))
        setPadding(0, dp(2), 0, dp(4))
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setOnClickListener { onClick() }
    }

    private fun LinearLayout.addView(b: Button, secondary: Boolean) {
        b.setTextColor(if (b.text.startsWith("إلغاء")) danger else accent)
        b.background = rounded(Color.parseColor(if (secondary) "#F0FDFA" else "#CCFBF1"), 12)
        addView(b, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
    }

    private fun row(label: String, value: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        setOnClickListener { onClick() }
        addView(TextView(context).apply { text = label; textSize = 15f; setTextColor(Color.parseColor("#292524")) },
            LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(context).apply {
            text = value; textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(accent)
            setPadding(dp(12), dp(6), dp(12), dp(6)); background = rounded(Color.parseColor("#F0FDFA"), 8)
        })
    }
}
