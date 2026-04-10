package com.example.weight.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.LocalStorageData
import com.example.weight.data.chat.ChatRepository
import com.example.weight.data.exercise.AiPlanResponse
import com.example.weight.data.exercise.DailyPlan
import com.example.weight.data.exercise.ExerciseCatalog
import com.example.weight.data.exercise.ExerciseCompletion
import com.example.weight.data.exercise.BlacklistToCatalogTagMap
import com.example.weight.data.exercise.ExerciseDifficultyAdjuster
import com.example.weight.data.exercise.ExerciseItem
import com.example.weight.data.exercise.ExercisePlanDao
import com.example.weight.data.exercise.ExercisePromptBuilder
import com.example.weight.data.exercise.FallbackPlanGenerator
import com.example.weight.data.exercise.WhitelistToCatalogTagMap
import com.example.weight.data.exercise.sanitize
import com.example.weight.data.record.RecordDao
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.android.annotation.KoinViewModel

data class ExercisePlanUiState(
    val todayPlan: DailyPlan? = null,
    val exercises: List<ExerciseItem> = emptyList(),
    val completions: List<ExerciseCompletion> = emptyList(),
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val error: String? = null,
    val streakDays: Int = 0,
    val allCompleted: Boolean = false,
)

@KoinViewModel
class ExercisePlanViewModel(
    private val recordDao: RecordDao,
    private val exercisePlanDao: ExercisePlanDao,
    private val chatRepository: ChatRepository,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExercisePlanUiState())
    val uiState: StateFlow<ExercisePlanUiState> = _uiState.asStateFlow()

    private val generateMutex = Mutex()

    init {
        loadTodayPlan()
    }

    fun loadTodayPlan() {
        val today = TimeUtils.getCurrentDate()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val plan = exercisePlanDao.getPlanByDate(today)
                if (plan != null) {
                    val exercises = parseExercisesJson(plan.exercisesJson)
                    observeCompletions(plan.id, exercises)
                    _uiState.value = _uiState.value.copy(
                        todayPlan = plan,
                        exercises = exercises,
                        isLoading = false,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                updateStreak()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun generatePlan() {
        viewModelScope.launch(Dispatchers.IO) {
            generateMutex.withLock {
                if (_uiState.value.isGenerating) return@launch
                _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
                try {
                    val today = TimeUtils.getCurrentDate()
                    val existingPlan = exercisePlanDao.getPlanByDate(today)
                    if (existingPlan != null) {
                        _uiState.value = _uiState.value.copy(isGenerating = false)
                        return@launch
                    }
                    doGeneratePlan(today)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isGenerating = false, error = e.message)
                }
            }
        }
    }

    fun forceRegenerate() {
        viewModelScope.launch(Dispatchers.IO) {
            generateMutex.withLock {
                if (_uiState.value.isGenerating) return@launch
                _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
                try {
                    val today = TimeUtils.getCurrentDate()
                    doGeneratePlan(today)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isGenerating = false, error = e.message)
                }
            }
        }
    }

    private suspend fun doGeneratePlan(today: String) {
        val fitnessLevel = LocalStorageData.userFitnessLevel.value
        val height = LocalStorageData.height.value
        val targetWeight = LocalStorageData.targetWeight.value
        val isFirstTime = exercisePlanDao.getPlansSince(today).isEmpty()

        val blacklistTags = LocalStorageData.exerciseBlacklist.value
        val whitelistTags = LocalStorageData.exerciseWhitelist.value
        val scene = LocalStorageData.exerciseScene.value

        val sevenDaysAgo = TimeUtils.getStartTimeForLastDays(7)
        val recentWeights = recordDao.getDailyMinWeightSince(sevenDaysAgo).first()
        val recentRecords = recordDao.getRecordWeightSince(sevenDaysAgo)

        val sinceDate = TimeUtils.convertMillisToDate(sevenDaysAgo)
        val completedCount = exercisePlanDao.getCompletedCountSince(sinceDate)
        val totalCount = exercisePlanDao.getTotalExerciseCountSince(sinceDate)
        val completionRate = ExerciseDifficultyAdjuster.calculateCompletionRate(completedCount, totalCount)

        val recentPlans = exercisePlanDao.getPlansSince(sinceDate)
        val skipRecords = buildSkipRecords(recentPlans)

        val lastRecord = recordDao.getLastData()
        val currentWeight = lastRecord?.weight ?: 0.0
        val bmi = if (height > 0) currentWeight / ((height / 100) * (height / 100)) else 0.0

        val chatBody = ExercisePromptBuilder.buildPrompt(
            height = height,
            currentWeight = currentWeight,
            bmi = bmi,
            targetWeight = targetWeight,
            fitnessLevel = fitnessLevel,
            recentWeights = recentWeights,
            recentRecords = recentRecords,
            completionRate = completionRate,
            skippedExercises = skipRecords,
            isFirstTime = isFirstTime,
            recentPlanDifficulty = recentPlans.firstOrNull()?.difficultyLevel ?: 2,
            blacklistTags = blacklistTags,
            whitelistTags = whitelistTags,
            scene = scene,
        )

        try {
            var responseText = ""
            chatRepository.chat(chatBody) { message ->
                responseText = message.content
            }
            val aiResponse = json.decodeFromString<AiPlanResponse>(responseText)
            val sanitizedExercises = aiResponse.exercises.map { it.sanitize() }
            val difficultyLevel = mapDifficulty(aiResponse.difficulty)

            savePlan(today, sanitizedExercises, aiResponse.encouragement, difficultyLevel, true, aiResponse.dailyTip)
        } catch (e: Exception) {
            val difficulty = if (completionRate >= 0 && completionRate < 0.5f) 1 else fitnessLevel
            val fallbackPlan = FallbackPlanGenerator.generate(
                difficultyLevel = difficulty,
                blacklistTags = blacklistTags,
                whitelistTags = whitelistTags,
            )
            val exercises = parseExercisesJson(fallbackPlan.exercisesJson)
            savePlan(today, exercises, fallbackPlan.aiAdvice, fallbackPlan.difficultyLevel, false, fallbackPlan.dailyTip)
        }
    }

    fun replaceExercise(exerciseId: String) {
        val plan = _uiState.value.todayPlan ?: return
        val exercises = _uiState.value.exercises
        val completions = _uiState.value.completions

        val completion = completions.find { it.exerciseId == exerciseId }
        if (completion?.isCompleted == true) return

        val targetExercise = exercises.find { it.id == exerciseId } ?: return
        val difficulty = ExerciseCatalog.getDifficultyLevel(targetExercise.intensity)
        val excludeNames = exercises.map { it.name }

        val blacklistTags = LocalStorageData.exerciseBlacklist.value
        val whitelistTags = LocalStorageData.exerciseWhitelist.value
        val excludedCatalogTags = BlacklistToCatalogTagMap.getExcludedCatalogTags(blacklistTags)
        val preferredCatalogTags = WhitelistToCatalogTagMap.getPreferredCatalogTags(whitelistTags)

        val replacement = ExerciseCatalog.getRandomExercise(
            difficulty = difficulty,
            excludeNames = excludeNames,
            excludedTags = excludedCatalogTags,
            preferredTags = preferredCatalogTags,
        ) ?: return

        val newExercise = ExerciseCatalog.toExerciseItem(replacement)
        val updatedExercises = exercises.map {
            if (it.id == exerciseId) newExercise else it
        }

        viewModelScope.launch(Dispatchers.IO) {
            val exercisesJson = json.encodeToString(
                kotlinx.serialization.serializer<List<ExerciseItem>>(), updatedExercises
            )
            val totalCalories = updatedExercises.sumOf { it.estimatedCalories }
            val totalDuration = updatedExercises.sumOf { it.durationMinutes }

            val newCompletion = ExerciseCompletion(
                planId = plan.id,
                exerciseId = newExercise.id,
            )

            exercisePlanDao.updatePlanAndCompletion(
                plan.copy(
                    exercisesJson = exercisesJson,
                    totalCalories = totalCalories,
                    totalDuration = totalDuration,
                ),
                exerciseId,
                newCompletion,
            )

            _uiState.value = _uiState.value.copy(exercises = updatedExercises)
        }
    }

    fun toggleExercise(exerciseId: String) {
        val plan = _uiState.value.todayPlan ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = exercisePlanDao.getCompletionsByPlanId(plan.id)
                .find { it.exerciseId == exerciseId }

            if (existing != null) {
                exercisePlanDao.updateCompletion(
                    existing.copy(
                        isCompleted = !existing.isCompleted,
                        completedAt = if (!existing.isCompleted) System.currentTimeMillis() else null,
                        skipped = false,
                        skipReason = null,
                    )
                )
            } else {
                exercisePlanDao.insertCompletions(
                    listOf(
                        ExerciseCompletion(
                            planId = plan.id,
                            exerciseId = exerciseId,
                            isCompleted = true,
                            completedAt = System.currentTimeMillis(),
                        )
                    )
                )
            }
            checkAllCompleted(plan.id)
        }
    }

    fun skipExercise(exerciseId: String, reason: String) {
        val plan = _uiState.value.todayPlan ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = exercisePlanDao.getCompletionsByPlanId(plan.id)
                .find { it.exerciseId == exerciseId }

            if (existing != null) {
                exercisePlanDao.updateCompletion(
                    existing.copy(skipped = true, skipReason = reason, isCompleted = false)
                )
            } else {
                exercisePlanDao.insertCompletions(
                    listOf(
                        ExerciseCompletion(
                            planId = plan.id,
                            exerciseId = exerciseId,
                            skipped = true,
                            skipReason = reason,
                        )
                    )
                )
            }
        }
    }

    private fun observeCompletions(planId: Int, exercises: List<ExerciseItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            exercisePlanDao.getCompletionsForPlan(planId).collect { completions ->
                val allDone = exercises.all { exercise ->
                    completions.any { it.exerciseId == exercise.id && it.isCompleted }
                } && exercises.isNotEmpty()
                _uiState.value = _uiState.value.copy(
                    completions = completions,
                    allCompleted = allDone,
                )
                if (allDone) updateStreak()
            }
        }
    }

    private suspend fun checkAllCompleted(planId: Int) {
        val completions = exercisePlanDao.getCompletionsByPlanId(planId)
        val exercises = _uiState.value.exercises
        val allDone = exercises.all { exercise ->
            completions.any { it.exerciseId == exercise.id && it.isCompleted }
        } && exercises.isNotEmpty()
        if (allDone) {
            updateStreak()
        }
    }

    private suspend fun savePlan(
        date: String,
        exercises: List<ExerciseItem>,
        aiAdvice: String,
        difficultyLevel: Int,
        isAiGenerated: Boolean,
        dailyTip: String,
    ) {
        val exercisesJson = json.encodeToString(
            kotlinx.serialization.serializer<List<ExerciseItem>>(), exercises
        )
        val totalCalories = exercises.sumOf { it.estimatedCalories }
        val totalDuration = exercises.sumOf { it.durationMinutes }

        val plan = DailyPlan(
            planDate = date,
            exercisesJson = exercisesJson,
            aiAdvice = aiAdvice,
            difficultyLevel = difficultyLevel,
            createdAt = System.currentTimeMillis(),
            isAiGenerated = isAiGenerated,
            totalCalories = totalCalories,
            totalDuration = totalDuration,
            dailyTip = dailyTip,
        )
        val planId = exercisePlanDao.insertPlan(plan)
        val savedPlan = plan.copy(id = planId.toInt())

        // 新计划写入成功后，再删除同一天的旧计划（CASCADE 会自动清理旧完成记录）
        exercisePlanDao.deleteOldPlansByDate(date, planId.toInt())

        val completions = exercises.map { exercise ->
            ExerciseCompletion(planId = planId.toInt(), exerciseId = exercise.id)
        }
        exercisePlanDao.insertCompletions(completions)

        _uiState.value = _uiState.value.copy(
            todayPlan = savedPlan,
            exercises = exercises,
            isGenerating = false,
        )
        observeCompletions(planId.toInt(), exercises)
    }

    private fun updateStreak() = viewModelScope.launch(Dispatchers.IO) {
        val today = TimeUtils.getCurrentDate()
        var streak = 0
        var checkDate = today
        while (true) {
            val plan = exercisePlanDao.getPlanByDate(checkDate)
            if (plan == null) break
            val completions = exercisePlanDao.getCompletionsByPlanId(plan.id)
            val hasCompleted = completions.any { it.isCompleted }
            if (!hasCompleted) break
            streak++
            checkDate = subtractDay(checkDate)
        }
        _uiState.value = _uiState.value.copy(streakDays = streak)
    }

    private fun subtractDay(dateStr: String): String {
        val date = java.time.LocalDate.parse(dateStr)
        return date.minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    private suspend fun buildSkipRecords(recentPlans: List<DailyPlan>): List<ExercisePromptBuilder.SkipRecord> {
        val skipRecords = mutableListOf<ExercisePromptBuilder.SkipRecord>()
        for (plan in recentPlans) {
            val exercises = parseExercisesJson(plan.exercisesJson)
            val completions = exercisePlanDao.getCompletionsByPlanId(plan.id)
            for (completion in completions) {
                if (completion.skipped && completion.skipReason != null) {
                    val exerciseName = exercises.find { it.id == completion.exerciseId }?.name ?: "未知运动"
                    skipRecords.add(
                        ExercisePromptBuilder.SkipRecord(
                            date = plan.planDate,
                            exerciseName = exerciseName,
                            reason = completion.skipReason,
                        )
                    )
                }
            }
        }
        return skipRecords
    }

    private fun mapDifficulty(difficulty: String): Int = when (difficulty) {
        "轻松" -> 1
        "适中" -> 2
        "挑战" -> 3
        else -> 2
    }

    private fun parseExercisesJson(exercisesJson: String): List<ExerciseItem> {
        return try {
            json.decodeFromString<List<ExerciseItem>>(exercisesJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
