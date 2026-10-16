package com.basma.tawajod

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub for:
 *  1. a newer APK (latest release) → notification with a download link
 *  2. updated Ringo pop-up words (config/ringo.json) → applied silently, no reinstall
 */
object Updater {
    private val REPO = BuildConfig.GITHUB_REPO // e.g. "user/basma-tawajod", filled in by the GitHub build
    private const val ID_UPDATE = 300

    data class Release(val versionCode: Int, val name: String, val apkUrl: String, val notes: String)

    /** Runs on a background thread; [done] is called on that thread. */
    fun checkAsync(ctx: Context, notify: Boolean, done: (Release?) -> Unit = {}) {
        if (REPO.isBlank()) { done(null); return }
        val app = ctx.applicationContext
        Thread {
            runCatching { refreshRingoWords(app) }
            val rel = runCatching { latestRelease() }.getOrNull()
            val newer = rel?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
            if (newer != null && notify) showUpdate(app, newer)
            done(newer)
        }.start()
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("Cache-Control", "no-cache")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun latestRelease(): Release? {
        val j = JSONObject(get("https://api.github.com/repos/$REPO/releases/latest"))
        // Tags look like "v1.0.17"; the last number is the versionCode
        val code = j.getString("tag_name").substringAfterLast('.').toIntOrNull() ?: return null
        val assets = j.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                return Release(code, j.optString("name"), a.getString("browser_download_url"), j.optString("body"))
            }
        }
        return null
    }

    private fun refreshRingoWords(ctx: Context) {
        val j = JSONObject(get("https://raw.githubusercontent.com/$REPO/main/config/ringo.json"))
        val p = Prefs(ctx)
        p.ringoEntryWords = j.getJSONArray("entry").let { a -> (0 until a.length()).map { a.getString(it) } }
        p.ringoIgnoreWords = j.getJSONArray("ignore").let { a -> (0 until a.length()).map { a.getString(it) } }
        j.optString("package_prefix").takeIf { it.isNotBlank() }?.let { p.ringoPrefix = it }
    }

    fun downloadIntent(rel: Release) = Intent(Intent.ACTION_VIEW, Uri.parse(rel.apkUrl))

    private fun showUpdate(ctx: Context, rel: Release) {
        val pi = PendingIntent.getActivity(ctx, 30, downloadIntent(rel),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(ctx, Scheduler.CHANNEL_INFO)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("تحديث جديد لبصمة التواجد")
            .setContentText("اضغط عشان تنزله وتثبته فوق القديم — إعداداتك تبقى")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { ctx.getSystemService(NotificationManager::class.java).notify(ID_UPDATE, n) } catch (_: SecurityException) {}
    }

    /** Checks once a day in the background. */
    fun scheduleDaily(ctx: Context) {
        val pi = PendingIntent.getBroadcast(ctx, 31, Intent(ctx, UpdateReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ctx.getSystemService(AlarmManager::class.java).setInexactRepeating(
            AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR,
            AlarmManager.INTERVAL_DAY, pi)
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        Updater.checkAsync(ctx, notify = true) { pending.finish() }
    }
}
