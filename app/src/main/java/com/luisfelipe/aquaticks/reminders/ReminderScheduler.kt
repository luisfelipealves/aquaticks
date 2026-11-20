package com.luisfelipe.aquaticks.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.luisfelipe.aquaticks.data.WaterRepository
import com.luisfelipe.aquaticks.domain.WaterRules
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderScheduler(private val context: Context, private val repository: WaterRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    private val lock = Mutex()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    fun createChannel() {
        val channel = NotificationChannel(CHANNEL, "Lembretes de água", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Lembretes configurados no AquaTicks"
        channel.enableVibration(true)
        channel.vibrationPattern = longArrayOf(0, 35)
        channel.setSound(null, null)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    fun allowed(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(CHANNEL)
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            channel?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun preciseAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    @SuppressLint("ScheduleExactAlarm") // Guarded by preciseAllowed, checked for every scheduling call.
    suspend fun reconcile(force: Boolean = false, reset: Boolean = false) = lock.withLock {
        createChannel()
        val pending = PendingIntent.getBroadcast(context, 10,
            Intent(context, WaterActionReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val settings = repository.settings()
        if (!settings.remindersEnabled || !allowed()) {
            alarms.cancel(pending)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            ReminderDiagnostics(context).scheduled(0)
            return@withLock
        }
        val now = clock.instant()
        val zone = zone()
        if (WaterRules.quiet(now.atZone(zone), settings.quietEnabled,
                settings.quietStart, settings.quietEnd)) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
        val saved = Instant.ofEpochMilli(settings.nextReminderAt)
        val aligned = settings.nextReminderAt > 0 && WaterRules.isWholeHour(saved, zone)
        val savedIsUsable = aligned && !WaterRules.quiet(saved.atZone(zone),
            settings.quietEnabled, settings.quietStart, settings.quietEnd)
        // Keep future calendar slots on refresh/reboot/permission changes. A late
        // delivery advances from its requested slot, never from the delivery time.
        val keep = !reset && savedIsUsable && (saved > now || (saved == now && !force))
        val due = if (keep) saved
            else WaterRules.nextReminder(now, zone, settings.intervalHours,
                settings.quietEnabled, settings.quietStart, settings.quietEnd,
                previousTarget = saved.takeIf { !reset && aligned })
        val dueMillis = due?.toEpochMilli() ?: 0L
        if (settings.nextReminderAt != dueMillis) {
            repository.update { it.copy(nextReminderAt = dueMillis) }
        }
        if (due == null) {
            alarms.cancel(pending)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            ReminderDiagnostics(context).scheduled(0)
            return@withLock
        }
        val precise = preciseAllowed()
        if (precise) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueMillis, pending)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueMillis, pending)
        ReminderDiagnostics(context).scheduled(dueMillis, precise)
    }
    companion object {
        const val CHANNEL = "water_reminders"
        const val NOTIFICATION_ID = 10
        const val ACTION_REMIND = "com.luisfelipe.aquaticks.REMIND"
        const val ACTION_ADD = "com.luisfelipe.aquaticks.ADD"
    }
}
