package com.basma.tawajod

import android.app.Activity
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper

/** Saves where you are right now as the work location. */
object WorkLocation {
    fun saveHere(a: Activity, done: (ok: Boolean, accuracyM: Int) -> Unit) {
        val lm = a.getSystemService(LocationManager::class.java)
        val p = Prefs(a)
        val onLoc: (Location?) -> Unit = { l ->
            if (l == null) done(false, 0)
            else {
                p.lat = l.latitude; p.lng = l.longitude; p.hasLocation = true
                Scheduler.startWatching(a)
                done(true, l.accuracy.toInt())
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(LocationManager.GPS_PROVIDER, CancellationSignal(), a.mainExecutor) { l ->
                    onLoc(l ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, object : LocationListener {
                    override fun onLocationChanged(l: Location) = onLoc(l)
                }, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            done(false, 0)
        }
    }
}
