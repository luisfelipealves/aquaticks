package com.luisfelipe.aquaticks.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.luisfelipe.aquaticks.AquaTicksApp
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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
        val before = Instant.now()
        receiver.processAction(app, reminder())
        val notification = requireNotNull(notifications.getNotification(ReminderScheduler.NOTIFICATION_ID))
        assertEquals("Hora de beber água!", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("Registre +300 ml com um toque.", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(ReminderScheduler.CHANNEL, notification.channelId)
        assertNotNull(notification.contentIntent)
        assertEquals("+300 ml", notification.actions.single().title)
        val due = app.repository.settings().nextReminderAt
        assertTrue(due >= before.plusSeconds(3600).toEpochMilli())
        assertTrue(due <= Instant.now().plusSeconds(3600).toEpochMilli())
        assertEquals(due, alarms.scheduledAlarms.single().triggerAtTime)
        assertTrue(alarms.scheduledAlarms.single().isAllowWhileIdle)
        assertEquals(0, app.repository.snapshot().totalMl)
    }
    @Test fun notificationActionRegistersDefaultAmountOnce() = runBlocking {
        receiver.processAction(app, reminder())
        val notification = requireNotNull(notifications.getNotification(ReminderScheduler.NOTIFICATION_ID))
        val action = shadowOf(notification.actions.single().actionIntent).savedIntent
        receiver.processAction(app, action)
        receiver.processAction(app, action)
        assertEquals(300, app.repository.snapshot().totalMl)
        assertEquals(1, app.repository.snapshot().intakes.size)
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
