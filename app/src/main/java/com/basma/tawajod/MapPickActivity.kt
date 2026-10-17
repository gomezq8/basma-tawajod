package com.basma.tawajod

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

/**
 * Pick the work location on a map: move the map under the pin, set the circle size, save.
 * Also opens when a place is shared from Google Maps.
 */
class MapPickActivity : BaseActivity() {

    private lateinit var map: MapView
    private lateinit var circle: Polygon
    private lateinit var radiusLabel: TextView
    private lateinit var search: EditText
    private var radius = 150
    private var savedAndAsking = false
    private var askedBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osm", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        radius = p.radiusM.coerceIn(50, 500)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pal.bg) }

        // ---- top: back + search
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        top.addView(text("→", 24f).apply {
            setPadding(dp(4), dp(4), dp(12), dp(4)); contentDescription = "رجوع"
            setOnClickListener { finish() }
        })
        search = EditText(this).apply {
            hint = "ابحث، أو الصق رابط قوقل ماب"
            setHintTextColor(pal.muted); setTextColor(pal.fg); textSize = 15f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = rounded(pal.surface, 14, pal.line)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnEditorActionListener { v, _, _ -> runSearch(v.text.toString()); true }
        }
        top.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)

        // ---- middle: map with a fixed pin in the centre
        val frame = FrameLayout(this)
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        }
        frame.addView(map, FrameLayout.LayoutParams(-1, -1))
        frame.addView(text("📍", 40f).apply {
            translationY = -dp(24).toFloat() // the pin's tip sits on the centre
        }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        frame.addView(text("◎  موقعي", 15f, pal.accent, bold = true).apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(pal.surface, 22, pal.line)
            elevation = dp(3).toFloat()
            setOnClickListener { goToMe() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(14), 0, dp(14), dp(14)) })
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))

        // ---- bottom: radius + save
        val bottom = column(20, 16).apply { setBackgroundColor(pal.surface) }
        bottom.add(text("حرّك الخريطة لين يصير الدبوس على مكان الدوام", 14f, pal.muted, center = true))
        radiusLabel = text("", 16f, bold = true, center = true)
        bottom.add(radiusLabel, top = 12)
        bottom.add(SeekBar(this).apply {
            max = (500 - 50) / 10
            progress = (radius - 50) / 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                    radius = 50 + v * 10; drawCircle()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }, top = 4)
        bottom.add(primary("احفظ موقع الدوام") { save() }, h = 54, top = 12)
        root.addView(bottom)

        setContentView(root)

        // circle overlay that follows the map centre
        circle = Polygon(map).apply {
            fillPaint.color = Color.argb(45, Color.red(pal.accent), Color.green(pal.accent), Color.blue(pal.accent))
            outlinePaint.color = pal.accent
            outlinePaint.strokeWidth = dp(2).toFloat()
            infoWindow = null
            setOnClickListener { _, _, _ -> false }
        }
        map.overlays.add(circle)
        map.addMapListener(object : MapListener {
            override fun onScroll(e: ScrollEvent?): Boolean { drawCircle(); return false }
            override fun onZoom(e: ZoomEvent?): Boolean { drawCircle(); return false }
        })

        if (p.hasLocation) moveTo(p.lat, p.lng, 17.0) else moveTo(29.3759, 47.9774, 12.0) // Kuwait City

        // Opened from Google Maps "Share"
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { runSearch(it) }
        }
    }

    private fun moveTo(lat: Double, lng: Double, zoom: Double) {
        map.controller.setZoom(zoom)
        map.controller.setCenter(GeoPoint(lat, lng))
        map.post { drawCircle() }
    }

    private fun center(): GeoPoint = map.mapCenter.let { GeoPoint(it.latitude, it.longitude) }

    private fun drawCircle() {
        radiusLabel.text = "منطقة الدوام: $radius متر"
        circle.points = Polygon.pointsAsCircle(center(), radius.toDouble())
        map.invalidate()
    }

    // ---- search / pasted link / shared link ----
    private fun runSearch(q: String) {
        if (q.isBlank()) return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken, 0)
        toast("جاري البحث…")
        Thread {
            val looksLikeLink = q.contains("http") || Regex("\\d+\\.\\d+\\s*,\\s*\\d+\\.\\d+").containsMatchIn(q)
            val hit = runCatching { if (looksLikeLink) WorkLocation.fromText(q) else WorkLocation.search(q) }.getOrNull()
            runOnUiThread {
                if (hit == null) toast(if (looksLikeLink) "ما قدرنا نقرأ الرابط، جرب تبحث بالاسم" else "ما لقينا المكان، جرب اسم ثاني")
                else { moveTo(hit.first, hit.second, 18.0); search.setText("") }
            }
        }.start()
    }

    private fun goToMe() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 21)
            return
        }
        toast("جاري تحديد موقعك…")
        WorkLocation.current(this) { l ->
            runOnUiThread {
                if (l == null) toast("شغّل الـ GPS وجرب مرة ثانية") else moveTo(l.latitude, l.longitude, 18.0)
            }
        }
    }

    // ---- save, then make sure location is allowed "all the time" ----
    private fun save() {
        val c = center()
        p.lat = c.latitude; p.lng = c.longitude; p.radiusM = radius; p.hasLocation = true
        Scheduler.startWatching(this)
        if (locationOn()) { done(); return }
        savedAndAsking = true
        // Already allowed "while using"? Then this call goes straight to the "all the time" page.
        if (has(Manifest.permission.ACCESS_FINE_LOCATION)) askedBackground = true
        askLocation()
    }

    private fun done() {
        Scheduler.startWatching(this)
        toast(if (locationOn()) "تم حفظ موقع الدوام ✅"
              else "انحفظ الموقع، بس لازم تسمح بالموقع «طوال الوقت» عشان ينبهك")
        finish()
    }

    override fun onResume() {
        super.onResume(); map.onResume()
        if (!savedAndAsking) return
        when {
            locationOn() -> done()
            has(Manifest.permission.ACCESS_FINE_LOCATION) && !askedBackground && Build.VERSION.SDK_INT >= 29 -> {
                askedBackground = true; askLocation()
            }
            else -> done()
        }
    }

    override fun onPause() {
        super.onPause(); map.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 21 && has(Manifest.permission.ACCESS_FINE_LOCATION)) goToMe()
    }
}
