package com.luisfelipe.aquaticks.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WholeHourRemindersTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private fun local(value: String) = LocalDateTime.parse(value).atZone(lisbon).toInstant()

    @Test fun activationAt1243StartsAt1300ForEveryInterval() {
        for (interval in 1..4) assertEquals("interval=$interval", local("2026-10-23T13:00"),
            WaterRules.nextReminder(local("2026-10-23T12:43"), lisbon, interval, false, 1320, 480))
    }
    @Test fun activationExactlyOnHourStartsAtFollowingHour() {
        assertEquals(local("2026-10-23T14:00"),
            WaterRules.nextReminder(local("2026-10-23T13:00"), lisbon, 3, false, 1320, 480))
    }
    @Test fun silenceEndingAt0830ResumesAt0900() {
        assertEquals(local("2026-10-24T09:00"),
            WaterRules.nextReminder(local("2026-10-23T22:43"), lisbon, 2, true, 1320, 510))
    }
    @Test fun noWholeHourOutsideSilenceProducesNoReminder() {
        assertNull(WaterRules.nextReminder(local("2026-10-23T12:43"), lisbon,
            1, true, 0, 1410))
    }
    @Test fun springGapUsesNextValidWholeHour() {
        assertEquals(local("2026-03-29T02:00"),
            WaterRules.nextReminder(local("2026-03-29T00:43"), lisbon, 1, false, 1320, 480))
    }
    @Test fun autumnOverlapDoesNotRepeatTheSameLocalHour() {
        val firstOneOClock = local("2026-10-25T01:00")
        assertEquals(local("2026-10-25T02:00"),
            WaterRules.nextReminder(firstOneOClock, lisbon, 1, false, 1320, 480))
    }
    @Test fun delayedRecoveryKeepsTheQuietResumedCadence() {
        val previous = local("2026-10-23T21:00")
        val morning = WaterRules.nextReminder(previous, lisbon, 4, true, 1320, 480, previous)
        val expected = WaterRules.nextReminder(morning!!, lisbon, 4, true, 1320, 480, morning)
        assertEquals(local("2026-10-24T08:00"), morning)
        assertEquals(local("2026-10-24T12:00"), expected)
        assertEquals(expected, WaterRules.nextReminder(local("2026-10-24T09:00"),
            lisbon, 4, true, 1320, 480, previous))
    }
    @Test fun delayedRecoveryKeepsTheSpringGapAdjustedCadence() {
        val previous = local("2026-03-28T21:00")
        val afterGap = WaterRules.nextReminder(previous, lisbon, 4, false, 1320, 480, previous)
        assertEquals(local("2026-03-29T02:00"), afterGap)
        assertEquals(local("2026-03-29T06:00"), WaterRules.nextReminder(
            local("2026-03-29T03:00"), lisbon, 4, false, 1320, 480, previous))
    }
    @Test fun fractionalOffsetZoneUsesItsLocalWholeHour() {
        val zone = ZoneId.of("Asia/Kathmandu")
        val now = LocalDateTime.parse("2026-10-23T12:43").atZone(zone).toInstant()
        val expected = LocalDateTime.parse("2026-10-23T13:00").atZone(zone).toInstant()
        assertEquals(expected, WaterRules.nextReminder(now, zone, 1, false, 1320, 480))
    }
}
