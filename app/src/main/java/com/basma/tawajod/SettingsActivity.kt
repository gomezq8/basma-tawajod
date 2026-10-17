package com.basma.tawajod

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch

/** Everything a normal user doesn't need every day. */
class SettingsActivity : BaseActivity() {

    private lateinit var root: LinearLayout
    private var updateNote = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = column(20, 16)
        setContentView(ScrollView(this).apply { setBackgroundColor(pal.bg); addView(root) })
    }

    override fun onResume() {
        super.onResume(); render()
    }

    private fun render() {
        root.removeAllViews()

        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(text("→", 24f).apply {
            setPadding(dp(4), dp(8), dp(12), dp(8)); contentDescription = "رجوع"
            setOnClickListener { finish() }
        })
        bar.addView(text("الإعدادات", 22f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        root.add(bar)

        // --- Rules
        section("أوقات الدوام")
        group {
            row("إذا بصمت قبل", Rules.fmt(p.earliestStart), "تنحسب نفس الوقت") {
                pickTime(p.earliestStart) { p.earliestStart = it }
            }
            switchRow("فترة السماح", p.graceEnabled) { p.graceEnabled = it }
            if (p.graceEnabled) {
                row("من", Rules.fmt(p.graceFrom), "تنحسب من هالوقت") { pickTime(p.graceFrom) { p.graceFrom = it } }
                row("إلى", Rules.fmt(p.graceTo), null) { pickTime(p.graceTo) { p.graceTo = it } }
            }
            row("التواجد بعد الدخول", duration(p.gapMinutes), null) {
                askNumber("كم دقيقة بعد الدخول؟ (ساعتين ودقيقة = 121)", p.gapMinutes) { p.gapMinutes = it.coerceIn(1, 600) }
            }
            row("دقايق احتياط", "${p.marginMinutes} د", "ننبهك بعد الوقت بشوي") {
                askNumber("دقايق احتياط", p.marginMinutes) { p.marginMinutes = it.coerceIn(0, 60) }
            }
            row("أيام الإجازة", offDaysLabel(), null) { pickOffDays() }
        }

        // examples
        val ex = listOf(6 * 60 + 45, 7 * 60 + 40, 8 * 60 + 15, 8 * 60 + 45)
        root.add(text(
            "أمثلة: " + ex.joinToString("   ·   ") { "${Rules.fmt(it)} ← ${Rules.fmt(Rules.presenceMinute(it, p))}" },
            13f, pal.muted), top = 8)

        // --- Location
        section("موقع الدوام (احتياط)")
        group {
            val locState = when {
                !has(Manifest.permission.ACCESS_FINE_LOCATION) -> "مو مسموح"
                p.hasLocation -> "محفوظ ✅"
                else -> "مو محفوظ"
            }
            row("أنا بالدوام الحين، احفظ الموقع", locState, null) { saveLocation() }
            row("حجم المنطقة", "${p.radiusM} م", null) {
                askNumber("نطاق الموقع بالمتر", p.radiusM) { p.radiusM = it.coerceIn(50, 2000); Scheduler.startWatching(this) }
            }
        }

        // --- Setup
        section("التجهيز")
        group {
            row("قراءة بصمة Ringo", if (ringoReaderOn()) "شغّال ✅" else "طافي", null) { openSetup() }
            row("التنبيهات", if (notificationsOn()) "شغّال ✅" else "طافي", null) { openSetup() }
            row("البطارية", if (batteryOn()) "مسموح ✅" else "مقيّد", null) { openSetup() }
            row("الموقع", if (locationOn()) "مسموح ✅" else "طافي", null) { openSetup() }
        }

        // --- Updates
        section("التحديثات")
        group {
            row("الإصدار ${BuildConfig.VERSION_NAME}", "تحقق", updateNote.ifEmpty { null }) { checkUpdate() }
        }
    }

    // ---- building blocks ----

    private fun section(title: String) = root.add(text(title, 14f, pal.muted, bold = true).apply {
        setPadding(dp(4), 0, dp(4), 0)
    }, top = 24)

    private var current: LinearLayout? = null

    private fun group(build: () -> Unit) {
        val g = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(pal.surface, 18, pal.line)
        }
        current = g; build(); current = null
        // remove the divider after the last row
        if (g.childCount > 0) g.removeViewAt(g.childCount - 1)
        root.add(g, top = 8)
    }

    private fun row(label: String, value: String, hint: String?, onClick: () -> Unit) {
        val r = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onClick() }
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text(label, 16f))
        if (hint != null) left.addView(text(hint, 13f, pal.muted))
        r.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(text(value, 16f, pal.accent, bold = true))
        addToGroup(r)
    }

    private fun switchRow(label: String, on: Boolean, change: (Boolean) -> Unit) {
        val r = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        r.addView(text(label, 16f), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(Switch(this).apply {
            isChecked = on
            setOnCheckedChangeListener { _, c -> change(c); render() }
        })
        addToGroup(r)
    }

    private fun addToGroup(v: View) {
        val g = current ?: return
        g.addView(v)
        g.addView(View(this).apply { setBackgroundColor(pal.line) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(16); marginEnd = dp(16) })
    }

    private fun duration(min: Int) = if (min >= 60) "${min / 60}س ${min % 60}د" else "$min د"

    // ---- actions ----

    private fun openSetup() = startActivity(Intent(this, SetupActivity::class.java))

    private fun pickTime(current: Int, done: (Int) -> Unit) =
        TimePickerDialog(this, { _, h, m -> done(h * 60 + m); render() }, current / 60, current % 60, false).show()

    private fun askNumber(title: String, current: Int, done: (Int) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString()); setSelection(text.length)
        }
        val box = column(20, 8).apply { addView(input) }
        AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton("حفظ") { _, _ -> input.text.toString().toIntOrNull()?.let(done); render() }
            .setNegativeButton("إلغاء", null).show()
    }

    private val dayNames = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

    private fun offDaysLabel() = (1..7).filter { p.offDays.contains(it.toString()) }
        .joinToString("، ") { dayNames[it - 1] }.ifEmpty { "ولا يوم" }

    private fun pickOffDays() {
        val checked = BooleanArray(7) { p.offDays.contains((it + 1).toString()) }
        AlertDialog.Builder(this).setTitle("أيام الإجازة")
            .setMultiChoiceItems(dayNames, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("حفظ") { _, _ ->
                p.offDays = (0..6).filter { checked[it] }.map { (it + 1).toString() }.toSet(); render()
            }.show()
    }

    private fun saveLocation() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) { openSetup(); return }
        toast("جاري تحديد موقعك…")
        WorkLocation.saveHere(this) { ok, _ ->
            runOnUiThread {
                toast(if (ok) "تم حفظ موقع الدوام ✅" else "ما قدرنا نحدد موقعك، شغّل الـ GPS وجرب")
                render()
            }
        }
    }

    private fun checkUpdate() {
        updateNote = "جاري التحقق…"; render()
        Updater.checkAsync(this, notify = false) { rel ->
            runOnUiThread {
                if (rel != null) {
                    updateNote = ""
                    startActivity(Updater.downloadIntent(rel))
                    toast("بعد ما ينزل افتحه واضغط تثبيت، إعداداتك تبقى")
                } else updateNote = "عندك آخر نسخة ✅"
                render()
            }
        }
    }
}
