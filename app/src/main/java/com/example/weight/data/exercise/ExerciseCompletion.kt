package com.example.weight.data.exercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ExerciseCompletion",
    foreignKeys = [ForeignKey(
        entity = DailyPlan::class,
        parentColumns = ["id"],
        childColumns = ["plan_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("plan_id"), Index("exercise_id")]
)
data class ExerciseCompletion(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "plan_id")
    val planId: Int,
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    val skipped: Boolean = false,
    @ColumnInfo(name = "skip_reason")
    val skipReason: String? = null,
)
