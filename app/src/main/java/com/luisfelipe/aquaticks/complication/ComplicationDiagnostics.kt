package com.luisfelipe.aquaticks.complication

import android.content.Context

/** Fixed local boot/lifecycle evidence. Never stores hydration records or instance IDs. */
internal class ComplicationDiagnostics(context: Context) {
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("complication_diagnostics", Context.MODE_PRIVATE)
    fun activated() = stamp("activatedAt")
    fun deactivated() = stamp("deactivatedAt")
    fun requested() = stamp("requestedAt")
    private fun stamp(key: String) = preferences.edit().putLong(key, System.currentTimeMillis()).apply()
    fun responded(status: String) {
        val now = System.currentTimeMillis()
        preferences.edit().apply {
            putLong("responseAt", now)
            putString("status", status)
            if (status == "failed") putLong("failedAt", now)
        }.apply()
    }
    fun snapshot() = ComplicationDiagnosticSnapshot(
        preferences.getLong("activatedAt", 0), preferences.getLong("deactivatedAt", 0),
        preferences.getLong("requestedAt", 0), preferences.getLong("responseAt", 0),
        preferences.getLong("failedAt", 0), preferences.getString("status", "none") ?: "none")
}

internal data class ComplicationDiagnosticSnapshot(
    val activatedAt: Long, val deactivatedAt: Long, val requestedAt: Long,
    val responseAt: Long, val failedAt: Long, val status: String
)
