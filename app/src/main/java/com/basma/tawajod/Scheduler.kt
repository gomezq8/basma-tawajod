package com.basma.tawajod

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object Scheduler {
    const val CHANNEL_ALARM = "presence_alarm"
    const val CHANNEL_INFO = "presence_info"
    private const val REQ_ALARM = 1
    private const val REQ_PROX = 2

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(CHANNEL_ALARM, "تنبيه بصمة التواجد", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "يرن وقت بصمة التواجد"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
        }
        val info = NotificationChannel(CHANNEL_INFO, "معلومات", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "تأكيد وصولك للدوام ووقت البصمة"
        }
        nm.createNotificationChannel(alarm)
        nm.createNotificationChannel(info)
    }

    enum class Source { GPS, RINGO, MANUAL }

    /**
     * Called when you arrive / punch in.
     *  - MANUAL always wins.
     *  - RINGO (the real punch time) replaces a GPS guess, but not an earlier Ringo punch the same day.
     *  - GPS only counts if nothing was recorded today.
     */
    fun onArrived(ctx: Context, arrivalMillis: Long, source: Source): Long? {
        val p = Prefs(ctx)
        val sameDay = p.lastArrivalMillis != 0L && Rules.isSameDay(p.lastArrivalMillis, arrivalMillis)
        val prev = runCatching { Source.valueOf(p.lastSource) }.getOrDefault(Source.GPS)
        when (source) {
            Source.MANUAL -> Unit
            Source.RINGO -> if (sameDay && prev != Source.GPS) return null
            Source.GPS -> if (sameDay || Rules.isOffDay(arrivalMillis, p)) return null
        }
        p.lastArrivalMillis = arrivalMillis
        p.lastSource = source.name
        val at = Rules.presenceTime(arrivalMillis, p)
        scheduleAlarm(ctx, at)
        Notifier.showArrived(ctx, arrivalMillis, at, source)
        return at
    }

    fun scheduleAlarm(ctx: Context, at: Long) {
        val p = Prefs(ctx)
        p.alarmAtMillis = at
        val am = ctx.getSystemService(AlarmManager::class.java)
        val fire = alarmIntent(ctx)
        val show = PendingIntent.getActivity(
            ctx, 10, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // Fallback: still fires, possibly a minute or two late
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        } else {
            // Alarm-clock alarms are exact even in battery saving mode
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire)
        }
    }

    fun cancelAlarm(ctx: Context) {
        val p = Prefs(ctx)
        p.alarmAtMillis = 0
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))
    }

    private fun alarmIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, REQ_ALARM, Intent(ctx, AlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Starts watching the work location. Returns false if permission is missing. */
    fun startWatching(ctx: Context): Boolean {
        val p = Prefs(ctx)
        if (!p.hasLocation) return false
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        val lm = ctx.getSystemService(LocationManager::class.java)
        val pi = proximityIntent(ctx)
        lm.removeProximityAlert(pi)
        lm.addProximityAlert(p.lat, p.lng, p.radiusM.toFloat(), -1, pi)
        return true
    }

    fun stopWatching(ctx: Context) {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        ctx.getSystemService(LocationManager::class.java).removeProximityAlert(proximityIntent(ctx))
    }

    // Must be MUTABLE so the system can add the "entering" extra
    private fun proximityIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, REQ_PROX, Intent(ctx, ProximityReceiver::class.java),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
