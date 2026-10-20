package com.luisfelipe.aquaticks.reminders

import android.Manifest
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
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderScheduler(private val context: Context, private val repository: WaterRepository) {
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
    suspend fun reconcile(force: Boolean = false) = lock.withLock {
        createChannel()
        val pending = PendingIntent.getBroadcast(context, 10,
            Intent(context, WaterActionReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(pending)
        val settings = repository.settings()
        if (!settings.remindersEnabled || !allowed()) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return@withLock
        }
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        if (WaterRules.quiet(now.atZone(zone), settings.quietEnabled,
                settings.quietStart, settings.quietEnd)) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
        val saved = Instant.ofEpochMilli(settings.nextReminderAt)
        val due = if (!force && saved > now &&
            !WaterRules.quiet(saved.atZone(zone), settings.quietEnabled, settings.quietStart, settings.quietEnd)) saved
        else WaterRules.nextReminder(now, zone, settings.intervalHours,
            settings.quietEnabled, settings.quietStart, settings.quietEnd)
        if (settings.nextReminderAt != due.toEpochMilli()) {
            repository.update { it.copy(nextReminderAt = due.toEpochMilli()) }
        }
        // One inexact alarm. Android may batch/delay it; no exact-alarm permission.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, due.toEpochMilli(), 10 * 60 * 1000L, pending)
    }
    companion object {
        const val CHANNEL = "water_reminders"
        const val NOTIFICATION_ID = 10
        const val ACTION_REMIND = "com.luisfelipe.aquaticks.REMIND"
        const val ACTION_ADD = "com.luisfelipe.aquaticks.ADD"
    }
}
