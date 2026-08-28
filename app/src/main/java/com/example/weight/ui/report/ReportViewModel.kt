package com.example.weight.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.LocalStorageData
import com.example.weight.data.RecommendedIntakeProvider
import com.example.weight.data.chat.AnalysisPromptBuilder
import com.example.weight.data.chat.ChatBodyModel
import com.example.weight.data.chat.ChatMessageRole
import com.example.weight.data.chat.ChatRepository
import com.example.weight.data.chat.MessageContent
import com.example.weight.data.chat.MessageModel
import com.example.weight.data.diet.DailyCalories
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.diet.FrequentFoodAggregator
import com.example.weight.data.diet.TrafficLightCount
import com.example.weight.data.health.HealthActivitySummary
import com.example.weight.data.health.HealthConnectManager
import com.example.weight.data.record.DailyWeight
import com.example.weight.data.record.RecordDao
import com.example.weight.data.record.BEIJING_OFFSET
import com.example.weight.data.record.dailyWeightsBetween
import com.example.weight.data.record.dailyWeightsSince
import com.example.weight.util.ActivityLevel
import com.example.weight.util.Gender
import com.example.weight.util.ReportAggregator
import com.example.weight.util.ReportCaloriesStats
import com.example.weight.util.ReportType
import com.example.weight.util.ReportWeightStats
import com.example.weight.util.TimeUtils
import com.example.weight.util.WeeklyControlEngine
import com.example.weight.util.WeightTrendAnalyzer
import com.example.weight.util.WeightTrendInsight
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/** 一个周期内聚合完成的报告数据；dailyWeights 为空表示该周期无打卡记录 */
data class ReportData(
    val type: ReportType,
    val anchor: LocalDate,
    val title: String,
    val totalDays: Int,
    val dailyWeights: List<DailyWeight>,
    val weightStats: ReportWeightStats?,
    val caloriesStats: ReportCaloriesStats?,
    val healthSummary: HealthActivitySummary?,
    val changeVsPrevPeriod: Double?,
    val endBmi: Double?,
    val trendInsight: WeightTrendInsight,
)

/** AI 周期总结的展示状态（加载/流式中/完成/失败） */
data class ReportAiState(
    val isShowSheet: Boolean = false,
    val isLoading: Boolean = false,
    /** 首帧已到、流式尚未完结；UI 在此期间用纯文本渲染，结束后再整篇 Markdown */
    val isStreaming: Boolean = false,
    val response: String = "",
    val error: String? = null, //null 表示无错误
)

@KoinViewModel
class ReportViewModel(
    private val recordDao: RecordDao,
    private val dietRecordDao: DietRecordDao,
    private val chatRepository: ChatRepository,
    private val healthConnectManager: HealthConnectManager,
    recommendedIntakeProvider: RecommendedIntakeProvider,
    private val json: Json,
) : ViewModel() {

    private val _selectedType = MutableStateFlow(ReportType.WEEK)
    val selectedType = _selectedType.asStateFlow()

    private val _anchor = MutableStateFlow(ReportType.WEEK.anchorOf(LocalDate.now()))
    val anchor = _anchor.asStateFlow()

    /** 每日建议摄入：与饮食页同口径（档案 + 最新体重 → TDEE 目标缺口），档案不全或无体重时为 null */
    val recommendedIntake: StateFlow<Int?> = recommendedIntakeProvider.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * 每周决策结果（评审 3A：进页实时计算、不缓存；体重口径经 dailyWeightsSince
     * 与趋势页同源同判）。null 表示首次计算未完成——UI 用固定高度占位防布局跳动。
     * 显示条件（WEEK 型且锚点=当前周）由 [showWeeklyDecision] 单独给出。
     */
    val weeklyDecision: StateFlow<WeeklyControlEngine.WeeklyControlResult?> = run {
        val profileFlow = combine(
            LocalStorageData.height,
            LocalStorageData.age,
            LocalStorageData.gender,
            LocalStorageData.activityLevel,
            combine(LocalStorageData.targetWeight, LocalStorageData.weeklyTargetChangeKg) { t, w -> t to w },
        ) { height, age, gender, activityLevel, goal ->
            WeeklyControlEngine.ControlProfile(
                gender = Gender.entries.find { it.name == gender },
                age = age,
                heightCm = height,
                activityLevel = ActivityLevel.entries.find { it.name == activityLevel },
            ) to goal
        }
        val today = LocalDate.now()
        val windowStart = today.minusDays(WeeklyControlEngine.WINDOW_DAYS - 1L)
        val startMillis = windowStart.atStartOfDay(BEIJING_OFFSET).toInstant().toEpochMilli()
        val indulgentSince = today.minusDays(FrequentFoodAggregator.WINDOW_DAYS - 1L).toString()
        // getDailyCaloriesBetween 上界为排他语义：传次日以免丢当天；getFoodJsonBetween
        // 是双端含且 today 上界专门挡未来日期补记，保持传 today 不变
        combine(
            profileFlow,
            recordDao.dailyWeightsSince(startMillis),
            dietRecordDao.getDailyCaloriesBetween(windowStart.toString(), today.plusDays(1).toString()),
            dietRecordDao.getFoodJsonBetween(indulgentSince, today.toString()),
            recommendedIntakeProvider.flow,
        ) { (profile, goal), weights, calories, foodJson, staticIntake ->
            WeeklyControlEngine.evaluate(
                WeeklyControlEngine.WeeklyControlInput(
                    today = LocalDate.now(),
                    dailyWeights = weights,
                    dailyCalories = calories,
                    targetWeightKg = goal.first,
                    weeklyTargetChangeKg = goal.second,
                    profile = profile,
                    staticRecommendedIntake = staticIntake,
                    indulgentMeals = WeeklyControlEngine.aggregateIndulgentMeals(foodJson, json),
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    /** 评审 2A：仅 WEEK 型且锚点=当前周时显示决策卡；翻历史周/切月年报时隐藏 */
    val showWeeklyDecision: StateFlow<Boolean> = combine(selectedType, anchor) { type, a ->
        type == ReportType.WEEK && a == ReportType.WEEK.anchorOf(LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * 当前周期的聚合报告。null 表示「周期/类型切换的加载瞬间」，UI 用上一次数据兜底；
     * 非空但 dailyWeights 为空表示该周期确实没有打卡记录（空态）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val reportFlow: Flow<ReportData?> = combine(selectedType, anchor) { type, a -> type to a }
        .flatMapLatest { (type, anchor) ->
            flow {
                emit(null)
                val today = LocalDate.now()
                val (start, end) = type.periodRange(anchor)
                val reportEnd = minOf(end, System.currentTimeMillis())
                val totalDays = type.daysOf(anchor, today)
                // 上一周期首末日，供「较上期」对比；一次性取值，翻页时随之刷新
                val prevRange = type.periodRange(type.shift(anchor, -1))
                coroutineScope {
                    // 与主数据流并行，不阻塞本期数据先到先渲染
                    val prevWeights = async {
                        recordDao.dailyWeightsBetween(prevRange.first, prevRange.second).first()
                    }
                    val healthSummary = async {
                        healthConnectManager.readActivitySummary(
                            start = java.time.Instant.ofEpochMilli(start),
                            end = java.time.Instant.ofEpochMilli(reportEnd),
                            rangeDays = totalDays,
                        )
                    }
                    val startDate = TimeUtils.convertMillisToDate(start)
                    val endDate = TimeUtils.convertMillisToDate(end)
                    val goalProfile = combine(
                        LocalStorageData.height,
                        LocalStorageData.targetWeight,
                    ) { height, targetWeight -> height to targetWeight }
                    combine(
                        recordDao.dailyWeightsBetween(start, end),
                        dietRecordDao.getDailyCaloriesBetween(startDate, endDate),
                        dietRecordDao.getTrafficLightBetween(startDate, endDate),
                        recommendedIntake,
                        goalProfile,
                    ) { weights, calories, lights, intake, profile ->
                        ReportData(
                            type = type,
                            anchor = anchor,
                            title = type.titleOf(anchor),
                            totalDays = totalDays,
                            dailyWeights = weights,
                            weightStats = ReportAggregator.weightStats(weights),
                            caloriesStats = ReportAggregator.caloriesStats(calories, lights, intake),
                            healthSummary = healthSummary.await(),
                            changeVsPrevPeriod = ReportAggregator.changeVsPrevPeriod(weights, prevWeights.await()),
                            endBmi = weights.lastOrNull()?.let { ReportAggregator.bmi(it.value, profile.first) },
                            trendInsight = WeightTrendAnalyzer.analyze(weights, totalDays, profile.second),
                        )
                    }.collect { emit(it) }
                }
            }
        }

    private val _aiState = MutableStateFlow(ReportAiState())
    val aiState = _aiState.asStateFlow()

    /** 切换周期类型时回到该类型的当前周期 */
    fun selectType(type: ReportType) {
        if (type == _selectedType.value) return
        _selectedType.value = type
        _anchor.value = type.anchorOf(LocalDate.now())
    }

    fun previousPeriod() {
        _anchor.update { _selectedType.value.shift(it, -1) }
    }

    fun nextPeriod() {
        val type = _selectedType.value
        _anchor.update {
            if (type.canGoNext(it, LocalDate.now())) type.shift(it, 1) else it
        }
    }

    fun aiSummarize(onFail: (String) -> Unit) {
        val type = _selectedType.value
        val anchor = _anchor.value
        _aiState.update { it.copy(isLoading = true, isStreaming = false, response = "", error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val (start, end) = type.periodRange(anchor)
            val reportEnd = minOf(end, System.currentTimeMillis())
            val records = recordDao.getRecordWeightBetween(start, end)
            if (records.size < 2) {
                _aiState.update { it.copy(isLoading = false) }
                onFail("该周期记录不足，至少需要两条记录才能进行总结哦。")
                return@launch
            }
            _aiState.update { it.copy(isShowSheet = true) }
            // 饮食热量与每日体重互不依赖，并行取数
            val (dailyCalories, weights, healthSummary) = coroutineScope {
                val caloriesDeferred = async {
                    dietRecordDao.getDailyCaloriesBetween(
                        TimeUtils.convertMillisToDate(start), TimeUtils.convertMillisToDate(end),
                    ).first()
                }
                val weightsDeferred = async { recordDao.dailyWeightsBetween(start, end).first() }
                val healthDeferred = async {
                    healthConnectManager.readActivitySummary(
                        start = java.time.Instant.ofEpochMilli(start),
                        end = java.time.Instant.ofEpochMilli(reportEnd),
                        rangeDays = type.daysOf(anchor, LocalDate.now()),
                    )
                }
                Triple(caloriesDeferred.await(), weightsDeferred.await(), healthDeferred.await())
            }
            val endBmi = weights.lastOrNull()?.let { ReportAggregator.bmi(it.value, LocalStorageData.height.value) }
            val gender = Gender.entries.find { it.name == LocalStorageData.gender.value }
            val activityLevel = ActivityLevel.entries.find { it.name == LocalStorageData.activityLevel.value }
            val trendInsight = WeightTrendAnalyzer.analyze(
                dailyWeights = weights,
                totalDays = type.daysOf(anchor, LocalDate.now()),
                targetWeight = LocalStorageData.targetWeight.value,
            )
            val prompt = AnalysisPromptBuilder.build(
                records = records,
                scopeLabel = type.titleOf(anchor),
                bmi = endBmi ?: 0.0,
                heightCm = LocalStorageData.height.value,
                targetWeight = LocalStorageData.targetWeight.value,
                dailyCalories = dailyCalories,
                age = LocalStorageData.age.value,
                genderLabel = gender?.displayName ?: "",
                activityLabel = activityLevel?.displayName ?: "",
                healthSummary = healthSummary,
                trendInsight = trendInsight,
            )
            // 流式回包先累积到 StringBuilder，由合帧协程按固定间隔刷新到状态，
            // 避免每个 chunk 一次 StateFlow 更新 + 一次全文重组/Markdown 重解析
            val streamed = StringBuilder()
            val emitTicker = launch {
                var emittedLength = 0
                while (true) {
                    delay(STREAM_EMIT_INTERVAL_MS)
                    val snapshotLength = synchronized(streamed) { streamed.length }
                    if (snapshotLength > emittedLength) {
                        emittedLength = snapshotLength
                        val text = synchronized(streamed) { streamed.toString() }
                        _aiState.update { it.copy(isLoading = false, isStreaming = true, response = text) }
                    }
                }
            }
            try {
                chatRepository.streamChat(
                    model = ChatBodyModel(
                        messages = listOf(
                            MessageModel(
                                role = ChatMessageRole.USER.label,
                                content = MessageContent.TextOnly(prompt)
                            )
                        )
                    ), onMessage = { msg ->
                        msg?.choices?.singleOrNull()?.delta?.content?.let { content ->
                            synchronized(streamed) { streamed.append(content) }
                        }
                    })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _aiState.update {
                    it.copy(isLoading = false, isStreaming = false, error = e.message ?: "总结失败，请稍后重试")
                }
            } finally {
                emitTicker.cancel()
                // 收尾：一次性发出最终全文（含最后一帧之后未到合帧间隔的尾巴）
                val fullText = synchronized(streamed) { streamed.toString() }
                _aiState.update { state ->
                    if (state.error == null) {
                        state.copy(isLoading = false, isStreaming = false, response = fullText)
                    } else state
                }
            }
        }
    }

    fun hideAiSheet() {
        _aiState.update { it.copy(isShowSheet = false, isStreaming = false, response = "", error = null) }
    }

    private companion object {
        /** 流式合帧间隔：每 100ms 把累积文本刷一次到 UI，重组次数比逐 chunk 低一个数量级 */
        const val STREAM_EMIT_INTERVAL_MS = 100L
    }
}
