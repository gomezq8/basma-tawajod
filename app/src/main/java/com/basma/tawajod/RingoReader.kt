package com.basma.tawajod

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches Ringo HR's screen only. When the "check-in done" pop-up appears,
 * takes that moment as the entry punch time.
 */
class RingoReader : AccessibilityService() {

    companion object {
        fun isEntryPopup(text: String, entry: List<String>, ignore: List<String>): Boolean {
            val t = text.lowercase()
            return entry.any { t.contains(it.lowercase()) } && ignore.none { t.contains(it.lowercase()) }
        }
    }

    private var lastHit = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val p = Prefs(this)
        if (!pkg.startsWith(p.ringoPrefix)) return
        val now = System.currentTimeMillis()
        if (now - lastHit < 60_000) return // same pop-up firing several events

        // Check each piece of text on its own: Ringo's main screen also shows
        // "تواجد" / "خروج" buttons, which must not cancel the pop-up's message.
        val pieces = mutableListOf<String>()
        event.text?.forEach { pieces.add(it.toString()) }
        rootInActiveWindow?.let { collect(it, pieces, 0) }
        val entry = p.ringoEntryWords; val ignore = p.ringoIgnoreWords
        if (pieces.any { isEntryPopup(it, entry, ignore) }) {
            lastHit = now
            Scheduler.onArrived(this, now, Scheduler.Source.RINGO)
        }
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 25) return
        node.text?.let { out.add(it.toString()) }
        node.contentDescription?.let { out.add(it.toString()) }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, out, depth + 1) }
    }

    override fun onInterrupt() {}
}
