package com.example.weight.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.LocalStorageData
import com.example.weight.data.chat.AnalysisPromptBuilder
import com.example.weight.data.chat.ChatBodyModel
import com.example.weight.data.chat.ChatMessageRole
import com.example.weight.data.chat.ChatRepository
import com.example.weight.data.chat.MessageContent
import com.example.weight.data.chat.MessageModel
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import com.example.weight.util.MilestoneCalculator
import com.example.weight.util.RecordStreakCalculator
import com.example.weight.util.StreakInfo
import com.example.weight.util.TimeUtils
import com.example.weight.util.TimeUtils.getStartTimeForLastDays
import com.example.weight.util.WeightPredictor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.time.LocalDate

data class UiState(
    val selectedRecord: DailyMinWeight? = null,
    val firstRecord: Record? = null,
    val analyzeResponse: String = "",
    val analyzeError: String? = null,//AI分析失败原因，null 表示无错误
)

data class DialogState(
    val isShowAddDialog: Boolean = false,//添加体重弹窗
    val isShowSetHeightDialog: Boolean = false,//设置身高弹窗
    val isShowAiAnalyzeBottomSheet: Boolean = false,//AI分析弹窗
    val isLoading: Boolean = false,//加载中
)

@KoinViewModel
class MainViewModel(
    private val recordDao: RecordDao,
    private val chatRepository: ChatRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState = _dialogState.asStateFlow()

    // 暴露当前选中的统计范围作为 StateFlow
    private val _selectedScope = MutableStateFlow(StatisticsScope.LAST_7DAYS)
    val selectedScope: StateFlow<StatisticsScope> = _selectedScope.asStateFlow()

    // 根据选中的范围，动态获取对应的数据 Flow
    // flatMapLatest 会取消前一个 Flow 的收集，并开始收集新的 Flow
    // 先发 null 表示"加载中"，UI 用它区分首次加载（转圈）与真的没有数据（空状态）；
    // 范围切换时的短暂 null 由 UI 用上一次数据兜底，不会闪烁
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentScopeData: Flow<List<DailyMinWeight>?> =
        selectedScope.flatMapLatest { scope ->
            flow {
                emit(null)
                emitAll(recordDao.getDailyMinWeightSince(scope.startTimeMillis()))
            }
        }

    init {
        observeFirstRecord()
        observeMilestones()
    }

    /** 连续打卡信息：打卡日来自数据库 Flow，记录增删后自动重算 */
    val streakInfo: StateFlow<StreakInfo> = recordDao.getRecordDaysFlow()
        .map { RecordStreakCalculator.calculate(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakInfo(0, 0, false))

    /** 里程碑达成的一次性庆祝事件，UI 收到后弹 SnackBar */
    private val _milestoneCelebration = Channel<String>(Channel.BUFFERED)
    val milestoneCelebration = _milestoneCelebration.receiveAsFlow()

    /**
     * 监听起始/最新体重计算里程碑档数，仅在档数「净增」时庆祝：
     * 首次发射（冷启动、导入数据）不庆祝，删除再恢复同档也不重复庆祝以外的方向均不触发。
     */
    private fun observeMilestones() {
        viewModelScope.launch(Dispatchers.IO) {
            var lastCount = 0
            var initialized = false
            kotlinx.coroutines.flow.combine(
                recordDao.getFirstDataFlow(),
                recordDao.getLastDataFlow(),
            ) { first, last ->
                if (first == null || last == null) 0
                else MilestoneCalculator.calculateMilestoneCount(first.weight, last.weight)
            }.collect { count ->
                if (initialized && count > lastCount && count > 0) {
                    _milestoneCelebration.send(
                        "已累计减重 ${MilestoneCalculator.lossKgOfMilestone(count)}kg，" +
                            "第 $count 个里程碑达成，继续加油！"
                    )
                }
                lastCount = count
                initialized = true
            }
        }
    }

    /** 预测目标达成天数用的固定窗口数据：最近 90 天每日最低体重，不随图表统计范围切换，保证预测稳定 */
    val predictionData: Flow<List<DailyMinWeight>> =
        recordDao.getDailyMinWeightSince(getStartTimeForLastDays(WeightPredictor.ANALYSIS_WINDOW_DAYS.toInt()))

    // 更新选中的统计范围
    fun selectScope(scope: StatisticsScope) {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedScope.value = scope
        }
    }

    fun showAddDialog() {
        viewModelScope.launch(Dispatchers.IO) {
            _dialogState.update {
                it.copy(isShowAddDialog = true)
            }
        }
    }

    fun hideAddDialog() {
        viewModelScope.launch(Dispatchers.IO) {
            _dialogState.update {
                it.copy(isShowAddDialog = false)
            }
        }
    }

    fun showSetHeightDialog() {
        viewModelScope.launch(Dispatchers.IO) {
            _dialogState.update {
                it.copy(isShowSetHeightDialog = true)
            }
        }
    }

    fun hideSetHeightDialog() {
        viewModelScope.launch(Dispatchers.IO) {
            _dialogState.update {
                it.copy(isShowSetHeightDialog = false)
            }
        }
    }

    fun setSelectedRecord(record: DailyMinWeight?) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(selectedRecord = record)
            }
        }
    }

    fun getLastRecordWeight(onWeight: (Double?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val lastRecord = recordDao.getLastData()
            onWeight(lastRecord?.weight)
        }
    }

    fun insertRecord(date: String, time: String, log: String, weight: Double, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            recordDao.insert(
                Record(
                    timestamp = TimeUtils.convertTimeToMillis("$date $time:00"),
                    weight = weight,
                    log = log
                )
            )
            onSuccess()
        }
    }

    /**
     * 响应式观察最早记录变化。
     * Record 表数据变化（新增/删除）时自动重发，确保进度条的"起始体重"始终准确。
     */
    private fun observeFirstRecord() {
        viewModelScope.launch(Dispatchers.IO) {
            recordDao.getFirstDataFlow().collect { record ->
                _uiState.update { it.copy(firstRecord = record) }
            }
        }
    }

    fun getFirstRecord() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(firstRecord = recordDao.getFirstData())
            }
        }
    }

    fun showLoading() {
        _dialogState.update {
            it.copy(isLoading = true)
        }
    }

    fun hideLoading() {
        _dialogState.update {
            it.copy(isLoading = false)
        }
    }

    fun showAiAnalyzeBottomSheet() {
        _dialogState.update {
            it.copy(isShowAiAnalyzeBottomSheet = true)
        }
    }

    fun hideAiAnalyzeBottomSheet() {
        _dialogState.update {
            it.copy(isShowAiAnalyzeBottomSheet = false)
        }
        _uiState.update {
            it.copy(analyzeResponse = "", analyzeError = null)
        }
    }

    fun aiAnalyze(bmi: Double, onFail: (String) -> Unit) {
        //todo 1.获取当前用户选择的时间范围
        val scope = selectedScope.value
        showLoading()
        viewModelScope.launch(Dispatchers.IO) {
            //todo 2.按当前所选范围取数，与图表口径一致，避免“标签写着近3年、数据只有1月”的错位
            val data = recordDao.getRecordWeightSince(scope.startTimeMillis())
            if (data.size < 2) {
                hideLoading()
                onFail("数据量不足，至少需要两条记录才能进行分析哦。")
                return@launch
            }
            showAiAnalyzeBottomSheet()
            // 3. 构建 Prompt
            val prompt = AnalysisPromptBuilder.build(
                records = data,
                scopeLabel = scope.label,
                bmi = bmi,
                heightCm = LocalStorageData.height.value,
                targetWeight = LocalStorageData.targetWeight.value,
            )
            // 重试或再次分析前清空上一次的结果与错误
            _uiState.update { it.copy(analyzeResponse = "", analyzeError = null) }
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
                            if (dialogState.value.isLoading) {
                                hideLoading()
                            }
                            _uiState.update {
                                it.copy(analyzeResponse = it.analyzeResponse + content)
                            }
                        }
                    })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                hideLoading()
                _uiState.update {
                    it.copy(analyzeError = e.message ?: "分析失败，请稍后重试")
                }
            }
        }
    }
}