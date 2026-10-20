package com.luisfelipe.aquaticks.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 0,
    val goalMl: Int = 2000,
    val defaultMl: Int = 250,
    val remindersEnabled: Boolean = false,
    val intervalHours: Int = 2,
    val quietEnabled: Boolean = true,
    val quietStart: Int = 1320,
    val quietEnd: Int = 480
)
@Entity(tableName = "intakes", indices = [Index("localDate")])
data class Intake(
    @PrimaryKey val id: String,
    val amountMl: Int,
    val timestamp: Long,
    val localDate: String,
    val zoneId: String
)
@Entity(tableName = "daily_goals")
data class DailyGoal(@PrimaryKey val localDate: String, val goalMl: Int)

@Dao
interface WaterDao {
    @Query("SELECT * FROM settings WHERE id = 0") suspend fun settings(): Settings?
    @Query("SELECT * FROM settings WHERE id = 0") fun watchSettings(): Flow<Settings?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun initSettings(settings: Settings)
    @Upsert suspend fun saveSettings(settings: Settings)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(intake: Intake): Long
    @Query("SELECT * FROM intakes WHERE localDate = :day ORDER BY timestamp DESC")
    fun watchIntakes(day: String): Flow<List<Intake>>
    @Query("SELECT * FROM intakes WHERE localDate = :day ORDER BY timestamp DESC")
    suspend fun intakes(day: String): List<Intake>
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM intakes WHERE localDate = :day")
    suspend fun total(day: String): Int
    @Query("SELECT * FROM daily_goals WHERE localDate = :day")
    suspend fun goal(day: String): DailyGoal?
    @Query("SELECT * FROM daily_goals WHERE localDate = :day")
    fun watchGoal(day: String): Flow<DailyGoal?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun initGoal(goal: DailyGoal)
    @Upsert suspend fun saveGoal(goal: DailyGoal)
}
@Database(entities = [Settings::class, Intake::class, DailyGoal::class], version = 1, exportSchema = true)
abstract class WaterDatabase : RoomDatabase() {
    abstract fun dao(): WaterDao
}
