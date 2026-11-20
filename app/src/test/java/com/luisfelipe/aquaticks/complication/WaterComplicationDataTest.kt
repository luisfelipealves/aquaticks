package com.luisfelipe.aquaticks.complication

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.wear.watchface.complications.data.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WaterComplicationDataTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun shortTextContainsActualAmount() {
        val data = WaterComplicationData.create(context, ComplicationType.SHORT_TEXT, 750, 2000)
            as ShortTextComplicationData
        assertEquals("750ml", data.text.getTextAt(context.resources, Instant.now()).toString())
    }
    @Test fun rangeClampsVisualProgressButKeepsActualAmount() {
        val data = WaterComplicationData.create(context, ComplicationType.RANGED_VALUE, 2500, 2000)
            as RangedValueComplicationData
        assertEquals(2000f, data.value, .001f)
        assertEquals("2500ml", data.text!!.getTextAt(context.resources, Instant.now()).toString())
    }
    @Test fun previousDayDataExpiresAtMidnight() {
        val midnight = Instant.parse("2026-10-21T00:00:00Z")
        val data = WaterComplicationData.create(context, ComplicationType.SHORT_TEXT, 750, 2000,
            validUntil = midnight)!!
        assertEquals(midnight, data.validTimeRange.endDateTimeMillis)
    }
    @Test fun unsupportedTypeIsNotAdvertisedAsData() {
        assertNull(WaterComplicationData.create(context, ComplicationType.LONG_TEXT, 750, 2000))
    }
}
