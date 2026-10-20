package com.luisfelipe.aquaticks.complication

import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import com.luisfelipe.aquaticks.R
import com.luisfelipe.aquaticks.domain.WaterRules
import java.time.Instant
import java.util.Locale

object WaterComplicationData {
    fun create(context: Context, type: ComplicationType, total: Int, goal: Int,
        tap: PendingIntent? = null, validUntil: Instant = Instant.MAX): ComplicationData? {
        val description = PlainComplicationText.Builder("$total de $goal mililitros").build()
        val label = if (total < 10000) "${total}ml"
            else String.format(Locale.US, "%.1fL", total / 1000f)
        val text = PlainComplicationText.Builder(label).build()
        val icon = MonochromaticImage.Builder(Icon.createWithResource(context, R.drawable.ic_drop)).build()
        val range = TimeRange.between(Instant.EPOCH, validUntil)
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text, description)
                .setMonochromaticImage(icon).setTapAction(tap).setValidTimeRange(range).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = WaterRules.progress(total, goal) * goal.coerceAtLeast(1),
                min = 0f, max = goal.coerceAtLeast(1).toFloat(), contentDescription = description)
                .setText(text).setMonochromaticImage(icon).setTapAction(tap).setValidTimeRange(range).build()
            else -> null
        }
    }
}
