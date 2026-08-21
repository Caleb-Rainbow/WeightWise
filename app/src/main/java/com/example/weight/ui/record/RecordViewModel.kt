package com.example.weight.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import com.example.weight.data.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class RecordViewModel(
    private val recordDao: RecordDao,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // 搜索防抖：每次按键直接重建 Pager 会触发全表 LIKE 扫描，等输入停顿 250ms 再查；
    // 清空搜索时无延迟立即恢复全量列表。cachedIn 让分页数据在旋转屏幕等场景下复用
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val recordPager: Flow<PagingData<Record>> = _query
        .debounce { if (it.isBlank()) 0L else 250L }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            Pager(config = PagingConfig(pageSize = 20)) {
                recordDao.pagingSource(query.trim())
            }.flow
        }
        .cachedIn(viewModelScope)

    /** 最新一条记录，用于列表顶部的汇总信息 */
    val latestRecord: Flow<Record?> = recordDao.getLastDataFlow()

    /** 累计记录条数 */
    val recordCount: Flow<Int> = recordDao.getRecordCount()

    fun onQueryChanged(query: String) {
        _query.value = query
    }

    fun deleteRecord(record: Record) {
        viewModelScope.launch(Dispatchers.IO) {
            recordDao.delete(record)
            // 非阻塞刷新：组件更新在后台完成即可，无需拖住本协程
            launch { widgetUpdater.notifyDataChanged() }
        }
    }

    fun updateRecord(record: Record) {
        viewModelScope.launch(Dispatchers.IO) {
            recordDao.update(record)
            launch { widgetUpdater.notifyDataChanged() }
        }
    }
}
