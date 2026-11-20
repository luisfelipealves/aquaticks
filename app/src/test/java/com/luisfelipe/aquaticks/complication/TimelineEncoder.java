package com.luisfelipe.aquaticks.complication;

import android.support.wearable.complications.ComplicationData;
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline;

/** Same direct JVM encoder bridge used by AndroidX's own timeline tests. */
final class TimelineEncoder {
    @SuppressWarnings("KotlinInternal")
    static ComplicationData encode(ComplicationDataTimeline timeline) {
        // Reflection over all methods also resolves optional Wear SDK return types,
        // which the plain Android Robolectric SDK does not contain.
        return timeline.asWireComplicationData$watchface_complications_data_source();
    }
}
