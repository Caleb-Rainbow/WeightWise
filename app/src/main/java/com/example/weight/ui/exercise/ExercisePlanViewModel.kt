package com.example.weight.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.LocalStorageData
import com.example.weight.data.chat.ChatRepository
import com.example.weight.data.diet.DietRecordDao
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
import com.example.weight.data.exercise.JourneyDao
import com.example.weight.data.exercise.WhitelistToCatalogTagMap
import com.example.weight.data.exercise.sanitize
import com.example.weight.data.record.RecordDao
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val journeyContext: ExercisePromptBuilder.JourneyContext? = null,
    val journeyPhaseName: String? = null,
)

@KoinViewModel
class ExercisePlanViewModel(
    private val recordDao: RecordDao,
    private val exercisePlanDao: ExercisePlanDao,
    private val chatRepository: ChatRepository,
    private val json: Json,
    private val journeyDao: JourneyDao,
    private val dietRecordDao: DietRecordDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExercisePlanUiState())
    val uiState: StateFlow<ExercisePlanUiState> = _uiState.asStateFlow()

    private val generateMutex = Mutex()

    private var journeyObserverJob: Job? = null

    init {
        loadTodayPlan()
        observeActiveJourney()
    }

    /**
     * 响应式观察活跃旅程变化。
     * 当用户在 JourneyProgressScreen 放弃/完成旅程后返回此页时，
     * journeyContext 和 journeyPhaseName 会自动清空，PhaseBanner 消失。
     */
    private fun observeActiveJourney() {
        journeyObserverJob?.cancel()
        journeyObserverJob = viewModelScope.launch(Dispatchers.IO) {
            journeyDao.getActiveJourneyFlow().collect { journey ->
                if (journey == null) {
                    _uiState.value = _uiState.value.copy(
                        journeyContext = null,
                        journeyPhaseName = null,
                    )
                } else {
                    // 旅程存在，重新加载完整上下文
                    reloadJourneyContext(journey)
                }
            }
        }
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
                loadJourneyContext()
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
        // 确保 Journey 上下文已加载
        loadJourneyContext()

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

        // 获取昨日饮食数据
        val yesterday = TimeUtils.getCurrentDate().let { today ->
            val date = java.time.LocalDate.parse(today)
            date.minusDays(1).toString()
        }
        val yesterdayDietRecords = try {
            dietRecordDao.getByDateOnce(yesterday)
        } catch (_: Exception) {
            emptyList()
        }
        val dietContext = if (yesterdayDietRecords.isNotEmpty()) {
            ExercisePromptBuilder.DietContext(
                yesterdayTotalCalories = yesterdayDietRecords.sumOf { it.estimatedCalories },
                yesterdayRedLightCount = yesterdayDietRecords.count { it.trafficLight == "RED" },
            )
        } else null

        val chatBody = ExercisePromptBuilder.buildPrompt(
            height = height,
            currentWeight = currentWeight,
            bmi = bmi,
            targetWeight = targetWeight,
            recentWeights = recentWeights,
            recentRecords = recentRecords,
            completionRate = completionRate,
            skippedExercises = skipRecords,
            isFirstTime = isFirstTime,
            blacklistTags = blacklistTags,
            whitelistTags = whitelistTags,
            scene = scene,
            journeyContext = _uiState.value.journeyContext,
            dietContext = dietContext,
        )

        try {
            var responseText = ""
            chatRepository.chat(chatBody) { message ->
                responseText = message.content.text ?: ""
            }
            val aiResponse = json.decodeFromString<AiPlanResponse>(stripMarkdownFences(responseText))
            val sanitizedExercises = aiResponse.exercises.map { it.sanitize() }
            val difficultyLevel = mapDifficulty(aiResponse.difficulty)

            savePlan(today, sanitizedExercises, aiResponse.encouragement, difficultyLevel, true, aiResponse.dailyTip)
        } catch (e: Exception) {
            val difficulty = if (completionRate >= 0 && completionRate < 0.5f) 1 else 2
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
            journeyId = _uiState.value.journeyContext?.journeyId ?: 0,
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

    private suspend fun loadJourneyContext() {
        // MMKV 自愈: 验证 ID 是否有效
        val mmkvId = LocalStorageData.activeJourneyId.value
        var journey: com.example.weight.data.exercise.Journey? = null

        if (mmkvId > 0) {
            journey = journeyDao.getJourneyById(mmkvId)
            if (journey == null || journey.status != "active") {
                LocalStorageData.activeJourneyId.value = 0
            }
        }

        if (journey == null) {
            journey = journeyDao.getActiveJourney()
            if (journey != null) {
                LocalStorageData.activeJourneyId.value = journey.id
            }
        }

        if (journey == null) {
            _uiState.value = _uiState.value.copy(journeyContext = null, journeyPhaseName = null)
            return
        }

        reloadJourneyContext(journey)
    }

    /**
     * 根据 Journey 对象构建完整的 JourneyContext 并更新 UI 状态。
     * 被 loadJourneyContext() 和 observeActiveJourney() 共同复用。
     */
    private suspend fun reloadJourneyContext(journey: com.example.weight.data.exercise.Journey) {
        val currentDay = TimeUtils.getDaysSince(journey.startDate)

        // 旅程到期自动完成
        if (currentDay > journey.targetDays) {
            journeyDao.updateJourneyStatus(journey.id, "completed", System.currentTimeMillis())
            LocalStorageData.activeJourneyId.value = 0
            _uiState.value = _uiState.value.copy(journeyContext = null, journeyPhaseName = null)
            return
        }

        val phase = journeyDao.getPhaseForDay(journey.id, currentDay)
        if (phase == null) {
            _uiState.value = _uiState.value.copy(journeyContext = null, journeyPhaseName = null)
            return
        }

        val startWeight = journey.startWeight
        val lastWeight = recordDao.getLastData()?.weight ?: startWeight
        val weightLost = startWeight - lastWeight

        val focusAreas = try {
            json.decodeFromString<List<String>>(phase.focusAreas)
        } catch (e: Exception) {
            emptyList()
        }

        val isFirstDayOfPhase = currentDay == phase.startDay

        val context = ExercisePromptBuilder.JourneyContext(
            journeyId = journey.id,
            currentDay = currentDay,
            totalDays = journey.targetDays,
            currentPhaseName = phase.name,
            currentPhaseDescription = phase.description,
            phaseFocusAreas = focusAreas,
            phaseDifficultyLevel = phase.difficultyLevel,
            phaseCalorieDeficit = phase.dailyCalorieDeficit,
            phaseExerciseDuration = phase.dailyExerciseDuration,
            startWeight = startWeight,
            targetWeight = journey.targetWeight,
            weightLostSoFar = weightLost,
            isFirstDayOfPhase = isFirstDayOfPhase,
        )

        _uiState.value = _uiState.value.copy(
            journeyContext = context,
            journeyPhaseName = "${phase.name} · 第${currentDay}天",
        )

        // 将已有的未关联今日计划关联到当前 Journey
        val today = TimeUtils.getCurrentDate()
        val existingPlan = exercisePlanDao.getPlanByDate(today)
        if (existingPlan != null && existingPlan.journeyId == 0) {
            exercisePlanDao.updatePlanJourneyId(existingPlan.id, journey.id)
        }
    }
}

private fun stripMarkdownFences(text: String): String = text
    .trim()
    .removePrefix("```json").removePrefix("```")
    .removeSuffix("```")
    .trim()
