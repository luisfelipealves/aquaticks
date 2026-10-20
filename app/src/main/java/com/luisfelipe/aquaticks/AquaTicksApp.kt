package com.luisfelipe.aquaticks

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.room.Room
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.luisfelipe.aquaticks.complication.WaterComplicationService
import com.luisfelipe.aquaticks.data.WaterDatabase
import com.luisfelipe.aquaticks.data.WaterRepository
import com.luisfelipe.aquaticks.reminders.ReminderScheduler
import com.luisfelipe.aquaticks.tile.WaterTileService

class AquaTicksApp : Application() {
    val repository by lazy {
        WaterRepository(Room.databaseBuilder(this, WaterDatabase::class.java, "aquaticks.db").build())
    }
    val reminders by lazy { ReminderScheduler(this, repository) }
}
val Context.aquaApp: AquaTicksApp get() = applicationContext as AquaTicksApp

fun Context.refreshSurfaces() {
    TileService.getUpdater(applicationContext).requestUpdate(WaterTileService::class.java)
    ComplicationDataSourceUpdateRequester.create(applicationContext,
        ComponentName(applicationContext, WaterComplicationService::class.java)).requestUpdateAll()
}
