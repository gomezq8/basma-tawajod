package com.basma.tawajod

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

/** Home: one thing on screen — when to do the presence punch. */
class MainActivity : BaseActivity() {

    private lateinit var root: LinearLayout
    private var countdown: TextView? = null
    private var release: Updater.Release? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Scheduler.createChannels(this)
        root = column(20, 16)
        setContentView(ScrollView(this).apply { setBackgroundColor(pal.bg); addView(root) })
        Scheduler.startWatching(this)
        Updater.scheduleDaily(this)
    }

    override fun onResume() {
        super.onResume()
        render()
        if (!p.autoAsked || askingNow) askNext()
        handler.post(ticker)
        Updater.checkAsync(this, notify = false) { rel -> runOnUiThread { if (rel != null) { release = rel; render() } } }
    }

    override fun onPause() {
        super.onPause(); handler.removeCallbacks(ticker)
    }

    // ---- Ask for what's missing, one system pop-up after another ----
    private var askStep = 0
    private var askingNow = false

    /** Each pop-up pauses the screen; when it closes, onResume calls this again for the next one. */
    private fun askNext() {
        askingNow = true
        while (true) {
            when (askStep++) {
                0 -> if (!notificationsOn()) { askNotifications(); return }
                1 -> if (!batteryOn()) { askBattery(); return }
                2 -> if (!ringoReaderOn()) { askRingo(); return }
                else -> { askingNow = false; p.autoAsked = true; render(); return }
            }
        }
    }

    private fun askAgain() { askStep = 0; askNext() }

    private val ticker = object : Runnable {
        override fun run() {
            val at = p.alarmAtMillis
            val left = at - System.currentTimeMillis()
            if (countdown != null && left <= 0) render() // time came while looking at the screen
            else countdown?.text = leftText(left)
            handler.postDelayed(this, 1000)
        }
    }

    private fun leftText(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "باقي %d:%02d:%02d".format(h, m, sec) else "باقي %02d:%02d".format(m, sec)
    }

    // ------------------------------------------------------------------

    private fun render() {
        root.removeAllViews()

        // Top bar: name + settings
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(text("بصمة التواجد", 22f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(text("⚙️", 24f).apply {
            setPadding(dp(12), dp(8), dp(4), dp(8))
            contentDescription = "الإعدادات"
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        root.add(bar)

        // Setup not finished → one clear banner
        if (p.autoAsked && !askingNow && essentialsMissing()) {
            val what = when {
                !ringoReaderOn() -> "قراءة بصمة Ringo طافية"
                !notificationsOn() -> "التنبيهات طافية"
                else -> "الجوال ممكن يسكّر التطبيق"
            }
            root.add(banner("⚠️  $what", "صلّحها") { askAgain() }, top = 12)
        }

        // Update available
        release?.let { rel ->
            root.add(banner("✨  فيه تحديث جديد", "نزّله") {
                startActivity(Updater.downloadIntent(rel))
                toast("بعد ما ينزل افتحه واضغط تثبيت، إعداداتك تبقى")
            }, top = 12)
        }

        root.add(hero(), top = 16)
    }

    /** The big card in the middle. States: day off / waiting / counting down / time's up. */
    private fun hero(): View {
        val card = column(20, 28).apply { background = rounded(pal.surface, 24, pal.line); gravity = Gravity.CENTER_HORIZONTAL }
        val now = System.currentTimeMillis()
        val arrival = p.lastArrivalMillis
        val today = arrival != 0L && Rules.isSameDay(arrival, now)
        val at = if (today) Rules.presenceTime(arrival, p) else 0L
        countdown = null

        when {
            !today && Rules.isOffDay(now, p) -> {
                card.add(text("🌴", 56f, center = true))
                card.add(text("اليوم إجازة", 24f, bold = true, center = true), top = 8)
                card.add(text("استانس!", 16f, pal.muted, center = true), top = 4)
                card.add(secondary("داوم اليوم؟ بصمت دخول الحين") { punchedNow() }, h = 52, top = 24)
            }
            !today -> {
                card.add(text("👆", 56f, center = true))
                card.add(text("ابصم دخول في Ringo", 24f, bold = true, center = true), top = 8)
                card.add(text(
                    if (ringoReaderOn()) "وبنحسب لك وقت التواجد على طول"
                    else "وبعدها اضغط الزر تحت", 16f, pal.muted, center = true), top = 4)
                card.add(primary("بصمت دخول الحين") { punchedNow() }, h = 56, top = 24)
                card.add(link("بصمت قبل؟ اختر الوقت") { pickTime() })
            }
            at > now -> {
                card.add(text("بصمة التواجد الساعة", 16f, pal.muted, center = true))
                card.add(text(Rules.fmtMillis(at), 64f, pal.accent, bold = true, center = true).apply {
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                })
                countdown = text(leftText(at - now), 18f, center = true).also { card.add(it) }
                card.add(text("🔔 بنرن لك وقتها", 15f, pal.muted, center = true), top = 16)
                card.add(divider(), h = 1, top = 20)
                card.add(text("دخلت ${Rules.fmtMillis(arrival)} · ${sourceLabel()}", 15f, pal.muted, center = true), top = 12)
                card.add(link("الوقت غلط؟ عدّله", pal.accent) { pickTime() })
            }
            else -> {
                card.add(text("✅", 56f, center = true))
                card.add(text("وقت التواجد ${Rules.fmtMillis(at)}", 24f, bold = true, center = true), top = 8)
                card.add(text("إذا بصمت، باقي عليك بس الخروج 👋", 16f, pal.muted, center = true), top = 4)
                card.add(link("الوقت غلط؟ عدّله", pal.accent) { pickTime() }, top = 8)
            }
        }
        return card
    }

    private fun sourceLabel() = when (p.lastSource) {
        "RINGO" -> "من Ringo"
        "MANUAL" -> "أدخلته أنت"
        else -> "من الموقع"
    }

    private fun banner(msg: String, action: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(10), dp(10))
            background = rounded(pal.warnSoft, 16)
            setOnClickListener { onClick() }
        }
        row.addView(text(msg, 15f, pal.warn, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(action, 15f, pal.onAccent, bold = true).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8)); background = rounded(pal.accent, 12)
            setOnClickListener { onClick() }
        })
        return row
    }

    private fun divider() = View(this).apply { setBackgroundColor(pal.line) }

    // ------------------------------------------------------------------

    private fun punchedNow() = arrived(System.currentTimeMillis())

    private fun pickTime() {
        val c = Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0)
            arrived(c.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
    }

    private fun arrived(millis: Long) {
        val at = Scheduler.onArrived(this, millis, Scheduler.Source.MANUAL) ?: return
        toast("تمام! بنرن لك الساعة ${Rules.fmtMillis(at)}")
        render()
    }
}
