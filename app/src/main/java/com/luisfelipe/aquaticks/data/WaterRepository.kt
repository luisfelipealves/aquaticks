package com.luisfelipe.aquaticks.data

import androidx.room.withTransaction
import com.luisfelipe.aquaticks.domain.WaterRules
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.combine

data class DaySnapshot(
    val day: LocalDate,
    val settings: Settings,
    val goalMl: Int,
    val intakes: List<Intake>
) {
    val totalMl: Int get() = intakes.sumOf { it.amountMl }
    val progress: Float get() = WaterRules.progress(totalMl, goalMl)
}
class WaterRepository(
    private val db: WaterDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {
    private val dao = db.dao()
    fun today(): LocalDate = WaterRules.date(clock.instant(), zone())
    suspend fun settings(): Settings = db.withTransaction {
        dao.initSettings(Settings())
        requireNotNull(dao.settings())
    }
    suspend fun ensureToday() = db.withTransaction {
        val settings = settings()
        dao.initGoal(DailyGoal(today().toString(), settings.goalMl))
    }
    fun observe(day: LocalDate) = combine(
        dao.watchSettings(), dao.watchGoal(day.toString()), dao.watchIntakes(day.toString())
    ) { prefs, goal, entries ->
        val settings = prefs ?: Settings()
        DaySnapshot(day, settings, goal?.goalMl ?: settings.goalMl, entries)
    }
    suspend fun snapshot(day: LocalDate = today()): DaySnapshot = db.withTransaction {
        val settings = settings()
        DaySnapshot(day, settings, dao.goal(day.toString())?.goalMl ?: settings.goalMl,
            dao.intakes(day.toString()))
    }
    suspend fun add(amount: Int, operationId: String = UUID.randomUUID().toString()): Boolean {
        require(amount in WaterRules.amounts) { "Unsupported water amount" }
        require(operationId.isNotBlank())
        // Capture instant + zone together so a transaction spanning midnight has one stable date.
        val instant = clock.instant()
        val currentZone = zone()
        val day = WaterRules.date(instant, currentZone).toString()
        return db.withTransaction {
            val settings = settings()
            dao.initGoal(DailyGoal(day, settings.goalMl))
            dao.insert(Intake(operationId, amount, instant.toEpochMilli(), day, currentZone.id)) != -1L
        }
    }
    suspend fun update(transform: (Settings) -> Settings) = db.withTransaction {
        val previous = settings()
        val updated = transform(previous).copy(id = 0)
        require(updated.goalMl in WaterRules.MIN_GOAL..WaterRules.MAX_GOAL)
        require(updated.defaultMl in WaterRules.amounts)
        require(updated.intervalHours in WaterRules.intervals)
        require(updated.quietStart in 0..1439 && updated.quietEnd in 0..1439)
        dao.saveSettings(updated)
        if (previous.goalMl != updated.goalMl) {
            dao.saveGoal(DailyGoal(today().toString(), updated.goalMl))
        } else {
            dao.initGoal(DailyGoal(today().toString(), updated.goalMl))
        }
    }
}
