package com.example.weight.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class RecordViewModel(private val recordDao: RecordDao): ViewModel() {
    val recordPager = Pager(config = PagingConfig(pageSize = 20)){
        recordDao.pagingSource("")
    }.flow

    fun deleteRecord(record: Record){
        viewModelScope.launch(Dispatchers.IO) {
            recordDao.delete(record)
        }
    }
}