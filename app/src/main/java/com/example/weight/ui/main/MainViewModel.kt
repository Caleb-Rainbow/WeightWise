package com.example.weight.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.LocalStorageData
import com.example.weight.data.chat.ChatBodyModel
import com.example.weight.data.chat.ChatMessageRole
import com.example.weight.data.chat.ChatRepository
import com.example.weight.data.chat.MessageModel
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import com.example.weight.util.TimeUtils
import com.example.weight.util.TimeUtils.getStartTimeForLastDays
import com.example.weight.util.TimeUtils.getStartTimeForLastMonths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class UiState(
    val selectedRecord: DailyMinWeight? = null,
    val firstRecord: Record? = null,
    val analyzeResponse: String = "",
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
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentScopeData: Flow<List<DailyMinWeight>> =
        selectedScope.flatMapLatest { scope ->
            val startTime = when (scope) {
                StatisticsScope.LAST_7DAYS -> getStartTimeForLastDays(7)
                StatisticsScope.LAST_14DAYS -> getStartTimeForLastDays(14)
                StatisticsScope.LAST_1MONTH -> getStartTimeForLastMonths(1)
                StatisticsScope.LAST_3MONTH -> getStartTimeForLastMonths(3)
                StatisticsScope.LAST_6MONTH -> getStartTimeForLastMonths(6)
                StatisticsScope.LAST_1YEARS -> getStartTimeForLastMonths(12)
                StatisticsScope.LAST_2YEARS -> getStartTimeForLastMonths(24)
                StatisticsScope.LAST_3YEARS -> getStartTimeForLastMonths(36)
            }
            recordDao.getDailyMinWeightSince(startTime)
        }

    init {
        observeFirstRecord()
    }

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
            it.copy(analyzeResponse = "")
        }
    }

    fun aiAnalyze(bmi: Double, onFail: (String) -> Unit) {
        //todo 1.获取当前用户选择的时间范围
        val scope = selectedScope.value
        showLoading()
        viewModelScope.launch(Dispatchers.IO) {
            //todo 2.获取范围数据
            val startTime = when (scope) {
                StatisticsScope.LAST_7DAYS -> getStartTimeForLastDays(7)
                StatisticsScope.LAST_14DAYS -> getStartTimeForLastDays(14)
                else -> getStartTimeForLastMonths(1)
            }
            val data = recordDao.getRecordWeightSince(startTime)
            if (data.size < 2) {
                hideLoading()
                onFail("数据量不足，至少需要两条记录才能进行分析哦。")
                return@launch
            }
            showAiAnalyzeBottomSheet()
            // 3. 构建 Prompt
            val prompt = buildAnalysisPrompt(data, scope, bmi)
            chatRepository.streamChat(
                model = ChatBodyModel(
                    messages = listOf(
                        MessageModel(
                            role = ChatMessageRole.USER.label,
                            content = prompt
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
        }
    }

    /**
     * 构建用于AI分析的高质量Prompt
     * @param records 从数据库获取的记录列表
     * @param scope 用户选择的时间范围
     * @return 格式化后的完整Prompt字符串
     */
    private fun buildAnalysisPrompt(records: List<Record>, scope: StatisticsScope, bmi: Double): String {
        val height = LocalStorageData.height.value
        val targetWeight = LocalStorageData.targetWeight.value

        // 如果数据为空，提前拦截或在 Prompt 中特殊处理（建议在函数外拦截）
        val recordsString = records.joinToString("\n") { record ->
            val date = Instant.ofEpochMilli(record.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            val logText = if (record.log.isNotBlank()) " [日志: ${record.log}]" else ""
            "- $date: ${String.format(Locale.CHINA, "%.1f", record.weight)}kg$logText"
        }

        return """
你是一位专业、温暖且富有同理心的体重管理顾问。请根据用户的体重记录和日志，为TA提供一份简单易懂、具有鼓励性的分析反馈。

【用户档案】
- 身高：${height}cm
- 目标体重：${targetWeight}kg
- 当前BMI：$bmi
- 数据时间范围：${scope.label}

【打卡数据】
$recordsString

【回复要求】
请以亲切的朋友口吻直接与用户对话（称呼“你”），字数控制在300字左右，并严格按以下三个段落结构输出：

1. 阶段总结：用一两句话概括用户在这段时间（${scope.label}）的体重变化趋势（如：稳步下降、遇到平台期、轻微波动等），并给予情绪上的肯定或安抚。
2. 数据洞察：结合体重数值的变化和用户的[日志]内容，分析可能的原因。如果日志提到了饮食/运动/情绪，请指出它们与体重变化的关联；如果没有日志，请基于纯数值趋势进行合理推断。
3. 行动建议：基于现状，给出1到2个具体、微小且容易执行的日常建议，帮助用户向 ${targetWeight}kg 的目标迈进。

【严格限制条件】
- 必须使用纯文本，绝对不要输出任何代码块、Markdown复杂表格或特殊图标。
- 语言必须通俗易懂，绝对不要使用生僻的医学术语。
- 不要机械地罗列或复述用户的数据，你的重点是“解读数据背后的意义”。
""".trimIndent()
    }
}