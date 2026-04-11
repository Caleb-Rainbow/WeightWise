package com.example.weight.data.exercise

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface JourneyDao {

    @Insert
    suspend fun insertJourney(journey: Journey): Long

    @Insert
    suspend fun insertPhases(phases: List<Phase>)

    @Query("SELECT * FROM Journey WHERE status = 'active' LIMIT 1")
    suspend fun getActiveJourney(): Journey?

    @Query("SELECT * FROM Journey WHERE status = 'active' LIMIT 1")
    fun getActiveJourneyFlow(): Flow<Journey?>

    @Query("SELECT * FROM Journey WHERE id = :id")
    suspend fun getJourneyById(id: Int): Journey?

    @Query("SELECT * FROM Phase WHERE journey_id = :journeyId ORDER BY phase_index ASC")
    suspend fun getPhasesForJourney(journeyId: Int): List<Phase>

    @Query("SELECT * FROM Phase WHERE journey_id = :journeyId ORDER BY phase_index ASC")
    fun getPhasesForJourneyFlow(journeyId: Int): Flow<List<Phase>>

    @Query("SELECT * FROM Phase WHERE journey_id = :journeyId AND :dayNumber BETWEEN start_day AND end_day LIMIT 1")
    suspend fun getPhaseForDay(journeyId: Int, dayNumber: Int): Phase?

    @Query("UPDATE Journey SET status = :status, completed_at = :completedAt WHERE id = :journeyId")
    suspend fun updateJourneyStatus(journeyId: Int, status: String, completedAt: Long? = null)

    @Query("SELECT COUNT(*) FROM DailyPlan WHERE journey_id = :journeyId")
    suspend fun getPlanCountForJourney(journeyId: Int): Int

    @Query("""
        SELECT COUNT(*) FROM ExerciseCompletion ec
        JOIN DailyPlan dp ON ec.plan_id = dp.id
        WHERE dp.journey_id = :journeyId AND ec.is_completed = 1
    """)
    suspend fun getCompletedExerciseCountForJourney(journeyId: Int): Int

    @Query("""
        SELECT COUNT(*) FROM ExerciseCompletion ec
        JOIN DailyPlan dp ON ec.plan_id = dp.id
        WHERE dp.journey_id = :journeyId AND ec.is_completed = 1
    """)
    fun getCompletedExerciseCountForJourneyFlow(journeyId: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM ExerciseCompletion ec
        JOIN DailyPlan dp ON ec.plan_id = dp.id
        WHERE dp.journey_id = :journeyId
    """)
    suspend fun getTotalExerciseCountForJourney(journeyId: Int): Int

    @Query("""
        SELECT COUNT(*) FROM ExerciseCompletion ec
        JOIN DailyPlan dp ON ec.plan_id = dp.id
        WHERE dp.journey_id = :journeyId
    """)
    fun getTotalExerciseCountForJourneyFlow(journeyId: Int): Flow<Int>

    @Transaction
    suspend fun insertJourneyWithPhases(journey: Journey, phases: List<Phase>): Int {
        val journeyId = insertJourney(journey).toInt()
        val phasesWithId = phases.map { it.copy(journeyId = journeyId) }
        insertPhases(phasesWithId)
        return journeyId
    }
}
