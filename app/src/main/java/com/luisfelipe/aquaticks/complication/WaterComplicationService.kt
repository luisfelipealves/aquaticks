package com.luisfelipe.aquaticks.complication

import android.app.PendingIntent
import android.content.Intent
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.*
import com.luisfelipe.aquaticks.MainActivity
import com.luisfelipe.aquaticks.aquaApp
import java.time.Instant
import java.time.ZoneId

class WaterComplicationService : SuspendingTimelineComplicationDataSourceService() {
    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        super.onComplicationActivated(complicationInstanceId, type)
        ComplicationDiagnostics(this).activated()
    }
    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        super.onComplicationDeactivated(complicationInstanceId)
        ComplicationDiagnostics(this).deactivated()
    }
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        val diagnostics = ComplicationDiagnostics(this)
        diagnostics.requested()
        // Application/repository initialization is lazy. Do not touch credential
        // storage before first unlock, even when the system binds during Direct Boot.
        if (!getSystemService(UserManager::class.java).isUserUnlocked) {
            diagnostics.responded("locked")
            return ComplicationDataTimeline(NoDataComplicationData(), emptyList())
        }
        return try {
            createTimeline(request).also { diagnostics.responded(if (it == null) "unsupported" else "ready") }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("AquaTicks", "Could not prepare complication data", error)
            diagnostics.responded("failed")
            ComplicationDataTimeline(NoDataComplicationData(), emptyList())
        }
    }
    private suspend fun createTimeline(request: ComplicationRequest): ComplicationDataTimeline? {
        aquaApp.repository.ensureToday()
        val snapshot = aquaApp.repository.snapshot()
        val midnight = snapshot.day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val tap = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val current = WaterComplicationData.create(this, request.complicationType, snapshot.totalMl,
            snapshot.goalMl, tap, midnight) ?: return null
        val next = WaterComplicationData.create(this, request.complicationType, 0,
            snapshot.settings.goalMl, tap) ?: return null
        // AndroidX caches each ComplicationData's wire object. Reusing `current` as the
        // root and an entry makes the root contain itself, breaking Binder serialization.
        // Keep the root valid after midnight so timeline-capable hosts can select `next`.
        val fallback = WaterComplicationData.create(this, request.complicationType, snapshot.totalMl,
            snapshot.goalMl, tap) ?: return null
        return ComplicationDataTimeline(fallback, listOf(
            TimelineEntry(TimeInterval(Instant.EPOCH, midnight), current),
            TimelineEntry(TimeInterval(midnight, midnight.plusSeconds(3650L * 86400)), next)
        ))
    }
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        WaterComplicationData.create(this, type, 750, 2000)
}
