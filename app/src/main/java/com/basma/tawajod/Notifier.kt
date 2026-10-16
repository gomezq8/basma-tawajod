package com.basma.tawajod

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifier {
    private const val ID_INFO = 100
    private const val ID_ALARM = 200

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun showArrived(ctx: Context, arrival: Long, at: Long, source: Scheduler.Source) {
        val title = when (source) {
            Scheduler.Source.RINGO -> "سجلنا بصمة دخولك من Ringo ${Rules.fmtMillis(arrival)}"
            Scheduler.Source.GPS -> "وصلت الدوام ${Rules.fmtMillis(arrival)}"
            Scheduler.Source.MANUAL -> "دخول ${Rules.fmtMillis(arrival)}"
        }
        val n = Notification.Builder(ctx, Scheduler.CHANNEL_INFO)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText("بصمة التواجد الساعة ${Rules.fmtMillis(at)} — بنبهك وقتها")
            .setStyle(Notification.BigTextStyle().bigText(
                "بصمة التواجد الساعة ${Rules.fmtMillis(at)} — بنبهك وقتها.\nالوقت غلط؟ افتح التطبيق وعدّله."))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        post(ctx, ID_INFO, n)
    }

    fun showPunchNow(ctx: Context) {
        val n = Notification.Builder(ctx, Scheduler.CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ حان وقت بصمة التواجد")
            .setContentText("روح ابصم التواجد الحين")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(openApp(ctx))
            .setFullScreenIntent(openApp(ctx), true)
            .setAutoCancel(true)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT // keeps ringing until you open it
        post(ctx, ID_ALARM, n)
    }

    private fun post(ctx: Context, id: Int, n: Notification) {
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: SecurityException) { /* notification permission not granted */ }
    }
}
