package com.example.weight.data.exercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DailyPlan")
data class DailyPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "plan_date")
    val planDate: String,
    val exercisesJson: String,
    val aiAdvice: String,
    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "is_ai_generated")
    val isAiGenerated: Boolean,
    @ColumnInfo(name = "total_calories", defaultValue = "0")
    val totalCalories: Int = 0,
    @ColumnInfo(name = "total_duration", defaultValue = "0")
    val totalDuration: Int = 0,
    val dailyTip: String = "",
)
