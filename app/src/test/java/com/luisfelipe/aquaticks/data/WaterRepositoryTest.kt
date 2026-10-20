package com.luisfelipe.aquaticks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WaterRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: WaterDatabase
    private lateinit var repository: WaterRepository
    private val clock = MovingClock(Instant.parse("2026-10-20T20:00:00Z"))
    private var zone: ZoneId = ZoneId.of("Europe/Lisbon")
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("water-test.db")
        open()
    }
    private fun open() {
        db = Room.databaseBuilder(context, WaterDatabase::class.java, "water-test.db").build()
        repository = WaterRepository(db, clock) { zone }
    }
    @After fun tearDown() { db.close(); context.deleteDatabase("water-test.db") }
    @Test fun recordsAndSettingsSurviveReopeningDatabase() = runBlocking {
        repository.update { it.copy(goalMl = 2500, remindersEnabled = true, intervalHours = 3,
            quietStart = 1380, quietEnd = 420, defaultMl = 300) }
        assertTrue(repository.add(300, "entry"))
        db.close()
        open()
        assertEquals(300, repository.snapshot().totalMl)
        assertEquals(2500, repository.snapshot().goalMl)
        assertTrue(repository.settings().remindersEnabled)
        assertEquals(3, repository.settings().intervalHours)
        assertEquals(1380, repository.settings().quietStart)
        assertEquals(420, repository.settings().quietEnd)
        assertEquals(300, repository.settings().defaultMl)
    }
    @Test fun duplicateNotificationActionIsIdempotent() = runBlocking {
        assertTrue(repository.add(250, "notification-1"))
        assertFalse(repository.add(250, "notification-1"))
        assertEquals(250, repository.snapshot().totalMl)
    }
    @Test fun concurrentAddsAreNotLost() = runBlocking {
        (1..8).map { async { repository.add(250, "entry-$it") } }.awaitAll()
        assertEquals(2000, repository.snapshot().totalMl)
    }
    @Test fun newDayIsEmptyAndPreviousDayIsPreserved() = runBlocking {
        repository.add(250)
        val yesterday = repository.today()
        clock.current = Instant.parse("2026-10-20T23:00:00Z")
        repository.ensureToday()
        assertEquals(0, repository.snapshot().totalMl)
        assertEquals(250, repository.snapshot(yesterday).totalMl)
    }
    @Test fun timezoneChangeDoesNotReassignPastRecords() = runBlocking {
        clock.current = Instant.parse("2026-10-20T23:30:00Z")
        repository.add(250)
        val recordedDay = repository.today()
        zone = ZoneId.of("America/New_York")
        assertEquals(0, repository.snapshot().totalMl)
        assertEquals(250, repository.snapshot(recordedDay).totalMl)
        assertEquals("Europe/Lisbon", repository.snapshot(recordedDay).intakes.single().zoneId)
    }
    @Test fun goalEditDoesNotChangeYesterday() = runBlocking {
        repository.add(250)
        val yesterday = repository.today()
        clock.current = Instant.parse("2026-10-21T12:00:00Z")
        repository.update { it.copy(goalMl = 3000) }
        assertEquals(2000, repository.snapshot(yesterday).goalMl)
        assertEquals(3000, repository.snapshot().goalMl)
    }
    @Test fun invalidIntakeAndSettingsAreRejectedWithoutWriting() = runBlocking {
        try { repository.add(-250); fail("Expected invalid amount") } catch (_: IllegalArgumentException) {}
        try { repository.update { it.copy(intervalHours = 0) }; fail("Expected invalid interval") }
        catch (_: IllegalArgumentException) {}
        assertEquals(0, repository.snapshot().totalMl)
        assertEquals(2, repository.settings().intervalHours)
    }
    @Test fun changingReminderConfigInvalidatesSavedSchedule() = runBlocking {
        repository.update { it.copy(remindersEnabled = true, nextReminderAt = 1000) }
        repository.update { it.copy(nextReminderAt = 123456789) }
        repository.update { it.copy(goalMl = 3000) }
        assertEquals(123456789L, repository.settings().nextReminderAt)
        repository.update { it.copy(intervalHours = 4) }
        assertEquals(0L, repository.settings().nextReminderAt)
        repository.update { it.copy(nextReminderAt = 123456789) }
        repository.update { it.copy(remindersEnabled = false) }
        assertEquals(0L, repository.settings().nextReminderAt)
    }
    private class MovingClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
    }
}
