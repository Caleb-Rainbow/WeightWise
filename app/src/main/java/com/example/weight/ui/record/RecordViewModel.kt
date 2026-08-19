package com.example.weight.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class RecordViewModel(private val recordDao: RecordDao): ViewModel() {
    val recordPager = Pager(config = PagingConfig(pageSize = 20)){
        recordDao.pagingSource("")
    }.flow

    /** 最新一条记录，用于列表顶部的汇总信息 */
    val latestRecord: Flow<Record?> = recordDao.getLastDataFlow()

    /** 累计记录条数 */
    val recordCount: Flow<Int> = recordDao.getRecordCount()

    fun deleteRecord(record: Record){
        viewModelScope.launch(Dispatchers.IO) {
            recordDao.delete(record)
        }
    }

    fun updateRecord(record: Record){
        viewModelScope.launch(Dispatchers.IO) {
            recordDao.update(record)
        }
    }
}
