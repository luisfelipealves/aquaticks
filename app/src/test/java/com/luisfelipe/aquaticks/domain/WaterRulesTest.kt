package com.luisfelipe.aquaticks.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WaterRulesTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private fun local(text: String) = java.time.LocalDateTime.parse(text).atZone(lisbon)
    @Test fun progressIsClamped() {
        assertEquals(0f, WaterRules.progress(0, 2000), 0.0001f)
        assertEquals(.375f, WaterRules.progress(750, 2000), 0.0001f)
        assertEquals(1f, WaterRules.progress(2500, 2000), 0.0001f)
        assertEquals(0f, WaterRules.progress(-20, 2000), 0.0001f)
        assertEquals(0f, WaterRules.progress(100, 0), 0.0001f)
    }
    @Test fun overnightQuietHoursIncludeStartExcludeEnd() {
        for (time in listOf("2026-10-20T22:00", "2026-10-21T00:00", "2026-10-21T07:59"))
            assertTrue(WaterRules.quiet(local(time), true, 1320, 480))
        assertFalse(WaterRules.quiet(local("2026-10-21T08:00"), true, 1320, 480))
        assertFalse(WaterRules.quiet(local("2026-10-20T21:59"), true, 1320, 480))
    }
    @Test fun daytimeAndDisabledQuietHours() {
        assertTrue(WaterRules.quiet(local("2026-10-20T13:00"), true, 720, 840))
        assertFalse(WaterRules.quiet(local("2026-10-20T14:00"), true, 720, 840))
        assertFalse(WaterRules.quiet(local("2026-10-20T23:00"), false, 1320, 480))
        assertFalse(WaterRules.quiet(local("2026-10-20T23:00"), true, 480, 480))
    }
    @Test fun reminderSkipsOvernightSilence() {
        val now = local("2026-10-20T21:00").toInstant()
        assertEquals(local("2026-10-21T08:00").toInstant(),
            WaterRules.nextReminder(now, lisbon, 2, true, 1320, 480))
    }
    @Test fun morningCandidateSkipsToSameDayEnd() {
        val now = local("2026-10-21T04:00").toInstant()
        assertEquals(local("2026-10-21T08:00").toInstant(),
            WaterRules.nextReminder(now, lisbon, 2, true, 1320, 480))
    }
    @Test fun intervalsApplyWithoutSilence() {
        val now = Instant.parse("2026-10-20T12:00:00Z")
        for (hours in WaterRules.intervals)
            assertEquals(now.plusSeconds(hours * 3600L),
                WaterRules.nextReminder(now, lisbon, hours, false, 1320, 480))
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidIntervalIsRejected() {
        WaterRules.nextReminder(Instant.now(), lisbon, 0, true, 1320, 480)
    }
    @Test fun dayChangesAtLocalMidnight() {
        assertNotEquals(WaterRules.date(local("2026-10-20T23:59").toInstant(), lisbon),
            WaterRules.date(local("2026-10-21T00:00").toInstant(), lisbon))
    }
    @Test fun zonesCanHaveDifferentCurrentDates() {
        val instant = Instant.parse("2026-10-20T23:30:00Z")
        assertNotEquals(WaterRules.date(instant, ZoneId.of("Europe/Lisbon")),
            WaterRules.date(instant, ZoneId.of("America/New_York")))
    }
    @Test fun midnightWaitRespectsDstDayLength() {
        assertEquals(25 * 3600000L,
            WaterRules.untilMidnight(local("2026-10-25T00:00").toInstant(), lisbon))
        assertEquals(23 * 3600000L,
            WaterRules.untilMidnight(local("2026-03-29T00:00").toInstant(), lisbon))
    }
}
