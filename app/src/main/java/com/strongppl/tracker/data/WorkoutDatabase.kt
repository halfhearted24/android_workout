package com.strongppl.tracker.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "set_logs", primaryKeys = ["date", "exerciseId", "setNumber"])
data class SetLog(
    val date: String, val exerciseId: String, val exerciseName: String, val setNumber: Int,
    val reps: Int? = null, val weightKg: Double? = null, val rir: Int? = null,
    val band: String? = null, val notes: String? = null, val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workout_sessions", primaryKeys = ["date"])
data class WorkoutSession(
    val date: String, val title: String, val status: String = "IN_PROGRESS",
    val startedAt: Long = System.currentTimeMillis(), val completedAt: Long? = null
)

@Dao interface WorkoutDao {
    @Query("SELECT * FROM set_logs ORDER BY date DESC, exerciseId, setNumber") fun observeAllSets(): Flow<List<SetLog>>
    @Query("SELECT * FROM set_logs WHERE date = :date ORDER BY exerciseId, setNumber") fun observeSetsForDate(date: String): Flow<List<SetLog>>
    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId AND date < :beforeDate ORDER BY date DESC LIMIT 3") suspend fun previousSets(exerciseId: String, beforeDate: String): List<SetLog>
    @Query("SELECT * FROM workout_sessions ORDER BY date DESC") fun observeSessions(): Flow<List<WorkoutSession>>
    @Query("SELECT * FROM workout_sessions WHERE date = :date LIMIT 1") suspend fun session(date: String): WorkoutSession?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSet(log: SetLog)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSession(session: WorkoutSession)
    @Query("DELETE FROM set_logs WHERE date = :date AND exerciseId = :exerciseId AND setNumber = :setNumber") suspend fun deleteSet(date: String, exerciseId: String, setNumber: Int)
    @Query("SELECT * FROM set_logs") suspend fun allSets(): List<SetLog>
    @Query("SELECT * FROM workout_sessions") suspend fun allSessions(): List<WorkoutSession>
    @Query("DELETE FROM set_logs") suspend fun clearSets()
    @Query("DELETE FROM workout_sessions") suspend fun clearSessions()
    @Transaction suspend fun replaceAll(sets: List<SetLog>, sessions: List<WorkoutSession>) {
        clearSets(); clearSessions(); sets.forEach { saveSet(it) }; sessions.forEach { saveSession(it) }
    }
}

@Database(entities = [SetLog::class, WorkoutSession::class], version = 1, exportSchema = true)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    companion object {
        @Volatile private var INSTANCE: WorkoutDatabase? = null
        fun get(context: Context): WorkoutDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, WorkoutDatabase::class.java, "strong_ppl_workouts.db")
                .fallbackToDestructiveMigrationOnDowngrade().build().also { INSTANCE = it }
        }
    }
}
