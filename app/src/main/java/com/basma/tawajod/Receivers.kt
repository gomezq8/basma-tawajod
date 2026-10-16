package com.basma.tawajod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager

/** The phone entered the work area. */
class ProximityReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
        if (entering) Scheduler.onArrived(ctx, System.currentTimeMillis(), Scheduler.Source.GPS)
    }
}

/** It's time for the presence punch. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Prefs(ctx).alarmAtMillis = 0
        Notifier.showPunchNow(ctx)
    }
}

/** After a restart: re-arm the location watch and any alarm still due today. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.createChannels(ctx)
        Scheduler.startWatching(ctx)
        Updater.scheduleDaily(ctx)
        val at = Prefs(ctx).alarmAtMillis
        val now = System.currentTimeMillis()
        when {
            at == 0L -> Unit
            at > now -> Scheduler.scheduleAlarm(ctx, at)
            now - at < 30 * 60_000L -> Notifier.showPunchNow(ctx) // missed during restart
            else -> Prefs(ctx).alarmAtMillis = 0
        }
    }
}
