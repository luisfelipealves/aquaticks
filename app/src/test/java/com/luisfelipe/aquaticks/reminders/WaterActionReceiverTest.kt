package com.luisfelipe.aquaticks.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.ComponentName
import android.content.IntentFilter
import android.net.Uri
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.luisfelipe.aquaticks.AquaTicksApp
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBroadcastPendingResult
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AquaTicksApp::class)
class WaterActionReceiverTest {
    private lateinit var app: AquaTicksApp
    private val receiver = WaterActionReceiver()
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private fun reminder() = Intent(app, WaterActionReceiver::class.java)
        .setAction(ReminderScheduler.ACTION_REMIND)

    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.reminders.createChannel()
        app.repository.update { it.copy(remindersEnabled = true, intervalHours = 1,
            quietEnabled = false, defaultMl = 300) }
        app.repository.update { it.copy(nextReminderAt = Instant.now().minusSeconds(600).toEpochMilli()) }
    }

    @Test fun deliveryPostsReminderAndArmsNextAlarm() = runBlocking {
        val nextHour = ZonedDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant()
        receiver.processAction(app, reminder())
        val notification = requireNotNull(notifications.getNotification(ReminderScheduler.NOTIFICATION_ID))
        assertEquals("Hora de beber água!", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("Registre +300 ml com um toque.", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(ReminderScheduler.CHANNEL, notification.channelId)
        assertNotNull(notification.contentIntent)
        assertEquals("+300 ml", notification.actions.single().title)
        assertEquals(0, notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        val due = app.repository.settings().nextReminderAt
        assertEquals(nextHour.toEpochMilli(), due)
        assertEquals(due, alarms.scheduledAlarms.single().triggerAtTime)
        assertTrue(alarms.scheduledAlarms.single().isAllowWhileIdle)
        assertEquals(0, app.repository.snapshot().totalMl)
    }
    @Test fun nextAlarmIsArmedBeforeNotificationFailure() = runBlocking {
        val failure = runCatching {
            receiver.processAction(app, reminder()) { _, _ ->
                assertEquals(1, alarms.scheduledAlarms.size)
                throw IllegalStateException("Simulated notification failure")
            }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(app.repository.settings().nextReminderAt > Instant.now().toEpochMilli())
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(0L, ReminderDiagnostics(app).snapshot().postedAt)
    }
    @Test fun cancelledPostingDoesNotCancelTheNextAlarm() = runBlocking {
        val task = launch {
            val job = currentCoroutineContext().job
            receiver.processAction(app, reminder()) { _, _ ->
                job.cancel()
                job.ensureActive()
            }
        }
        task.join()
        assertTrue(task.isCancelled)
        val due = app.repository.settings().nextReminderAt
        assertTrue(due > Instant.now().toEpochMilli())
        assertEquals(due, alarms.scheduledAlarms.single().triggerAtTime)
        assertEquals(0L, ReminderDiagnostics(app).snapshot().postedAt)
    }
    @Test fun alarmPendingIntentRunsAsyncReceiverTwiceWithoutOpeningActivity() = runBlocking {
        val info = app.packageManager.getReceiverInfo(ComponentName(app, WaterActionReceiver::class.java), 0)
        assertFalse(info.exported)
        // Robolectric does not auto-register manifest receivers without intent filters.
        // Keep production's explicit-only receiver, register its real instance in the harness.
        ContextCompat.registerReceiver(app, receiver, IntentFilter(ReminderScheduler.ACTION_REMIND),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        app.reminders.reconcile()
        var previousToken: String? = null
        try { repeat(2) {
            alarms.fireAlarm(alarms.scheduledAlarms.single())
            awaitFinishedBroadcast()
            val diagnostic = ReminderDiagnostics(app).snapshot()
            assertTrue(diagnostic.receivedAt > 0)
            assertTrue(diagnostic.postedAt > 0)
            assertEquals(0L, diagnostic.failedAt)
            assertEquals(diagnostic.scheduledAt, alarms.scheduledAlarms.single().triggerAtTime)
            assertTrue(diagnostic.scheduledAt > Instant.now().toEpochMilli())
            val notification = requireNotNull(notifications.getNotification(ReminderScheduler.NOTIFICATION_ID))
            assertEquals(0, notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
            val token = shadowOf(notification.actions.single().actionIntent).savedIntent.dataString
            assertNotEquals(previousToken, token)
            previousToken = token
            assertNull(shadowOf(app).nextStartedActivity)
        } } finally { app.unregisterReceiver(receiver) }
    }
    @Test fun asyncFailureStillFinishesBroadcastAndDoesNotRecordWater() = runBlocking {
        ContextCompat.registerReceiver(app, receiver, IntentFilter(ReminderScheduler.ACTION_ADD),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            app.sendBroadcast(Intent(app, WaterActionReceiver::class.java)
                .setAction(ReminderScheduler.ACTION_ADD)
                .setData(Uri.parse("aquaticks://intake/invalid-amount"))
                .putExtra("amount", 0))
            awaitFinishedBroadcast()
            assertEquals(0, app.repository.snapshot().totalMl)
        } finally { app.unregisterReceiver(receiver) }
    }
    private fun awaitFinishedBroadcast() {
        shadowOf(Looper.getMainLooper()).idle()
        val pending = requireNotNull(shadowOf(receiver).originalPendingResult)
        Shadow.extract<ShadowBroadcastPendingResult>(pending).future.get(5, TimeUnit.SECONDS)
    }
    @Test fun notificationActionRegistersDefaultAmountOnce() = runBlocking {
        receiver.processAction(app, reminder())
        val notification = requireNotNull(notifications.getNotification(ReminderScheduler.NOTIFICATION_ID))
        val action = shadowOf(notification.actions.single().actionIntent).savedIntent
        receiver.processAction(app, action)
        assertEquals(0, notifications.size())
        receiver.processAction(app, action)
        assertEquals(300, app.repository.snapshot().totalMl)
        assertEquals(1, app.repository.snapshot().intakes.size)
        assertEquals(0, notifications.size())
    }
    @Test fun quietHoursSuppressDelayedDeliveryButKeepFutureAlarm() = runBlocking {
        val local = ZonedDateTime.now()
        val minute = local.hour * 60 + local.minute
        app.repository.update { it.copy(quietEnabled = true, quietStart = minute,
            quietEnd = (minute + 30) % 1440) }
        receiver.processAction(app, reminder())
        assertEquals(0, notifications.size())
        assertTrue(app.repository.settings().nextReminderAt > Instant.now().toEpochMilli())
        assertEquals(1, alarms.scheduledAlarms.size)
    }
    @Test fun revokedPermissionSuppressesNotificationAndCancelsSchedule() = runBlocking {
        app.reminders.reconcile()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        receiver.processAction(app, reminder())
        assertEquals(0, notifications.size())
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
    @Test fun disabledRemindersSuppressLateBroadcast() = runBlocking {
        app.repository.update { it.copy(remindersEnabled = false) }
        receiver.processAction(app, reminder())
        assertEquals(0, notifications.size())
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
}
