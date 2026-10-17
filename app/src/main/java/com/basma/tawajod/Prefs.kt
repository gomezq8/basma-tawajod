package com.basma.tawajod

import android.content.Context

/** All settings and state, stored on the phone. Times are minutes since midnight. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("basma", Context.MODE_PRIVATE)

    var setupDone: Boolean
        get() = sp.getBoolean("setupDone", false)
        set(v) = sp.edit().putBoolean("setupDone", v).apply()

    // ---- Work location ----
    var hasLocation: Boolean
        get() = sp.getBoolean("hasLoc", false)
        set(v) = sp.edit().putBoolean("hasLoc", v).apply()
    var lat: Double
        get() = java.lang.Double.longBitsToDouble(sp.getLong("lat", 0))
        set(v) = sp.edit().putLong("lat", java.lang.Double.doubleToRawLongBits(v)).apply()
    var lng: Double
        get() = java.lang.Double.longBitsToDouble(sp.getLong("lng", 0))
        set(v) = sp.edit().putLong("lng", java.lang.Double.doubleToRawLongBits(v)).apply()
    var radiusM: Int
        get() = sp.getInt("radius", 150)
        set(v) = sp.edit().putInt("radius", v).apply()

    // ---- Rules ----
    /** Punching before this time counts as this time. Default 07:00 */
    var earliestStart: Int
        get() = sp.getInt("earliest", 7 * 60)
        set(v) = sp.edit().putInt("earliest", v).apply()
    /** Punching between graceFrom and graceTo counts as graceFrom. Default 08:00 – 08:30 */
    var graceEnabled: Boolean
        get() = sp.getBoolean("graceOn", true)
        set(v) = sp.edit().putBoolean("graceOn", v).apply()
    var graceFrom: Int
        get() = sp.getInt("graceFrom", 8 * 60)
        set(v) = sp.edit().putInt("graceFrom", v).apply()
    var graceTo: Int
        get() = sp.getInt("graceTo", 8 * 60 + 30)
        set(v) = sp.edit().putInt("graceTo", v).apply()
    /** Official gap after entry: 2h 1m */
    var gapMinutes: Int
        get() = sp.getInt("gap", 121)
        set(v) = sp.edit().putInt("gap", v).apply()
    /** Extra safety margin. 4 → total 2h 5m */
    var marginMinutes: Int
        get() = sp.getInt("margin", 4)
        set(v) = sp.edit().putInt("margin", v).apply()
    /** Days off (Calendar.DAY_OF_WEEK values), default Friday + Saturday */
    var offDays: Set<String>
        get() = sp.getStringSet("offDays", setOf("6", "7"))!!
        set(v) = sp.edit().putStringSet("offDays", v).apply()

    // ---- Ringo pop-up words (defaults; refreshed from GitHub config/ringo.json) ----
    var ringoEntryWords: List<String>
        get() = sp.getString("rEntry", null)?.split("\n")?.filter { it.isNotBlank() } ?: DEFAULT_ENTRY
        set(v) = sp.edit().putString("rEntry", v.joinToString("\n")).apply()
    var ringoIgnoreWords: List<String>
        get() = sp.getString("rIgnore", null)?.split("\n")?.filter { it.isNotBlank() } ?: DEFAULT_IGNORE
        set(v) = sp.edit().putString("rIgnore", v.joinToString("\n")).apply()
    var ringoPrefix: String
        get() = sp.getString("rPrefix", "com.ringo")!!
        set(v) = sp.edit().putString("rPrefix", v).apply()

    companion object {
        val DEFAULT_ENTRY = listOf(
            "تم تسجيل الدخول", "تم تسجيل الحضور", "تم الدخول",
            "check in successful", "checked in successfully", "clocked in successfully"
        )
        val DEFAULT_IGNORE = listOf(
            "خروج", "انصراف", "تواجد", "check out", "checked out", "clock out", "presence",
            "كلمة المرور", "password"
        )
    }

    // ---- Today's state ----
    var lastArrivalMillis: Long
        get() = sp.getLong("arrival", 0)
        set(v) = sp.edit().putLong("arrival", v).apply()
    /** GPS, RINGO or MANUAL */
    var lastSource: String
        get() = sp.getString("source", "GPS")!!
        set(v) = sp.edit().putString("source", v).apply()
    var alarmAtMillis: Long
        get() = sp.getLong("alarmAt", 0)
        set(v) = sp.edit().putLong("alarmAt", v).apply()
}
