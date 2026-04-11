package com.example.weight.data.exercise

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExercisePlanDao {

    @Insert
    suspend fun insertPlan(plan: DailyPlan): Long

    @Insert
    suspend fun insertCompletions(completions: List<ExerciseCompletion>)

    @Update
    suspend fun updateCompletion(completion: ExerciseCompletion)

    @Query("DELETE FROM ExerciseCompletion WHERE plan_id = :planId AND exercise_id = :exerciseId")
    suspend fun deleteCompletion(planId: Int, exerciseId: String)

    @Query("DELETE FROM DailyPlan WHERE plan_date = :date AND id != :excludePlanId")
    suspend fun deleteOldPlansByDate(date: String, excludePlanId: Int)

    @Query("SELECT * FROM DailyPlan WHERE plan_date = :date LIMIT 1")
    suspend fun getPlanByDate(date: String): DailyPlan?

    @Query("SELECT * FROM ExerciseCompletion WHERE plan_id = :planId ORDER BY exercise_id ASC")
    fun getCompletionsForPlan(planId: Int): Flow<List<ExerciseCompletion>>

    @Query("SELECT * FROM ExerciseCompletion WHERE plan_id = :planId ORDER BY exercise_id ASC")
    suspend fun getCompletionsByPlanId(planId: Int): List<ExerciseCompletion>

    @Query("SELECT * FROM DailyPlan WHERE plan_date >= :sinceDate ORDER BY plan_date DESC")
    suspend fun getPlansSince(sinceDate: String): List<DailyPlan>

    @Query("""
        SELECT COUNT(*) FROM ExerciseCompletion ec
        JOIN DailyPlan dp ON ec.plan_id = dp.id
        WHERE dp.plan_date >= :sinceDate AND ec.is_completed = 1
    """)
    suspend fun getCompletedCountSince(sinceDate: String): Int

    @Query("""
        SELECT COUNT(*) FROM ExerciseCompletion ec
        JOIN DailyPlan dp ON ec.plan_id = dp.id
        WHERE dp.plan_date >= :sinceDate
    """)
    suspend fun getTotalExerciseCountSince(sinceDate: String): Int

    @Query("""
        SELECT COUNT(DISTINCT dp.plan_date) FROM DailyPlan dp
        JOIN ExerciseCompletion ec ON dp.id = ec.plan_id
        WHERE ec.is_completed = 1 AND dp.plan_date >= :sinceDate
    """)
    suspend fun getActiveDaysSince(sinceDate: String): Int

    @Transaction
    suspend fun updatePlanAndCompletion(plan: DailyPlan, oldExerciseId: String, newCompletion: ExerciseCompletion) {
        updatePlanJson(plan.id, plan.exercisesJson, plan.totalCalories, plan.totalDuration)
        deleteCompletion(plan.id, oldExerciseId)
        insertCompletions(listOf(newCompletion))
    }

    @Query("UPDATE DailyPlan SET exercisesJson = :exercisesJson, total_calories = :totalCalories, total_duration = :totalDuration WHERE id = :planId")
    suspend fun updatePlanJson(planId: Int, exercisesJson: String, totalCalories: Int, totalDuration: Int)

    @Query("SELECT * FROM DailyPlan WHERE journey_id = :journeyId ORDER BY plan_date ASC")
    suspend fun getPlansForJourney(journeyId: Int): List<DailyPlan>

    @Query("SELECT * FROM DailyPlan WHERE journey_id = :journeyId ORDER BY plan_date ASC")
    fun getPlansForJourneyFlow(journeyId: Int): Flow<List<DailyPlan>>

    @Query("UPDATE DailyPlan SET journey_id = :journeyId WHERE id = :planId AND journey_id = 0")
    suspend fun updatePlanJourneyId(planId: Int, journeyId: Int)
}
