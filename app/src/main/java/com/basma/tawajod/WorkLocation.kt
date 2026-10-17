package com.basma.tawajod

import android.app.Activity
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

object WorkLocation {

    /** Where the phone is right now (null if GPS is off or not allowed). */
    fun current(a: Activity, done: (Location?) -> Unit) {
        val lm = a.getSystemService(LocationManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(LocationManager.GPS_PROVIDER, CancellationSignal(), a.mainExecutor) { l ->
                    done(l ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, object : LocationListener {
                    override fun onLocationChanged(l: Location) = done(l)
                }, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            done(null)
        }
    }

    // ------------------------------------------------------------------
    // Google Maps links → coordinates. Runs network calls: call off the main thread.

    // Most exact first: "!3d..!4d.." is the dropped pin itself; "@lat,lng" is the map's centre.
    private val strict = listOf(
        Regex("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)"),
        Regex("[?&](?:q|query|ll|center|destination|daddr)=(-?\\d+\\.\\d+),\\s*\\+?(-?\\d+\\.\\d+)"),
        Regex("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)"),
    )
    // Plain "29.3759, 47.9774" typed or pasted by the user
    private val plain = Regex("(-?\\d{1,2}\\.\\d{3,})\\s*,\\s*(-?\\d{1,3}\\.\\d{3,})")

    private fun find(s: String, loose: Boolean): Pair<Double, Double>? {
        val d = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
        for (r in strict) r.find(d)?.let { return it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
        if (loose) plain.find(d)?.let { return it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
        return null
    }

    /** Accepts a shared Google Maps text/link, a pasted link, or "lat, lng". */
    fun fromText(text: String): Pair<Double, Double>? {
        find(text, loose = true)?.let { return it }
        var url = Regex("https?://\\S+").find(text)?.value ?: return null
        // Short links (maps.app.goo.gl) redirect to the full link that has the coordinates.
        repeat(6) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            val code = c.responseCode
            val next = c.getHeaderField("Location")
            if (code in 300..399 && next != null) {
                c.disconnect()
                url = URL(URL(url), next).toString()
                find(url, loose = false)?.let { return it }
            } else {
                val body = runCatching { c.inputStream.bufferedReader().use { it.readText().take(300_000) } }.getOrDefault("")
                c.disconnect()
                return find(url, false) ?: find(body, false)
            }
        }
        return find(url, false)
    }

    /** Search a place by name (OpenStreetMap). Returns the first match. */
    fun search(query: String): Pair<Double, Double>? {
        val q = URLEncoder.encode(query, "UTF-8")
        val c = URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&accept-language=ar&q=$q")
            .openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "BasmaTawajod/1.0 (github.com/gomezq8/basma-tawajod)")
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        c.disconnect()
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return o.getString("lat").toDouble() to o.getString("lon").toDouble()
    }
}
