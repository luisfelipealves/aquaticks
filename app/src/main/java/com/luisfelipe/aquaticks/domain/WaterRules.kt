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
        // Replay calendar transitions without posting missed reminders. Skipping
        // interval slots first would lose the phase established by silence or a DST gap.
        // Normally this visits one slot; recovery visits only the elapsed schedule.
        var excludedHours = 0
        while (true) {
            val resolved = candidate.atZone(zone)
            if (isWholeHour(resolved.toInstant(), zone) &&
                !quiet(resolved, quietEnabled, start, end)) {
                if (resolved.toInstant() > now) return resolved.toInstant()
                candidate = resolved.toLocalDateTime().plusHours(intervalHours.toLong())
                excludedHours = 0
            } else {
                // A quiet window can leave no eligible whole hour. Bound that search
                // to two days, including zones whose DST gap resolves to a half hour.
                if (++excludedHours >= 48) return null
                candidate = candidate.plusHours(1)
            }
        }
    }
    fun isWholeHour(time: Instant, zone: ZoneId): Boolean = time.atZone(zone).let {
        it.minute == 0 && it.second == 0 && it.nano == 0
    }
}
