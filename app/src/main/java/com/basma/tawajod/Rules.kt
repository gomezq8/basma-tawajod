package com.basma.tawajod

import java.util.Calendar

object Rules {

    /**
     * The time the system counts your entry from (minutes since midnight):
     *  - before 07:00            → 07:00
     *  - between 08:00 and 08:30 → 08:00
     *  - otherwise               → the actual time
     */
    fun countedEntry(arrivalMin: Int, p: Prefs): Int = when {
        arrivalMin < p.earliestStart -> p.earliestStart
        p.graceEnabled && arrivalMin in p.graceFrom..p.graceTo -> p.graceFrom
        else -> arrivalMin
    }

    /** Presence-punch time (minutes since midnight) = counted entry + 2:01 + margin. */
    fun presenceMinute(arrivalMin: Int, p: Prefs): Int =
        countedEntry(arrivalMin, p) + p.gapMinutes + p.marginMinutes

    /** Presence-punch time as epoch millis, for an arrival at [arrivalMillis]. */
    fun presenceTime(arrivalMillis: Long, p: Prefs): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = arrivalMillis }
        val target = presenceMinute(minuteOf(arrivalMillis), p)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MINUTE, target)
        return cal.timeInMillis
    }

    fun minuteOf(millis: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun isOffDay(millis: Long, p: Prefs): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return p.offDays.contains(c.get(Calendar.DAY_OF_WEEK).toString())
    }

    fun fmt(min: Int): String = String.format("%02d:%02d", (min / 60) % 24, min % 60)

    fun fmtMillis(millis: Long): String = fmt(minuteOf(millis))
}
