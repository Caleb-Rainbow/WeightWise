package com.example.weight.data.exercise

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object FallbackPlanGenerator {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun generate(
        difficultyLevel: Int = 2,
        blacklistTags: Set<String> = emptySet(),
        whitelistTags: Set<String> = emptySet(),
    ): DailyPlan {
        val today = LocalDate.now().format(dateFormatter)
        val exercises = selectExercises(difficultyLevel, blacklistTags, whitelistTags)
        val totalCalories = exercises.sumOf { it.estimatedCalories }
        val totalDuration = exercises.sumOf { it.durationMinutes }

        return DailyPlan(
            planDate = today,
            exercisesJson = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer<List<ExerciseItem>>(), exercises
            ),
            aiAdvice = getEncouragement(difficultyLevel),
            difficultyLevel = difficultyLevel,
            createdAt = System.currentTimeMillis(),
            isAiGenerated = false,
            totalCalories = totalCalories,
            totalDuration = totalDuration,
            dailyTip = getTip(),
        )
    }

    private fun selectExercises(
        difficulty: Int,
        blacklistTags: Set<String>,
        whitelistTags: Set<String>,
    ): List<ExerciseItem> {
        val excludedCatalogTags = BlacklistToCatalogTagMap.getExcludedCatalogTags(blacklistTags)
        val preferredCatalogTags = WhitelistToCatalogTagMap.getPreferredCatalogTags(whitelistTags)

        val pool = ExerciseCatalog.getFilteredExercisesForDifficulty(
            level = difficulty,
            excludedTags = excludedCatalogTags,
            preferredTags = preferredCatalogTags,
        ).toMutableList()

        // If all exercises filtered out, use safe exercises — never fall back to unfiltered pool
        if (pool.isEmpty()) {
            return selectSafeExercises()
        }

        val selected = mutableListOf<ExerciseCatalog.CatalogExercise>()

        // Ensure at least one NEAT exercise (prefer from preferred if possible)
        val neatPool = pool.filter { it.category == ExerciseCatalog.Category.NEAT }
        val neatPreferred = neatPool.filter { it.tags.any { t -> t in preferredCatalogTags } }
        val neatExercise = (neatPreferred.ifEmpty { neatPool }).randomOrNull()
        if (neatExercise != null) {
            selected.add(neatExercise)
            pool.remove(neatExercise)
        }

        val remainingCount = (3..4).random() - selected.size
        repeat(remainingCount) {
            if (pool.isNotEmpty()) {
                val exercise = pool.random()
                selected.add(exercise)
                pool.remove(exercise)
            }
        }

        return selected.map { ExerciseCatalog.toExerciseItem(it) }
    }

    private fun selectSafeExercises(): List<ExerciseItem> {
        val safe1 = ExerciseCatalog.getSafeExercise()
        val safe2 = ExerciseCatalog.getSafeExercise()
        val result = mutableListOf<ExerciseCatalog.CatalogExercise>()
        safe1?.let { result.add(it) }
        if (safe2 != null && safe2.name != safe1?.name) {
            result.add(safe2)
        }
        // Ensure at least 3 exercises
        while (result.size < 3) {
            ExerciseCatalog.getSafeExercise()?.let { candidate ->
                if (candidate.name !in result.map { it.name }) {
                    result.add(candidate)
                }
            } ?: break
        }
        return result.map { ExerciseCatalog.toExerciseItem(it) }
    }

    private fun getEncouragement(difficulty: Int): String = when (difficulty) {
        1 -> "今天轻松一点也没关系！每一个小动作都在帮助你变得更好。"
        2 -> "保持节奏，你做得很好！今天的计划刚刚好。"
        3 -> "挑战自己的感觉真棒！记住量力而行，安全第一。"
        else -> "动起来就是胜利！"
    }

    private fun getTip(): String = listOf(
        "多喝水可以增加代谢，每天至少8杯水哦",
        "饭后不要立刻坐下，站一会儿或散步10分钟",
        "保证充足的睡眠对减重很重要，尽量11点前入睡",
        "记录饮食比记录运动更能帮助你控制体重",
        "不要跳过早餐，一顿好的早餐能开启一天的好代谢",
    ).random()
}
