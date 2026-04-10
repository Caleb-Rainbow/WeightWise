package com.example.weight.data.exercise

object ExerciseDifficultyAdjuster {

    fun calculateCompletionRate(completed: Int, total: Int): Float {
        if (total == 0) return -1f
        return completed.toFloat() / total
    }

    fun shouldReduceDifficulty(completionRateLast3Days: Float): Boolean {
        if (completionRateLast3Days < 0) return false
        return completionRateLast3Days < 0.5f
    }

    fun shouldIncreaseDifficulty(
        completionRateLast5Days: Float,
        currentLevel: Int,
    ): Boolean {
        if (currentLevel >= 3) return false
        return completionRateLast5Days > 0.8f
    }

    fun getDifficultyLabel(level: Int): String = when (level) {
        1 -> "轻松"
        2 -> "适中"
        3 -> "挑战"
        else -> "适中"
    }
}
