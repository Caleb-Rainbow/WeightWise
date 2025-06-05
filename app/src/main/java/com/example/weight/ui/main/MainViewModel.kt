package com.example.weight.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class UiState(
    val selectedRecord: DailyMinWeight? = null
)

data class DialogState(
    val isShowAddDialog: Boolean = false,//添加体重弹窗
    val isShowSetHeightDialog: Boolean = false,//设置身高弹窗
)

@KoinViewModel
class MainViewModel(
    private val recordDao: RecordDao
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

    fun insertRecord(date: String, time: String, weight: Double, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            recordDao.insert(
                Record(
                    timestamp = TimeUtils.convertTimeToMillis("$date $time:00"),
                    weight = weight,
                )
            )
            onSuccess()
        }
    }
}