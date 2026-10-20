package com.luisfelipe.aquaticks.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.*
import com.luisfelipe.aquaticks.MainActivity
import com.luisfelipe.aquaticks.aquaApp
import java.time.Instant
import java.time.ZoneId

class WaterComplicationService : SuspendingTimelineComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        aquaApp.repository.ensureToday()
        val snapshot = aquaApp.repository.snapshot()
        val midnight = snapshot.day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val tap = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val current = WaterComplicationData.create(this, request.complicationType, snapshot.totalMl,
            snapshot.goalMl, tap, midnight) ?: return null
        val next = WaterComplicationData.create(this, request.complicationType, 0,
            snapshot.settings.goalMl, tap) ?: return null
        return ComplicationDataTimeline(current, listOf(
            TimelineEntry(TimeInterval(Instant.EPOCH, midnight), current),
            TimelineEntry(TimeInterval(midnight, midnight.plusSeconds(3650L * 86400)), next)
        ))
    }
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        WaterComplicationData.create(this, type, 750, 2000)
}
