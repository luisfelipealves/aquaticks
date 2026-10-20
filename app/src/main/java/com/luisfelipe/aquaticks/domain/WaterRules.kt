package com.luisfelipe.aquaticks.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object WaterRules {
    val amounts = listOf(100, 200, 250, 300, 500)
    val intervals = listOf(1, 2, 3, 4)
    const val MIN_GOAL = 250
    const val MAX_GOAL = 10000
    fun progress(total: Int, goal: Int): Float =
        if (goal <= 0) 0f else (total.toFloat() / goal).coerceIn(0f, 1f)
    fun date(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()
    fun untilMidnight(now: Instant, zone: ZoneId): Long =
        Duration.between(now, date(now, zone).plusDays(1).atStartOfDay(zone).toInstant())
            .toMillis().coerceAtLeast(1L)
    fun quiet(now: ZonedDateTime, enabled: Boolean, start: Int, end: Int): Boolean {
        if (!enabled || start == end) return false
        val minute = now.hour * 60 + now.minute
        return if (start < end) minute >= start && minute < end
        else minute >= start || minute < end
    }
    fun nextReminder(now: Instant, zone: ZoneId, intervalHours: Int,
        quietEnabled: Boolean, start: Int, end: Int): Instant {
        require(intervalHours in intervals)
        require(start in 0..1439 && end in 0..1439)
        var candidate = now.plusSeconds(intervalHours * 3600L).atZone(zone)
        if (quiet(candidate, quietEnabled, start, end)) {
            var endDate = candidate.toLocalDate()
            val minute = candidate.hour * 60 + candidate.minute
            if (start > end && minute >= start) endDate = endDate.plusDays(1)
            candidate = endDate.atStartOfDay(zone).withHour(end / 60).withMinute(end % 60)
                .withSecond(0).withNano(0)
        }
        // A DST transition can skip/overlap an hour. Never schedule in the past.
        return candidate.toInstant().takeIf { it > now } ?: now.plusSeconds(60)
    }
}
