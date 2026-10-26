package com.vinay.dayflow.data

import android.content.Context
import com.vinay.dayflow.tracking.AppUse
import com.vinay.dayflow.tracking.SleepInfo
import com.vinay.dayflow.tracking.StepStore
import com.vinay.dayflow.tracking.UsageTracker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DaySummary(
    val date: LocalDate,
    val isToday: Boolean,
    val tasks: List<Task>,
    val steps: Int?,
    val stepGoal: Int,
    val sleep: SleepInfo?,
    val sleepMin: Int?,
    val sleepGoal: Int,
    val screenMin: Int?,
    val screenLimit: Int,
    val unlocks: Int?,
    val apps: List<AppUse>,
    val score: Int,
    val tips: List<String>,
    val log: DayLog?,
) {
    val done: List<Task> get() = tasks.filter { it.done }
    val missed: List<Task> get() = tasks.filter { !it.done }
    val completion: Int get() = if (tasks.isEmpty()) 0 else done.size * 100 / tasks.size
    val hasData: Boolean get() = tasks.isNotEmpty() || steps != null || sleepMin != null || screenMin != null
}

/** Builds the "what I did / didn't do / how to improve" summary for any day. */
object SummaryEngine {

    fun build(ctx: Context, date: LocalDate): DaySummary {
        val db = Db.get(ctx)
        val p = Prefs(ctx)
        val today = LocalDate.now()
        val isToday = date == today
        val key = date.toString()
        val tasks = db.tasksFor(date)
        val stored = db.getLog(key)

        var steps = stored?.steps?.takeIf { it >= 0 }
        if (isToday) {
            val live = StepStore.todayOrNull(ctx)
            if (live != null) steps = live
        }
        var screen = stored?.screenMin?.takeIf { it >= 0 }
        var unlocks = stored?.unlocks?.takeIf { it >= 0 }
        var sleepMin = stored?.sleepMin?.takeIf { it >= 0 }
        var sleep: SleepInfo? = null
        var apps = emptyList<AppUse>()

        if (!date.isAfter(today) && UsageTracker.hasAccess(ctx)) {
            val u = UsageTracker.usageFor(ctx, date)
            // Android only keeps detailed events for ~a week; keep stored numbers if they're gone.
            if (u.apps.isNotEmpty() || isToday) {
                screen = u.screenMin
                unlocks = u.unlocks
                apps = u.apps
                db.saveMetrics(key, screenMin = u.screenMin, unlocks = u.unlocks)
            }
            val s = UsageTracker.sleepFor(ctx, date)
            if (s != null) {
                sleep = s
                sleepMin = s.minutes
                db.saveMetrics(key, sleepMin = s.minutes)
            }
        }

        if (date.isAfter(today)) {
            steps = null; screen = null; unlocks = null; sleepMin = null
        }

        val score = score(tasks, steps, p.stepGoal, sleepMin, p.sleepGoalMin, screen, p.screenLimitMin)
        val tips = tips(isToday, tasks, steps, p.stepGoal, sleep, sleepMin, p.sleepGoalMin, screen, p.screenLimitMin, unlocks, apps)

        return DaySummary(
            date = date, isToday = isToday, tasks = tasks,
            steps = steps, stepGoal = p.stepGoal,
            sleep = sleep, sleepMin = sleepMin, sleepGoal = p.sleepGoalMin,
            screenMin = screen, screenLimit = p.screenLimitMin, unlocks = unlocks,
            apps = apps, score = score, tips = tips, log = db.getLog(key),
        )
    }

    fun week(ctx: Context, end: LocalDate): List<DaySummary> =
        (6L downTo 0L).map { build(ctx, end.minusDays(it)) }

    /** 0–100. Tasks 40%, steps 20%, sleep 20%, screen time 20% (only what's measured counts). */
    private fun score(
        tasks: List<Task>, steps: Int?, stepGoal: Int, sleepMin: Int?, sleepGoal: Int, screen: Int?, limit: Int,
    ): Int {
        var w = 0.0
        var s = 0.0
        if (tasks.isNotEmpty()) {
            w += 40; s += 40.0 * tasks.count { it.done } / tasks.size
        }
        if (steps != null) {
            w += 20; s += 20.0 * min(1.0, steps.toDouble() / max(1, stepGoal))
        }
        if (sleepMin != null) {
            w += 20; s += 20.0 * min(1.0, sleepMin.toDouble() / max(1, sleepGoal))
        }
        if (screen != null) {
            w += 20
            val over = max(0, screen - limit).toDouble()
            s += 20.0 * max(0.0, 1.0 - over / max(1, limit))
        }
        return if (w == 0.0) 0 else (s / w * 100).roundToInt()
    }

    private fun tips(
        isToday: Boolean,
        tasks: List<Task>,
        steps: Int?, stepGoal: Int,
        sleep: SleepInfo?, sleepMin: Int?, sleepGoal: Int,
        screen: Int?, limit: Int,
        unlocks: Int?,
        apps: List<AppUse>,
    ): List<String> {
        val t = mutableListOf<String>()
        val done = tasks.count { it.done }
        val missed = tasks.filter { !it.done }

        if (tasks.isEmpty()) {
            t.add("No tasks were planned. Spend 5 minutes each night writing tomorrow's top 3 tasks — a written plan makes the day far more focused.")
        } else {
            val pct = done * 100 / tasks.size
            val high = missed.filter { it.priority == 2 }
            if (high.isNotEmpty()) {
                val names = high.joinToString(", ") { it.title }.let { if (it.length > 70) it.take(67) + "…" else it }
                t.add(
                    if (isToday) "High priority still open: $names. Do it before messages and email."
                    else "High priority left undone: $names. Tomorrow, do your most important task first thing."
                )
            }
            if (!isToday && tasks.size >= 7 && pct < 60) {
                t.add("You planned ${tasks.size} tasks and finished $done. Plan fewer — pick your top 3–5 and finish those before anything else.")
            }
            val untimed = missed.count { it.time.isBlank() }
            if (!isToday && untimed >= 2) {
                t.add("$untimed unfinished tasks had no time slot. Give important tasks a fixed time — scheduled tasks get done far more often.")
            }
            if (!isToday && pct == 100) {
                t.add("All ${tasks.size} tasks done — great consistency. Keep the same plan size tomorrow.")
            }
        }

        if (steps != null && steps < stepGoal) {
            val gap = Fmt.num(stepGoal - steps)
            t.add(
                if (isToday) "$gap steps to your goal. A 15-minute brisk walk adds roughly 1,500–2,000 steps."
                else "You were $gap steps short. Add a walk after lunch, or take phone calls while walking."
            )
        }
        if (sleepMin != null && sleepMin < sleepGoal - 30) {
            t.add("You slept about ${Fmt.mins(sleepMin)} (goal ${Fmt.mins(sleepGoal)}). Put the phone away 30 minutes before bed and keep a fixed wake-up time.")
        }
        if (sleep != null) {
            val hour = Instant.ofEpochMilli(sleep.start).atZone(ZoneId.systemDefault()).hour
            if (hour in 0..4) {
                t.add("Your phone was in use until ${Fmt.clock(sleep.start)}. Turn on Bedtime mode in Digital Wellbeing to wind down earlier.")
            }
        }
        if (screen != null && screen > limit) {
            val top = apps.firstOrNull()
            t.add(
                "Screen time ${Fmt.mins(screen)} is over your ${Fmt.mins(limit)} limit" +
                    (if (top != null) " — ${top.label} alone took ${Fmt.mins(top.minutes)}. Set an app timer for it." else ".")
            )
        }
        if (unlocks != null && unlocks > 80) {
            t.add("You unlocked your phone $unlocks times. Turn off non-essential notifications to cut distractions.")
        }
        if (t.isEmpty()) {
            t.add(if (isToday) "You're on track today — keep going." else "Solid day across tasks, activity and rest. Repeat it tomorrow.")
        }
        return t.take(6)
    }
}
