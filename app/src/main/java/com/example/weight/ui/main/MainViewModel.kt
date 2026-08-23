package com.example.weight.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import com.example.weight.data.widget.WidgetUpdater
import com.example.weight.util.GoalProgressCalculator
import com.example.weight.util.MilestoneCalculator
import com.example.weight.util.RecordStreakCalculator
import com.example.weight.util.StreakInfo
import com.example.weight.util.TimeUtils
import com.example.weight.util.TimeUtils.getStartTimeForLastDays
import com.example.weight.util.WeightPredictor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
)

data class DialogState(
    val isShowAddDialog: Boolean = false,//添加体重弹窗
    val isShowSetHeightDialog: Boolean = false,//设置身高弹窗
)

@KoinViewModel
class MainViewModel(
    private val recordDao: RecordDao,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState = _dialogState.asStateFlow()

    // 暴露当前选中的统计范围作为 StateFlow
    private val _selectedScope = MutableStateFlow(StatisticsScope.LAST_7DAYS)
    val selectedScope: StateFlow<StatisticsScope> = _selectedScope.asStateFlow()

    // 根据选中的范围，动态获取对应的数据 StateFlow
    // flatMapLatest 会取消前一个 Flow 的收集，并开始收集新的 Flow。
    // 缓存收进 ViewModel：stateIn 让导航返回（短时重订阅）直接回放旧值，不闪加载态、不重查；
    // 切范围时 StateFlow 保留旧值直到新范围首份数据到达（原先由 UI 层 cachedData 兜底，语义相同）；
    // null 仅出现在真正的首次加载，UI 用它区分加载中（转圈）与没有数据（空状态）
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentScopeData: StateFlow<List<DailyMinWeight>?> = selectedScope
        .flatMapLatest { scope -> recordDao.getDailyMinWeightSince(scope.startTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        observeFirstRecordAndMilestones()
    }

    /** 连续打卡信息：打卡日来自数据库 Flow，记录增删后自动重算。
     *  distinctUntilChanged 过滤同日改体重等「天数列表内容未变」的表级失效重发，
     *  省掉全表 DISTINCT 扫描与 O(n log n) 重算 */
    val streakInfo: StateFlow<StreakInfo> = recordDao.getRecordDaysFlow()
        .distinctUntilChanged()
        .map { RecordStreakCalculator.calculate(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakInfo(0, 0, false))

    /** 里程碑达成的一次性庆祝事件，UI 收到后弹 SnackBar */
    private val _milestoneCelebration = Channel<String>(Channel.BUFFERED)
    val milestoneCelebration = _milestoneCelebration.receiveAsFlow()

    /**
     * 一条 combine 同时驱动「起始体重」展示与里程碑庆祝：
     * 相比原先两条独立 Flow 各自订阅 getFirstDataFlow，Record 表写入时的重查次数少一半。
     * 起始体重与首页进度卡同口径：手动设置优先，未设置时取第一条记录。
     */
    private fun observeFirstRecordAndMilestones() {
        viewModelScope.launch(Dispatchers.IO) {
            var lastCount = 0
            var initialized = false
            kotlinx.coroutines.flow.combine(
                recordDao.getFirstDataFlow(),
                recordDao.getLastDataFlow(),
                LocalStorageData.startWeight,
            ) { first, last, configuredStartWeight ->
                _uiState.update { it.copy(firstRecord = first) }
                val startWeight = GoalProgressCalculator.effectiveStartWeight(
                    configuredStartWeight, first?.weight
                )
                if (startWeight == null || last == null) 0
                else MilestoneCalculator.calculateMilestoneCount(startWeight, last.weight)
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

    /** 预测目标达成天数用的固定窗口数据：最近 90 天每日最低体重，不随图表统计范围切换，保证预测稳定；同样 stateIn 缓存，导航返回不重查 */
    val predictionData: StateFlow<List<DailyMinWeight>> = recordDao
        .getDailyMinWeightSince(getStartTimeForLastDays(WeightPredictor.ANALYSIS_WINDOW_DAYS.toInt()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // StateFlow 更新线程安全且为微秒级操作，直接在调用线程执行即可，无需切 IO 调度
    fun selectScope(scope: StatisticsScope) {
        _selectedScope.value = scope
    }

    fun showAddDialog() {
        _dialogState.update { it.copy(isShowAddDialog = true) }
    }

    fun hideAddDialog() {
        _dialogState.update { it.copy(isShowAddDialog = false) }
    }

    fun showSetHeightDialog() {
        _dialogState.update { it.copy(isShowSetHeightDialog = true) }
    }

    fun hideSetHeightDialog() {
        _dialogState.update { it.copy(isShowSetHeightDialog = false) }
    }

    fun setSelectedRecord(record: DailyMinWeight?) {
        _uiState.update { it.copy(selectedRecord = record) }
    }

    fun getLastRecordWeight(onWeight: (Double?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val lastRecord = recordDao.getLastData()
            onWeight(lastRecord?.weight)
        }
    }

    fun insertRecord(
        date: String,
        time: String,
        log: String,
        weight: Double,
        bodyComposition: String = "",
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            recordDao.insert(
                Record(
                    timestamp = TimeUtils.convertTimeToMillis("$date $time:00"),
                    weight = weight,
                    log = log,
                    bodyComposition = bodyComposition,
                )
            )
            // 小组件刷新走独立协程：updateAll 含跨进程 binder + RemoteViews 组合，
            // 不能阻塞弹窗关闭回调；launch 在 insert 之后保证组件读到新数据
            launch { widgetUpdater.notifyDataChanged() }
            onSuccess()
        }
    }
}
