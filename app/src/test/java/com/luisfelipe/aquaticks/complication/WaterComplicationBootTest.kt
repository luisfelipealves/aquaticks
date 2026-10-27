package com.luisfelipe.aquaticks.complication

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.luisfelipe.aquaticks.AquaTicksApp
import com.luisfelipe.aquaticks.aquaApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AquaTicksApp::class)
class WaterComplicationBootTest {
    private val app: AquaTicksApp get() = ApplicationProvider.getApplicationContext()
    @Test fun providerIsDiscoverableBeforeFirstUnlock() {
        val component = ComponentName(app, WaterComplicationService::class.java)
        val matches = app.packageManager.queryIntentServices(Intent(
            "android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST")
            .setPackage(app.packageName), PackageManager.MATCH_DIRECT_BOOT_AWARE)
        assertTrue("Provider must be available to locked-boot discovery",
            matches.any { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) == component })
    }
    @Test fun lockedShortTextRequestDoesNotOpenCredentialDatabase() = verifyLocked(ComplicationType.SHORT_TEXT)
    @Test fun lockedRangedRequestDoesNotOpenCredentialDatabase() = verifyLocked(ComplicationType.RANGED_VALUE)
    private fun verifyLocked(type: ComplicationType) = runBlocking {
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        val controller = Robolectric.buildService(WaterComplicationService::class.java).create()
        try {
            val timeline = requireNotNull(controller.get().onComplicationRequest(ComplicationRequest(1, type, false)))
            assertTrue("Locked request must return no data, never a fabricated total",
                timeline.defaultComplicationData is NoDataComplicationData)
            assertFalse("Room must stay unopened before first unlock", app.getDatabasePath("aquaticks.db").exists())
        } finally { controller.destroy() }
    }
    @Test fun unlockServesSavedConsumptionWithoutReselectingProvider() = runBlocking {
        val manager = app.getSystemService(UserManager::class.java)
        val controller = Robolectric.buildService(WaterComplicationService::class.java).create()
        try {
            shadowOf(manager).setUserUnlocked(false)
            controller.get().onComplicationRequest(ComplicationRequest(1, ComplicationType.SHORT_TEXT, false))
            shadowOf(manager).setUserUnlocked(true)
            app.repository.add(250, "boot-intake")
            val timeline = requireNotNull(controller.get().onComplicationRequest(
                ComplicationRequest(1, ComplicationType.SHORT_TEXT, false)))
            val wire = TimelineEncoder.encode(timeline)
            assertEquals("250ml", wire.shortText!!.getTextAt(app.resources, System.currentTimeMillis()).toString())
        } finally { controller.destroy() }
    }
    @Test fun lifecycleEvidenceSurvivesServiceRecreationWithoutHydrationData() {
        val controller = Robolectric.buildService(WaterComplicationService::class.java).create()
        controller.get().onComplicationActivated(7, ComplicationType.SHORT_TEXT)
        controller.get().onComplicationDeactivated(7)
        controller.destroy()
        val prefs = app.createDeviceProtectedStorageContext()
            .getSharedPreferences("complication_diagnostics", Context.MODE_PRIVATE)
        assertTrue(prefs.getLong("activatedAt", 0) > 0)
        assertTrue(prefs.getLong("deactivatedAt", 0) > 0)
        assertEquals(setOf("activatedAt", "deactivatedAt"), prefs.all.keys)
    }
}
