package com.vinay.dayflow.tracking

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.vinay.dayflow.data.Fmt
import java.time.LocalDate

data class AppUse(val pkg: String, val label: String, val minutes: Int)
data class UsageDay(val screenMin: Int, val unlocks: Int, val apps: List<AppUse>)
data class SleepInfo(val start: Long, val end: Long) {
    val minutes: Int get() = ((end - start) / 60_000L).toInt()
}

/**
 * Reads Android's usage events (needs "Usage access") to work out:
 *  - time spent in each app, total screen time and unlocks for a day
 *  - an estimate of sleep: the longest stretch the screen stayed off between 8 PM and noon
 */
object UsageTracker {
    private const val RESUMED = 1        // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private const val PAUSED = 2         // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    private const val SCREEN_ON = 15     // SCREEN_INTERACTIVE
    private const val SCREEN_OFF = 16    // SCREEN_NON_INTERACTIVE
    private const val UNLOCK = 18        // KEYGUARD_HIDDEN
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    private fun excluded(ctx: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = try {
            @Suppress("DEPRECATION")
            ctx.packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        return (launchers + listOf("com.android.systemui", "android")).toSet()
    }

    private val labelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun label(ctx: Context, pkg: String): String = labelCache.getOrPut(pkg) {
        try {
            val pm = ctx.packageManager
            @Suppress("DEPRECATION")
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun usageFor(ctx: Context, day: LocalDate): UsageDay {
        val empty = UsageDay(0, 0, emptyList())
        if (!hasAccess(ctx)) return empty
        val start = Fmt.startMillis(day)
        val end = minOf(Fmt.startMillis(day.plusDays(1)), System.currentTimeMillis())
        if (end <= start) return empty
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return empty
        val events: UsageEvents? = try { usm.queryEvents(start, end) } catch (e: Exception) { null }
        if (events == null) return empty

        val ev = UsageEvents.Event()
        val open = HashMap<String, Long>()   // "pkg/class" -> resumed at
        val seen = HashSet<String>()
        val totals = HashMap<String, Long>()
        var unlocks = 0

        fun add(pkg: String, ms: Long) {
            if (ms > 0) totals[pkg] = (totals[pkg] ?: 0L) + ms
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val ts = ev.timeStamp
            when (ev.eventType) {
                UNLOCK -> unlocks++
                SCREEN_OFF -> {
                    // Screen went off: close anything still marked as open.
                    for ((key, from) in open) add(key.substringBefore('/'), ts - from)
                    open.clear()
                }
                RESUMED -> {
                    val pkg = ev.packageName ?: continue
                    val key = pkg + "/" + (ev.className ?: "")
                    if (!open.containsKey(key)) open[key] = ts
                    seen.add(key)
                }
                PAUSED -> {
                    val pkg = ev.packageName ?: continue
                    val key = pkg + "/" + (ev.className ?: "")
                    val from = open.remove(key) ?: if (key !in seen) start else null
                    if (from != null) add(pkg, ts - from)
                    seen.add(key)
                }
            }
        }
        for ((key, from) in open) add(key.substringBefore('/'), end - from)

        val skip = excluded(ctx)
        val span = end - start
        val apps = totals
            .filterKeys { it !in skip }
            .map { (pkg, ms) -> AppUse(pkg, label(ctx, pkg), (minOf(ms, span) / MIN).toInt()) }
            .filter { it.minutes >= 1 }
            .sortedByDescending { it.minutes }
        return UsageDay(apps.sumOf { it.minutes }.coerceAtMost((span / MIN).toInt()), unlocks, apps)
    }

    /** Sleep for the night that ends on [day] (8 PM the evening before → noon on [day]). */
    fun sleepFor(ctx: Context, day: LocalDate): SleepInfo? {
        if (Build.VERSION.SDK_INT < 28 || !hasAccess(ctx)) return null
        val ws = Fmt.startMillis(day.minusDays(1)) + 20 * HOUR
        val we = minOf(Fmt.startMillis(day) + 12 * HOUR, System.currentTimeMillis())
        if (we - ws < 3 * HOUR) return null
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return null
        val events: UsageEvents? = try { usm.queryEvents(ws, we) } catch (e: Exception) { null }
        if (events == null) return null

        val ev = UsageEvents.Event()
        var best: SleepInfo? = null
        fun consider(a: Long, b: Long) {
            val cur = best
            if (b - a > (if (cur == null) 0L else cur.end - cur.start)) best = SleepInfo(a, b)
        }

        var offSince: Long? = null
        var pending: LongArray? = null   // [offFrom, onAt] — a finished off-period that might continue
        var firstScreen = true

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val ts = ev.timeStamp
            when (ev.eventType) {
                SCREEN_OFF -> {
                    val p = pending
                    if (p != null && ts - p[1] < 3 * MIN) {
                        // A short glance at the phone (checking the time) doesn't break sleep.
                        offSince = p[0]
                    } else {
                        if (p != null) consider(p[0], p[1])
                        if (offSince == null) offSince = ts
                    }
                    pending = null
                    firstScreen = false
                }
                SCREEN_ON -> {
                    val from = offSince ?: (if (firstScreen) ws else null)
                    if (from != null) {
                        val p = pending
                        if (p != null) consider(p[0], p[1])
                        pending = longArrayOf(from, ts)
                    }
                    offSince = null
                    firstScreen = false
                }
            }
        }
        val p = pending
        if (p != null) consider(p[0], p[1])
        val o = offSince
        if (o != null) consider(o, we)

        val result = best
        return if (result != null && result.minutes >= 90) result else null
    }
}
