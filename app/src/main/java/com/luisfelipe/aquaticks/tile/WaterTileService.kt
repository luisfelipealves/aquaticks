package com.luisfelipe.aquaticks.tile

import androidx.wear.tiles.TileService
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.protolayout.*
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.ProgressIndicatorColors
import com.google.common.util.concurrent.ListenableFuture
import com.luisfelipe.aquaticks.MainActivity
import com.luisfelipe.aquaticks.aquaApp
import com.luisfelipe.aquaticks.data.DaySnapshot
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.future

class WaterTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            aquaApp.repository.ensureToday()
            val snapshot = aquaApp.repository.snapshot()
            val midnight = snapshot.day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val current = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(layout(snapshot)).build())
                .setValidity(TimelineBuilders.TimeInterval.Builder()
                    .setStartMillis(0).setEndMillis(midnight).build()).build()
            // The renderer can switch to zero at midnight without waking the app.
            val nextDay = snapshot.copy(day = snapshot.day.plusDays(1), goalMl = snapshot.settings.goalMl,
                intakes = emptyList())
            val next = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(layout(nextDay)).build())
                .setValidity(TimelineBuilders.TimeInterval.Builder().setStartMillis(midnight)
                    .setEndMillis(Long.MAX_VALUE).build()).build()
            TileBuilders.Tile.Builder().setResourcesVersion("1")
                .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(current)
                    .addTimelineEntry(next).build())
                .build()
        }
    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest):
        ListenableFuture<ResourceBuilders.Resources> = scope.future {
        ResourceBuilders.Resources.Builder().setVersion("1").build()
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private fun text(value: String, sp: Float, color: Int = 0xFFFFFFFF.toInt()) =
        LayoutElementBuilders.Text.Builder().setText(value)
            .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(sp))
                .setColor(ColorBuilders.argb(color)).build()).build()
    private fun layout(snapshot: DaySnapshot): LayoutElementBuilders.LayoutElement {
        // Tile action opens the app directly at the single-tap register button.
        // It deliberately never creates an intake merely because the Tile was refreshed.
        val action = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(ActionBuilders.AndroidActivity.Builder()
                .setPackageName(packageName).setClassName(MainActivity::class.java.name).build()).build()
        val clickable = ModifiersBuilders.Clickable.Builder().setId("open_water").setOnClick(action).build()
        val ring = CircularProgressIndicator.Builder().setProgress(snapshot.progress)
            .setStrokeWidth(DimensionBuilders.dp(8f))
            .setCircularProgressIndicatorColors(ProgressIndicatorColors(0xFF458FFF.toInt(), 0xFF18212D.toInt()))
            .setContentDescription("${snapshot.totalMl} de ${snapshot.goalMl} mililitros").build()
        val center = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("AquaTicks", 14f, 0xFFB5BAC3.toInt()))
            .addContent(text("${snapshot.totalMl} ml", 24f))
            .addContent(text("de ${snapshot.goalMl} ml", 14f, 0xFFB5BAC3.toInt()))
            .addContent(text("+${snapshot.settings.defaultMl} ml ›", 18f, 0xFF458FFF.toInt())).build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand()).setHeight(DimensionBuilders.expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable)
                .setBackground(ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(0xFF000000.toInt())).build())
                .setPadding(ModifiersBuilders.Padding.Builder().setAll(DimensionBuilders.dp(16f)).build())
                .build())
            .addContent(ring).addContent(center).build()
    }
}
