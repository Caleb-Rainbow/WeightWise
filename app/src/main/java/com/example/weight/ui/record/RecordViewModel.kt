package com.example.weight.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class RecordViewModel(private val recordDao: RecordDao) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // 搜索条件变化时切换 PagingSource；cachedIn 让分页数据在旋转屏幕等场景下复用
    @OptIn(ExperimentalCoroutinesApi::class)
    val recordPager: Flow<PagingData<Record>> = _query
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
        }
    }

    fun updateRecord(record: Record) {
        viewModelScope.launch(Dispatchers.IO) {
            recordDao.update(record)
        }
    }
}
