package com.luisfelipe.aquaticks.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.luisfelipe.aquaticks.data.WaterDatabase
import com.luisfelipe.aquaticks.data.WaterRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReminderSchedulerTest {
    private lateinit var app: Application
    private lateinit var db: WaterDatabase
    private lateinit var repository: WaterRepository
    private lateinit var scheduler: ReminderScheduler
    private val clock = MovingClock(Instant.parse("2026-10-22T10:00:00Z"))
    private val zone = ZoneId.of("Europe/Lisbon")
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(app, WaterDatabase::class.java).build()
        repository = WaterRepository(db, clock) { zone }
        scheduler = ReminderScheduler(app, repository, clock) { zone }
        repository.update { it.copy(remindersEnabled = true, intervalHours = 1, quietEnabled = false) }
    }
    @After fun close() { db.close() }

    @Test fun enablingSchedulesOneInexactIdleCapableAlarm() = runBlocking {
        scheduler.reconcile()
        val alarm = alarms.scheduledAlarms.single()
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(clock.instant().plusSeconds(3600).toEpochMilli(), alarm.triggerAtTime)
        assertEquals(0L, alarm.interval)
    }
    @Test fun openingAppAfterDeadlineDoesNotPostponeOverdueReminder() = runBlocking {
        scheduler.reconcile()
        val due = repository.settings().nextReminderAt
        clock.current = clock.current.plusSeconds(4500)
        repeat(3) { scheduler.reconcile() }
        assertEquals(due, repository.settings().nextReminderAt)
        assertEquals(due, alarms.scheduledAlarms.single().triggerAtTime)
    }
    @Test fun recordingWaterDoesNotPostponeAnOverdueReminder() = runBlocking {
        scheduler.reconcile()
        val due = repository.settings().nextReminderAt
        clock.current = clock.current.plusSeconds(4500)
        repository.add(250)
        scheduler.reconcile()
        assertEquals(due, repository.settings().nextReminderAt)
    }
    @Test fun deliveredAlarmAdvancesOneIntervalAndRefreshKeepsIt() = runBlocking {
        scheduler.reconcile()
        clock.current = clock.current.plusSeconds(4500)
        scheduler.reconcile(force = true)
        val next = clock.instant().plusSeconds(3600).toEpochMilli()
        assertEquals(next, repository.settings().nextReminderAt)
        scheduler.reconcile()
        assertEquals(next, alarms.scheduledAlarms.single().triggerAtTime)
    }
    @Test fun overdueAlarmDuringQuietHoursMovesToMorning() = runBlocking {
        repository.update { it.copy(quietEnabled = true, quietStart = 1320, quietEnd = 480) }
        scheduler.reconcile()
        clock.current = Instant.parse("2026-10-22T22:00:00Z")
        scheduler.reconcile()
        val morning = java.time.LocalDateTime.parse("2026-10-23T08:00").atZone(zone).toInstant()
        assertEquals(morning.toEpochMilli(), alarms.scheduledAlarms.single().triggerAtTime)
    }
    @Test fun disablingOrRevokingPermissionCancelsAlarm() = runBlocking {
        scheduler.reconcile()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        scheduler.reconcile()
        assertTrue(alarms.scheduledAlarms.isEmpty())
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        scheduler.reconcile()
        assertEquals(1, alarms.scheduledAlarms.size)
        repository.update { it.copy(remindersEnabled = false) }
        scheduler.reconcile()
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
    @Test fun blockedNotificationsPreventScheduling() = runBlocking {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        scheduler.reconcile()
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
    private class MovingClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
    }
}
