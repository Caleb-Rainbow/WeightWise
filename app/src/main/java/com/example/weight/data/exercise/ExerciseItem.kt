package com.example.weight.data.exercise

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ExerciseItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val durationMinutes: Int,
    val estimatedCalories: Int,
    val category: String,
    val intensity: String,
)

@Serializable
data class AiPlanResponse(
    val difficulty: String,
    val encouragement: String,
    val exercises: List<ExerciseItem>,
    val dailyTip: String,
)

fun ExerciseItem.sanitize(): ExerciseItem = copy(
    durationMinutes = durationMinutes.coerceIn(5, 60),
    estimatedCalories = estimatedCalories.coerceIn(10, 300),
    id = id.ifBlank { UUID.randomUUID().toString() },
)
