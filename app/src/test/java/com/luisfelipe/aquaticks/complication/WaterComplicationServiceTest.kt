package com.luisfelipe.aquaticks.complication

import android.os.Parcel
import android.support.wearable.complications.ComplicationData as WireData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.luisfelipe.aquaticks.AquaTicksApp
import com.luisfelipe.aquaticks.aquaApp
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AquaTicksApp::class)
class WaterComplicationServiceTest {
    @Test fun shortTextRequestCanBeDeliveredToWatchFace() = verifyDelivery(ComplicationType.SHORT_TEXT)
    @Test fun rangedValueRequestCanBeDeliveredToWatchFace() = verifyDelivery(ComplicationType.RANGED_VALUE)

    private fun verifyDelivery(type: ComplicationType) = runBlocking {
        val controller = Robolectric.buildService(WaterComplicationService::class.java).create()
        val service = controller.get()
        try {
            service.aquaApp.repository.add(250, "complication-test")
            val timeline = requireNotNull(service.onComplicationRequest(ComplicationRequest(1, type, false)))
            // Use the library's real timeline encoder, not just our data builder.
            val wire = TimelineEncoder.encode(timeline)
            val entries = requireNotNull(wire.timelineEntries)
            assertEquals(2, entries.size)
            // Check before parceling: a cycle would recurse indefinitely in Binder serialization.
            entries.forEach { assertNotSame("Timeline must not contain its own root", wire, it) }
            val parcel = Parcel.obtain()
            try {
                wire.writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                val delivered = WireData.CREATOR.createFromParcel(parcel)
                assertTrue(delivered.isActiveAt(Instant.now().toEpochMilli()))
                assertEquals(2, delivered.timelineEntries!!.size)
                assertEquals(type.toWireComplicationType(), delivered.type)
                if (type == ComplicationType.RANGED_VALUE) assertEquals(250f, delivered.rangedValue, .001f)
                assertEquals("250ml", delivered.shortText!!.getTextAt(service.resources, System.currentTimeMillis()).toString())
                assertNotNull(delivered.tapAction)
                val midnight = timeline.timelineEntries.first().validity.end
                assertTrue("Root must not expire before the next timeline entry", delivered.isActiveAt(midnight.toEpochMilli()))
                val next = delivered.timelineEntries!![1]
                assertEquals(midnight.epochSecond, next.timelineStartEpochSecond)
                assertEquals("0ml", next.shortText!!.getTextAt(service.resources, midnight.toEpochMilli()).toString())
                assertTrue(next.isActiveAt(midnight.toEpochMilli()))
            } finally { parcel.recycle() }
        } finally { controller.destroy() }
    }
}
