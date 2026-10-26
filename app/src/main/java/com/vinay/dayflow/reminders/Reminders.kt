package com.vinay.dayflow.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vinay.dayflow.R
import com.vinay.dayflow.data.Db
import com.vinay.dayflow.data.Fmt
import com.vinay.dayflow.data.Task
import com.vinay.dayflow.tracking.Notifs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Schedules one alarm per timed task: its next not-yet-done occurrence. */
object Reminders {

    @Synchronized
    fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val db = Db.get(ctx)
        val now = LocalDateTime.now()
        for (t in db.allTasks()) {
            cancel(ctx, t.id)
            if (t.time.isBlank()) continue
            val next = nextOccurrence(db, t, now) ?: continue
            val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, alarmIntent(ctx, t.id, next.toLocalDate()))
            } catch (_: Exception) {
            }
        }
    }

    private fun nextOccurrence(db: Db, t: Task, now: LocalDateTime): LocalDateTime? {
        val time = try { LocalTime.parse(t.time) } catch (e: Exception) { return null }
        for (i in 0L..8L) {
            val d = now.toLocalDate().plusDays(i)
            if (!t.occursOn(d)) continue
            val at = d.atTime(time)
            if (at.isAfter(now) && !db.isDone(t.id, d)) return at
        }
        return null
    }

    private fun alarmIntent(ctx: Context, id: Long, date: LocalDate): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            id.toInt(),
            Intent(ctx, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_ID, id)
                .putExtra(ReminderReceiver.EXTRA_DATE, date.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun cancel(ctx: Context, id: Long) {
        val pi = PendingIntent.getBroadcast(
            ctx,
            id.toInt(),
            Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val date = intent.getStringExtra(EXTRA_DATE)
        val db = Db.get(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)

        if (id > 0 && date != null) {
            val day = LocalDate.parse(date)
            if (intent.action == ACTION_DONE) {
                db.setDone(id, day, true)
                nm?.cancel(notifId(id))
            } else {
                val t = db.getTask(id)
                if (t != null && !db.isDone(id, day) && Notifs.notificationsAllowed(ctx)) {
                    Notifs.ensureChannels(ctx)
                    val doneIntent = PendingIntent.getBroadcast(
                        ctx,
                        id.toInt() + 100_000,
                        Intent(ctx, ReminderReceiver::class.java)
                            .setAction(ACTION_DONE)
                            .putExtra(EXTRA_ID, id)
                            .putExtra(EXTRA_DATE, date),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val meta = listOf(Fmt.time12(t.time), t.category).filter { it.isNotBlank() }.joinToString(" · ")
                    val text = if (t.notes.isNotBlank()) "$meta — ${t.notes}" else meta
                    val n = NotificationCompat.Builder(ctx, Notifs.CH_REMIND)
                        .setSmallIcon(R.drawable.ic_notif)
                        .setContentTitle(t.title)
                        .setContentText(text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setAutoCancel(true)
                        .setContentIntent(Notifs.openAppIntent(ctx))
                        .addAction(0, "Mark done", doneIntent)
                        .build()
                    try {
                        nm?.notify(notifId(id), n)
                    } catch (_: SecurityException) {
                    }
                }
            }
        }
        Reminders.scheduleAll(ctx)
    }

    private fun notifId(id: Long) = 5000 + id.toInt()

    companion object {
        const val ACTION_DONE = "com.vinay.dayflow.MARK_DONE"
        const val EXTRA_ID = "task_id"
        const val EXTRA_DATE = "task_date"
    }
}
