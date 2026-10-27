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
            val diagnostics = ReminderDiagnostics(context)
            val isReminder = intent.action == ReminderScheduler.ACTION_REMIND
            var failed = false
            try {
                if (isReminder) diagnostics.received()
                withTimeout(8000) {
                    processAction(context, intent)
                }
            } catch (error: Exception) {
                failed = true
                Log.e("AquaTicks", "Could not process local water reminder", error)
            } finally {
                try {
                    if (isReminder) diagnostics.finished(failed)
                } finally { pending.finish() }
            }
        }
    }
    internal suspend fun processAction(context: Context, intent: Intent,
        postReminder: (Context, Int) -> Unit = ::showReminder) {
        val app = context.aquaApp
        if (intent.action == ReminderScheduler.ACTION_ADD) {
            try {
                val operation = intent.data?.lastPathSegment ?: return
                val amount = intent.getIntExtra("amount", 250)
                app.repository.add(amount, operation)
                NotificationManagerCompat.from(context).cancel(ReminderScheduler.NOTIFICATION_ID)
                context.refreshSurfaces()
            } finally { app.reminders.reconcile() }
        } else if (intent.action == ReminderScheduler.ACTION_REMIND) {
            // Consume this deadline and arm its successor before posting. A failed or
            // cancelled notification must not leave the one-shot alarm chain empty.
            app.reminders.reconcile(force = true)
            val prefs = app.repository.settings()
            if (prefs.remindersEnabled && app.reminders.allowed() &&
                !WaterRules.quiet(ZonedDateTime.now(), prefs.quietEnabled,
                    prefs.quietStart, prefs.quietEnd)) {
                postReminder(context, prefs.defaultMl)
                ReminderDiagnostics(context).posted()
            }
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
            .addAction(R.drawable.ic_drop, "+$amount ml", add)
            .build()
        NotificationManagerCompat.from(context).notify(ReminderScheduler.NOTIFICATION_ID, notification)
    }
}
