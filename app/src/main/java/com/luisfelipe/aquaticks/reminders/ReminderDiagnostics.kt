package com.luisfelipe.aquaticks.reminders

import android.content.Context

/** A fixed-size snapshot on the watch, not a log of consumption or telemetry. */
internal class ReminderDiagnostics(context: Context) {
    private val preferences = context.getSharedPreferences("reminder_diagnostics", Context.MODE_PRIVATE)

    fun received() = preferences.edit()
        .putLong("received", System.currentTimeMillis()).apply()

    fun posted() = preferences.edit()
        .putLong("posted", System.currentTimeMillis()).apply()

    fun scheduled(at: Long) = preferences.edit().putLong("scheduled", at).apply()

    fun finished(failed: Boolean) {
        val now = System.currentTimeMillis()
        preferences.edit().apply {
            putLong("finished", now)
            if (failed) putLong("failed", now)
        }.apply()
    }

    fun snapshot() = ReminderDiagnosticSnapshot(
        preferences.getLong("received", 0),
        preferences.getLong("posted", 0),
        preferences.getLong("scheduled", 0),
        preferences.getLong("finished", 0),
        preferences.getLong("failed", 0)
    )
}

internal data class ReminderDiagnosticSnapshot(
    val receivedAt: Long,
    val postedAt: Long,
    val scheduledAt: Long,
    val finishedAt: Long,
    val failedAt: Long
)
