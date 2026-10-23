package com.luisfelipe.aquaticks.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

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
        quietEnabled: Boolean, start: Int, end: Int, previousTarget: Instant? = null): Instant? {
        require(intervalHours in intervals)
        require(start in 0..1439 && end in 0..1439)
        val localNow = now.atZone(zone).toLocalDateTime()
        val previous = previousTarget?.takeIf { isWholeHour(it, zone) }
            ?.atZone(zone)?.toLocalDateTime()
        var candidate = previous?.plusHours(intervalHours.toLong())
            ?: localNow.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        if (candidate <= localNow) {
            val missedHours = Duration.between(candidate, localNow).toHours()
            candidate = candidate.plusHours((missedHours / intervalHours + 1) * intervalHours)
        }
        // Local calendar arithmetic skips a repeated DST hour. Gaps may resolve to a
        // half hour in some zones; only accept an actual whole hour after resolution.
        // Scan at most two days: some quiet windows leave no eligible whole hour.
        repeat(48) {
            val resolved = candidate.atZone(zone)
            if (resolved.toInstant() > now && isWholeHour(resolved.toInstant(), zone) &&
                !quiet(resolved, quietEnabled, start, end)) return resolved.toInstant()
            candidate = candidate.plusHours(1)
        }
        return null
    }
    fun isWholeHour(time: Instant, zone: ZoneId): Boolean = time.atZone(zone).let {
        it.minute == 0 && it.second == 0 && it.nano == 0
    }
}
