package com.luisfelipe.aquaticks.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.luisfelipe.aquaticks.aquaApp
import com.luisfelipe.aquaticks.refreshSurfaces
import kotlinx.coroutines.*

class SystemChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8000) {
                    context.aquaApp.repository.ensureToday()
                    context.aquaApp.reminders.reconcile(force = intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED)
                    context.refreshSurfaces()
                }
            } catch (error: Exception) {
                Log.e("AquaTicks", "Could not reconcile local schedule", error)
            } finally { pending.finish() }
        }
    }
}
