package com.example.weight.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.LocalStorageData
import com.example.weight.data.exercise.DailyPlan
import com.example.weight.data.exercise.ExercisePlanDao
import com.example.weight.data.exercise.Journey
import com.example.weight.data.exercise.JourneyDao
import com.example.weight.data.exercise.Phase
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.data.record.RecordDao
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlinx.serialization.json.Json

data class JourneyProgressState(
    val journey: Journey? = null,
    val phases: List<Phase> = emptyList(),
    val currentPhase: Phase? = null,
    val currentDay: Int = 0,
    val totalDays: Int = 0,
    val weightProgress: List<DailyMinWeight> = emptyList(),
    val dailyPlans: List<DailyPlan> = emptyList(),
    val completionRate: Float = 0f,
    val completedExercises: Int = 0,
    val totalExercises: Int = 0,
    val streakDays: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val earlyCompleted: Boolean = false,
    val isJourneyNotFound: Boolean = false,
)

@KoinViewModel
class JourneyProgressViewModel(
    private val journeyDao: JourneyDao,
    private val exercisePlanDao: ExercisePlanDao,
    private val recordDao: RecordDao,
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow(JourneyProgressState())
    val state: StateFlow<JourneyProgressState> = _state.asStateFlow()

    private var plansObserverJob: Job? = null
    private var statsObserverJob: Job? = null
    private var weightObserverJob: Job? = null

    init {
        loadActiveJourney()
    }

    fun loadActiveJourney() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)

            // MMKV 自愈: 验证 ID 是否有效
            val mmkvId = LocalStorageData.activeJourneyId.value
            var journey: Journey? = null

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
                _state.value = _state.value.copy(isLoading = false, isJourneyNotFound = true)
                return@launch
            }

            val phases = journeyDao.getPhasesForJourney(journey.id)
            val currentDay = TimeUtils.getDaysSince(journey.startDate)

            // 旅程已到期
            if (currentDay > journey.targetDays) {
                journeyDao.updateJourneyStatus(journey.id, "completed", System.currentTimeMillis())
                LocalStorageData.activeJourneyId.value = 0
                _state.value = _state.value.copy(isLoading = false, isJourneyNotFound = true)
                return@launch
            }

            val currentPhase = journeyDao.getPhaseForDay(journey.id, currentDay)

            // 提前达标检测
            val lastWeight = recordDao.getLastData()?.weight
            val earlyCompleted = lastWeight != null && lastWeight <= journey.targetWeight

            _state.value = _state.value.copy(
                journey = journey,
                phases = phases,
                currentPhase = currentPhase,
                currentDay = currentDay,
                totalDays = journey.targetDays,
                isLoading = false,
                earlyCompleted = earlyCompleted && journey.status == "active",
            )

            // 响应式: 监听每日计划变化
            observeDailyPlans(journey.id)
            // 响应式: 监听统计数据变化（打卡、新增计划等）
            observeStats(journey.id)
            // 响应式: 监听体重变化
            observeWeightProgress(journey.startDate)
        }
    }

    private fun observeWeightProgress(startDateStr: String) {
        weightObserverJob?.cancel()
        weightObserverJob = viewModelScope.launch(Dispatchers.IO) {
            val startDate = TimeUtils.convertDateToMillis(startDateStr)
            recordDao.getDailyMinWeightSince(startDate)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
                .collect { weights ->
                    _state.value = _state.value.copy(weightProgress = weights)
                }
        }
    }

    private suspend fun calculateStreak(journeyId: Int): Int {
        val plans = exercisePlanDao.getPlansForJourney(journeyId)
        if (plans.isEmpty()) return 0

        val today = TimeUtils.getCurrentDate()
        var streak = 0
        var checkDate = today

        // 按日期降序排列
        val plansByDate = plans.associateBy { it.planDate }

        while (true) {
            val plan = plansByDate[checkDate] ?: break
            val completions = exercisePlanDao.getCompletionsByPlanId(plan.id)
            val hasCompleted = completions.any { it.isCompleted }
            if (!hasCompleted) break
            streak++
            checkDate = subtractDay(checkDate)
        }
        return streak
    }

    private fun subtractDay(dateStr: String): String {
        val date = java.time.LocalDate.parse(dateStr)
        return date.minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    private fun observeDailyPlans(journeyId: Int) {
        plansObserverJob?.cancel()
        plansObserverJob = viewModelScope.launch(Dispatchers.IO) {
            exercisePlanDao.getPlansForJourneyFlow(journeyId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
                .collect { plans ->
                    _state.value = _state.value.copy(dailyPlans = plans)
                }
        }
    }

    private fun observeStats(journeyId: Int) {
        statsObserverJob?.cancel()
        statsObserverJob = viewModelScope.launch(Dispatchers.IO) {
            // 两个 Flow 分别引用 ExerciseCompletion 和 DailyPlan，
            // 任一表变更（打卡、新增计划、替换运动）都会触发重算。
            combine(
                journeyDao.getCompletedExerciseCountForJourneyFlow(journeyId),
                journeyDao.getTotalExerciseCountForJourneyFlow(journeyId),
            ) { completedCount, totalCount ->
                val rate = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
                val streak = calculateStreak(journeyId)
                _state.value = _state.value.copy(
                    completedExercises = completedCount,
                    totalExercises = totalCount,
                    completionRate = rate,
                    streakDays = streak,
                )
            }.collect {}
        }
    }

    fun dismissEarlyCompletion() {
        _state.value = _state.value.copy(earlyCompleted = false)
    }

    fun abandonJourney() {
        val journey = _state.value.journey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            journeyDao.updateJourneyStatus(journey.id, "abandoned", System.currentTimeMillis())
            LocalStorageData.activeJourneyId.value = 0
            _state.value = _state.value.copy(isJourneyNotFound = true)
        }
    }

    fun completeJourney() {
        val journey = _state.value.journey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            journeyDao.updateJourneyStatus(journey.id, "completed", System.currentTimeMillis())
            LocalStorageData.activeJourneyId.value = 0
            _state.value = _state.value.copy(isJourneyNotFound = true)
        }
    }
}
