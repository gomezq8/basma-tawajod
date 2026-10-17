package com.basma.tawajod

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** First-run walkthrough: one permission per screen, one button each. */
class SetupActivity : BaseActivity() {

    private class Step(
        val icon: String,
        val title: String,
        val body: String,
        val button: String,
        val optional: Boolean = false,
        val isDone: () -> Boolean = { false },
        val action: () -> Unit,
    )

    private lateinit var steps: List<Step>
    private var index = 0
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Scheduler.createChannels(this)
        steps = buildSteps()
        index = savedInstanceState?.getInt("i") ?: if (p.setupDone) 1 else 0
        root = column(24, 24).apply { setBackgroundColor(pal.bg) }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(pal.bg)
            addView(root)
        })
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out); out.putInt("i", index)
    }

    override fun onResume() {
        super.onResume()
        // Came back from a settings page: if that step is now done, move on.
        skipDone()
        render()
    }

    private fun skipDone() {
        while (index in 1 until steps.size - 1 && steps[index].isDone()) index++
    }

    private fun next() {
        index++; skipDone(); render()
    }

    private fun buildSteps(): List<Step> {
        val list = mutableListOf<Step>()
        list += Step("⏰", "أهلاً فيك",
            "التطبيق يحسب لك وقت بصمة التواجد وينبهك وقتها.\n\nخلنا نجهزه، ما ياخذ دقيقة.",
            "يلا نبدأ") { next() }

        if (Build.VERSION.SDK_INT >= 33) list += Step("🔔", "التنبيهات",
            "عشان نقدر نرن لك وقت بصمة التواجد.",
            "اسمح بالتنبيهات", isDone = { notificationsOn() }) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        list += Step("👆", "قراءة بصمة Ringo",
            "أول ما تبصم دخول في Ringo، ناخذ الوقت بالضبط ونحسب التواجد لحالنا.\n\n" +
                "في الصفحة الجاية اضغط «التطبيقات المثبتة» ← «بصمة التواجد» ← شغّله.",
            "افتح الإعدادات", isDone = { ringoReaderOn() }) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        list += Step("🔋", "خلّه شغّال دايم",
            "عشان الجوال ما يسكّر التطبيق ويفوتك التنبيه.\n\nاضغط «سماح» في الرسالة الجاية.",
            "سماح", isDone = { batteryOn() }) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        list += Step("📍", "الموقع (احتياط)",
            "لو ما قدرنا نقرأ بصمة Ringo، ننبهك أول ما توصل الدوام.\n\nاختياري، تقدر تتخطاه.",
            "اسمح بالموقع", optional = true, isDone = { locationOn() }) {
            if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2)
            } else if (Build.VERSION.SDK_INT >= 30) {
                toast("اختر «الموقع» ← «السماح طوال الوقت»")
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } else if (Build.VERSION.SDK_INT == 29) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 3)
            }
        }

        list += Step("✅", "جاهز!",
            "من الحين: ابصم دخول في Ringo عادي، وإحنا نحسب ونرن لك وقت التواجد.",
            "ابدأ") { finishSetup() }
        return list
    }

    private fun finishSetup() {
        p.setupDone = true
        Scheduler.startWatching(this)
        Updater.scheduleDaily(this)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        skipDone(); render()
    }

    private fun render() {
        val s = steps[index.coerceIn(0, steps.size - 1)]
        root.removeAllViews()

        // progress dots (welcome and done screens don't count)
        val dots = LinearLayout(this).apply { gravity = Gravity.CENTER }
        for (i in 1 until steps.size - 1) {
            dots.addView(View(this).apply {
                background = rounded(if (i <= index) pal.accent else pal.line, 8)
            }, LinearLayout.LayoutParams(dp(if (i == index) 22 else 8), dp(8)).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        root.add(dots, top = 8)
        root.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f)) // push content to middle

        root.add(text(s.icon, 64f, center = true))
        root.add(text(s.title, 26f, bold = true, center = true), top = 16)
        if (s.optional) root.add(text("اختياري", 13f, pal.muted, center = true), top = 4)
        root.add(text(s.body, 17f, pal.muted, center = true), top = 14)

        root.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        if (s.title == "قراءة بصمة Ringo") root.add(link("طلع لك «إعداد محظور»؟ اضغط هنا", pal.accent) { restrictedHelp() })
        root.add(primary(s.button) { s.action() }, h = 56, top = 8)
        if (index in 1 until steps.size - 1) {
            root.add(link(if (s.optional) "تخطّي" else "بعدين") { next() })
        }
    }

    private fun restrictedHelp() {
        val box = column(4, 8)
        box.add(text("أندرويد يقفل هالإعداد للتطبيقات اللي من برا المتجر. افتحه كذا:", 16f))
        box.add(text("١. اضغط «فتح معلومات التطبيق» تحت\n٢. اضغط ⋮ فوق\n٣. «السماح بالإعدادات المحظورة»\n٤. ارجع هنا واضغط «افتح الإعدادات»", 16f, pal.muted), top = 10)
        android.app.AlertDialog.Builder(this)
            .setView(box)
            .setPositiveButton("فتح معلومات التطبيق") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }
}
