package com.luisfelipe.aquaticks.reminders

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.luisfelipe.aquaticks.MainActivity
import com.luisfelipe.aquaticks.R
import com.luisfelipe.aquaticks.aquaApp
import com.luisfelipe.aquaticks.domain.WaterRules
import com.luisfelipe.aquaticks.refreshSurfaces
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.*

class WaterActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(ReminderScheduler.ACTION_REMIND, ReminderScheduler.ACTION_ADD)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8000) {
                    processAction(context, intent)
                }
            } catch (error: Exception) {
                Log.e("AquaTicks", "Could not process local water reminder", error)
            } finally { pending.finish() }
        }
    }
    internal suspend fun processAction(context: Context, intent: Intent) {
        val app = context.aquaApp
        try {
            if (intent.action == ReminderScheduler.ACTION_ADD) {
                val operation = intent.data?.lastPathSegment ?: return
                val amount = intent.getIntExtra("amount", 250)
                val added = app.repository.add(amount, operation)
                context.refreshSurfaces()
                if (added && app.reminders.allowed()) confirm(context, amount)
            } else {
                val prefs = app.repository.settings()
                if (prefs.remindersEnabled && app.reminders.allowed() &&
                    !WaterRules.quiet(ZonedDateTime.now(), prefs.quietEnabled,
                        prefs.quietStart, prefs.quietEnd)) {
                    showReminder(context, prefs.defaultMl)
                }
            }
        } finally {
            // Only a delivered alarm consumes the deadline. Foreground refreshes and
            // intake actions preserve it. A notification failure must not stop the chain.
            app.reminders.reconcile(force = intent.action == ReminderScheduler.ACTION_REMIND)
        }
    }
    @SuppressLint("MissingPermission")
    private fun showReminder(context: Context, amount: Int) {
        val token = UUID.randomUUID().toString()
        val addIntent = Intent(context, WaterActionReceiver::class.java)
            .setAction(ReminderScheduler.ACTION_ADD)
            .setData(Uri.parse("aquaticks://intake/$token"))
            .putExtra("amount", amount)
        val add = PendingIntent.getBroadcast(context, 11, addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, 12, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_drop)
            .setContentTitle("Hora de beber água!")
            .setContentText("Registre +$amount ml com um toque.")
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(45 * 60 * 1000L)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_drop, "+$amount ml", add)
            .build()
        NotificationManagerCompat.from(context).notify(ReminderScheduler.NOTIFICATION_ID, notification)
    }
    @SuppressLint("MissingPermission")
    private fun confirm(context: Context, amount: Int) {
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_drop)
            .setContentTitle("Boa!")
            .setContentText("+$amount ml registrados")
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setTimeoutAfter(4000)
            .build()
        NotificationManagerCompat.from(context).notify(ReminderScheduler.NOTIFICATION_ID, notification)
    }
}
