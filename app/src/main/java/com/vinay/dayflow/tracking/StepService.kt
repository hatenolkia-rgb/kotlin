package com.vinay.dayflow.tracking

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vinay.dayflow.R
import com.vinay.dayflow.data.Db
import com.vinay.dayflow.data.Fmt
import com.vinay.dayflow.reminders.Reminders
import java.time.LocalDate

/**
 * Turns the phone's hardware step counter (total steps since boot) into "steps today".
 * Works incrementally, so it survives reboots and day changes.
 */
object StepStore {
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("steps", Context.MODE_PRIVATE)

    @Synchronized
    fun onCounter(ctx: Context, total: Long): Int {
        val s = sp(ctx)
        val today = LocalDate.now().toString()
        val last = s.getLong("last", -1L)
        var steps = if (s.getString("day", null) == today) s.getInt("steps", 0) else 0
        val delta = when {
            last < 0 -> 0L                 // first reading ever: start counting from now
            total >= last -> total - last
            else -> total                  // phone rebooted, counter restarted from zero
        }
        steps += delta.coerceIn(0L, 60_000L).toInt()
        s.edit().putString("day", today).putInt("steps", steps).putLong("last", total).apply()
        Db.get(ctx).saveMetrics(today, steps = steps)
        return steps
    }

    /** Steps today, 0 if tracking is set up but no steps yet, null if tracking never ran. */
    fun todayOrNull(ctx: Context): Int? {
        val s = sp(ctx)
        return when {
            s.getString("day", null) == LocalDate.now().toString() -> s.getInt("steps", 0)
            s.contains("last") -> 0
            else -> null
        }
    }
}

class StepService : Service(), SensorEventListener {
    private var manager: SensorManager? = null
    private var shown = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifs.ensureChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification(StepStore.todayOrNull(this) ?: 0), type)
        } catch (e: Exception) {
            stopSelf()
            return
        }
        val sm = getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sm == null || sensor == null) {
            stopSelf()
            return
        }
        // Batch readings up to 30s to save battery.
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 30_000_000)
        manager = sm
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val steps = StepStore.onCounter(this, event.values[0].toLong())
        if (steps != shown) {
            shown = steps
            try {
                getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, notification(steps))
            } catch (_: Exception) {
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        manager?.unregisterListener(this)
        super.onDestroy()
    }

    private fun notification(steps: Int): Notification =
        NotificationCompat.Builder(this, Notifs.CH_TRACK)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("${Fmt.num(steps)} steps today")
            .setContentText("DayFlow is counting your steps")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(Notifs.openAppIntent(this))
            .build()

    companion object {
        private const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            if (!Perms.activity(ctx) || !Perms.hasStepSensor(ctx)) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, StepService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                StepService.start(context)
                Reminders.scheduleAll(context)
            }
        }
    }
}
