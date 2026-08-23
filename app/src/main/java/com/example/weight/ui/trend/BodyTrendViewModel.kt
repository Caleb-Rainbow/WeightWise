package com.example.weight.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.record.MetricPoint
import com.example.weight.data.record.RecordCompositionRaw
import com.example.weight.data.record.RecordDao
import com.example.weight.data.record.TrendMetric
import com.example.weight.data.record.dailyLastCompositions
import com.example.weight.ui.main.StatisticsScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

/**
 * 成分趋势页状态：范围 + 指标两个选择维度驱动一条派生链，
 * Room 表级失效时自动重查（新增称重记录后趋势即时刷新）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class BodyTrendViewModel(
    private val recordDao: RecordDao,
) : ViewModel() {
    // 成分数据积累慢，默认近 1 月；7 天窗口常只有零星几个点，趋势感不足
    private val _selectedScope = MutableStateFlow(StatisticsScope.LAST_1MONTH)
    val selectedScope: StateFlow<StatisticsScope> = _selectedScope.asStateFlow()

    private val _selectedMetric = MutableStateFlow(TrendMetric.FAT_RATIO)
    val selectedMetric: StateFlow<TrendMetric> = _selectedMetric.asStateFlow()

    /** 与 MainViewModel.currentScopeData 同模式：null 仅表示首次加载，切范围先回放旧值不闪空态 */
    val rawRecords: StateFlow<List<RecordCompositionRaw>?> = selectedScope
        .flatMapLatest { scope -> recordDao.getCompositionsSince(scope.startTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 每日最后一条成分快照（北京时间分区），聚合口径见 [dailyLastCompositions] */
    val dailyPoints: StateFlow<List<MetricPoint>> = rawRecords
        .map { dailyLastCompositions(it.orEmpty()) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前所选指标的序列：跳过该指标未测得的日期（老记录缺个别字段时不留空洞） */
    val metricSeries: StateFlow<List<MetricPoint>> = combine(
        dailyPoints, selectedMetric,
    ) { points, metric -> points.filter { metric.valueOf(it.composition) != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectScope(scope: StatisticsScope) {
        _selectedScope.value = scope
    }

    fun selectMetric(metric: TrendMetric) {
        _selectedMetric.value = metric
    }
}
