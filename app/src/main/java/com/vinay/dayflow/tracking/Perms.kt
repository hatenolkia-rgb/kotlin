package com.vinay.dayflow.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.vinay.dayflow.MainActivity

data class PermState(
    val activity: Boolean = true,
    val notifications: Boolean = true,
    val usage: Boolean = true,
    val battery: Boolean = true,
    val sensor: Boolean = true,
) {
    companion object {
        fun read(ctx: Context) = PermState(
            activity = Perms.activity(ctx),
            notifications = Perms.notifications(ctx),
            usage = UsageTracker.hasAccess(ctx),
            battery = Perms.batteryUnrestricted(ctx),
            sensor = Perms.hasStepSensor(ctx),
        )
    }
}

object Perms {
    fun activity(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    fun notifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true

    fun hasStepSensor(ctx: Context): Boolean =
        ctx.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    private fun launch(ctx: Context, vararg intents: Intent) {
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
    }

    fun openUsageSettings(ctx: Context) = launch(
        ctx,
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
    )

    fun openBatterySettings(ctx: Context) = launch(
        ctx,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appSettingsIntent(ctx),
    )

    fun openAppSettings(ctx: Context) = launch(ctx, appSettingsIntent(ctx))

    private fun appSettingsIntent(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
}

object Notifs {
    const val CH_TRACK = "tracking"
    const val CH_REMIND = "reminders"

    fun notificationsAllowed(ctx: Context): Boolean = Perms.notifications(ctx)

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_TRACK, "Step counting", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                description = "Shows today's steps while DayFlow counts in the background"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminds you when a task is due"
            }
        )
    }

    fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx,
        0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
