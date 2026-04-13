package com.example.weight.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.LocalStorageData
import com.example.weight.data.chat.ChatRepository
import com.example.weight.data.exercise.AiPhaseDefinition
import com.example.weight.data.exercise.AiJourneyResponse
import com.example.weight.data.exercise.FallbackJourneyGenerator
import com.example.weight.data.exercise.Journey
import com.example.weight.data.exercise.JourneyDao
import com.example.weight.data.exercise.JourneyPromptBuilder
import com.example.weight.data.exercise.Phase
import com.example.weight.data.record.RecordDao
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.annotation.KoinViewModel

data class JourneyCreationState(
    val startWeight: String = "",
    val targetWeight: String = "",
    val targetDays: String = "90",
    val isCreating: Boolean = false,
    val error: String? = null,
    val createdJourneyId: Int? = null,
)

@KoinViewModel
class JourneyCreationViewModel(
    private val journeyDao: JourneyDao,
    private val recordDao: RecordDao,
    private val chatRepository: ChatRepository,
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow(JourneyCreationState())
    val state: StateFlow<JourneyCreationState> = _state.asStateFlow()

    init {
        loadDefaults()
    }

    private fun loadDefaults() {
        val targetWeight = LocalStorageData.targetWeight.value
        _state.value = _state.value.copy(
            targetWeight = if (targetWeight > 0) targetWeight.toString() else "",
        )
        viewModelScope.launch(Dispatchers.IO) {
            val lastWeight = runCatching { recordDao.getLastData()?.weight }.getOrNull()
            _state.value = _state.value.copy(
                startWeight = (lastWeight ?: 0.0).toString(),
            )
        }
    }

    fun updateStartWeight(value: String) {
        _state.value = _state.value.copy(startWeight = value, error = null)
    }

    fun updateTargetWeight(value: String) {
        _state.value = _state.value.copy(targetWeight = value, error = null)
    }

    fun updateTargetDays(value: String) {
        _state.value = _state.value.copy(targetDays = value, error = null)
    }

    fun createJourney() {
        val s = _state.value
        val startWeight = s.startWeight.toDoubleOrNull()
        val targetWeight = s.targetWeight.toDoubleOrNull()
        val targetDays = s.targetDays.toIntOrNull()

        when {
            startWeight == null || startWeight <= 0 -> {
                _state.value = _state.value.copy(error = "请输入有效的起始体重")
                return
            }
            targetWeight == null || targetWeight <= 0 -> {
                _state.value = _state.value.copy(error = "请输入有效的目标体重")
                return
            }
            targetWeight >= startWeight -> {
                _state.value = _state.value.copy(error = "目标体重应低于起始体重")
                return
            }
            targetDays == null || targetDays < 14 -> {
                _state.value = _state.value.copy(error = "目标天数至少14天")
                return
            }
            targetDays > 365 -> {
                _state.value = _state.value.copy(error = "目标天数不能超过365天")
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isCreating = true, error = null)

            try {
                val active = journeyDao.getActiveJourney()
                if (active != null) {
                    _state.value = _state.value.copy(isCreating = false, error = "已有进行中的旅程，请先完成或放弃当前旅程")
                    return@launch
                }

                val height = LocalStorageData.height.value
                val recentWeights = recordDao.getDailyMinWeightSince(
                    TimeUtils.getStartTimeForLastDays(7)
                ).first()

                val bmi = if (height > 0) startWeight / ((height / 100) * (height / 100)) else 0.0

                val blacklist = LocalStorageData.exerciseBlacklist.value
                val whitelist = LocalStorageData.exerciseWhitelist.value

                val result = tryGenerateAiPhases(
                    height = height,
                    currentWeight = startWeight,
                    bmi = bmi,
                    targetWeight = targetWeight,
                    targetDays = targetDays,
                    recentWeights = recentWeights,
                    preferences = whitelist,
                    limitations = blacklist,
                )

                val phasesJson = FallbackJourneyGenerator.generatePhasesJson(result.phases)
                val startDate = TimeUtils.getCurrentDate()

                val journey = Journey(
                    startWeight = startWeight,
                    targetWeight = targetWeight,
                    targetDays = targetDays,
                    startDate = startDate,
                    phasesJson = phasesJson,
                    status = "active",
                    createdAt = System.currentTimeMillis(),
                    aiAdvice = result.advice,
                )

                val roomPhases = result.phases.mapIndexed { index, p ->
                    Phase(
                        journeyId = 0,
                        phaseIndex = index,
                        name = p.name,
                        description = p.description,
                        startDay = p.startDay,
                        endDay = p.endDay,
                        targetWeightLoss = p.targetWeightLoss,
                        focusAreas = json.encodeToString(
                            kotlinx.serialization.serializer<List<String>>(),
                            p.focusAreas,
                        ),
                        difficultyLevel = p.difficultyLevel,
                        dailyCalorieDeficit = p.dailyCalorieDeficit,
                        dailyExerciseDuration = p.dailyExerciseDuration,
                    )
                }
                val journeyId = journeyDao.insertJourneyWithPhases(journey, roomPhases)
                LocalStorageData.activeJourneyId.value = journeyId

                _state.value = _state.value.copy(isCreating = false, createdJourneyId = journeyId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isCreating = false, error = e.message ?: "创建旅程失败")
            }
        }
    }

    private data class AiGenerationResult(
        val phases: List<AiPhaseDefinition>,
        val advice: String,
    )

    private suspend fun tryGenerateAiPhases(
        height: Double,
        currentWeight: Double,
        bmi: Double,
        targetWeight: Double,
        targetDays: Int,
        recentWeights: List<com.example.weight.data.record.DailyMinWeight>,
        preferences: Set<String>,
        limitations: Set<String>,
    ): AiGenerationResult {
        return try {
            val chatBody = JourneyPromptBuilder.buildPrompt(
                height = height,
                currentWeight = currentWeight,
                bmi = bmi,
                targetWeight = targetWeight,
                targetDays = targetDays,
                recentWeights = recentWeights,
                exercisePreferences = preferences,
                exerciseLimitations = limitations,
            )

            var responseText = ""
            chatRepository.chat(chatBody) { message ->
                responseText = message.content.text ?: ""
            }

            val aiResponse = json.decodeFromString<AiJourneyResponse>(stripMarkdownFences(responseText))
            AiGenerationResult(phases = aiResponse.phases, advice = aiResponse.overallAdvice)
        } catch (e: Exception) {
            val phases = FallbackJourneyGenerator.generate(targetDays, currentWeight, targetWeight)
            AiGenerationResult(phases = phases, advice = "")
        }
    }
}

private fun stripMarkdownFences(text: String): String = text
    .trim()
    .removePrefix("```json").removePrefix("```")
    .removeSuffix("```")
    .trim()
