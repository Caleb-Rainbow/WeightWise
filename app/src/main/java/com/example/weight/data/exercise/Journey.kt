package com.example.weight.data.exercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "Journey")
data class Journey(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "start_weight")
    val startWeight: Double,
    @ColumnInfo(name = "target_weight")
    val targetWeight: Double,
    @ColumnInfo(name = "target_days")
    val targetDays: Int,
    @ColumnInfo(name = "start_date")
    val startDate: String,
    @ColumnInfo(name = "phases_json")
    val phasesJson: String,
    val status: String = "active",
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "ai_advice")
    val aiAdvice: String = "",
)

@Entity(
    tableName = "Phase",
    foreignKeys = [ForeignKey(
        entity = Journey::class,
        parentColumns = ["id"],
        childColumns = ["journey_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("journey_id")],
)
data class Phase(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "journey_id")
    val journeyId: Int,
    @ColumnInfo(name = "phase_index")
    val phaseIndex: Int,
    val name: String,
    val description: String,
    @ColumnInfo(name = "start_day")
    val startDay: Int,
    @ColumnInfo(name = "end_day")
    val endDay: Int,
    @ColumnInfo(name = "target_weight_loss")
    val targetWeightLoss: Double,
    @ColumnInfo(name = "focus_areas")
    val focusAreas: String,
    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: Int,
    @ColumnInfo(name = "daily_calorie_deficit")
    val dailyCalorieDeficit: Int,
    @ColumnInfo(name = "daily_exercise_duration")
    val dailyExerciseDuration: Int,
)

@Serializable
data class AiJourneyResponse(
    val phases: List<AiPhaseDefinition>,
    val overallAdvice: String,
)

@Serializable
data class AiPhaseDefinition(
    val name: String,
    val description: String,
    val startDay: Int,
    val endDay: Int,
    val targetWeightLoss: Double,
    val focusAreas: List<String>,
    val difficultyLevel: Int,
    val dailyCalorieDeficit: Int,
    val dailyExerciseDuration: Int,
)
