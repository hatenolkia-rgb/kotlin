package com.vinay.dayflow.data

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("dayflow_prefs", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) { sp.edit().putString("name", v).apply() }

    var stepGoal: Int
        get() = sp.getInt("step_goal", 8000)
        set(v) { sp.edit().putInt("step_goal", v).apply() }

    var sleepGoalMin: Int
        get() = sp.getInt("sleep_goal", 450)
        set(v) { sp.edit().putInt("sleep_goal", v).apply() }

    var screenLimitMin: Int
        get() = sp.getInt("screen_limit", 180)
        set(v) { sp.edit().putInt("screen_limit", v).apply() }

    var firstRunDone: Boolean
        get() = sp.getBoolean("first_run_done", false)
        set(v) { sp.edit().putBoolean("first_run_done", v).apply() }

    fun wasAsked(permission: String): Boolean = sp.getBoolean("asked_$permission", false)
    fun markAsked(permission: String) { sp.edit().putBoolean("asked_$permission", true).apply() }
}

object Fmt {
    private val clockFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val longFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())

    fun mins(m: Int): String = when {
        m < 60 -> "${m}m"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }

    fun num(n: Int): String = String.format(Locale.getDefault(), "%,d", n)

    fun clock(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(clockFmt)

    fun time12(hhmm: String): String =
        if (hhmm.isBlank()) "" else try { LocalTime.parse(hhmm).format(clockFmt) } catch (e: Exception) { hhmm }

    fun dayLabel(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> d.format(dayFmt)
    }

    fun longDate(d: LocalDate): String = d.format(longFmt)

    fun startMillis(d: LocalDate): Long = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
