package com.example.weight.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.weight.data.record.Record
import com.example.weight.ui.common.DeleteDialog
import com.example.weight.ui.common.MyTopBar
import com.example.weight.util.TimeUtils
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat

@Composable
fun RecordScreen(modifier: Modifier = Modifier, viewModel: RecordViewModel = koinViewModel(), goBack:()-> Unit = {}){
    Scaffold(modifier = modifier, topBar = {
        MyTopBar(title = "记录", goBack = goBack)
    }) {paddingValues->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            val recordList = viewModel.recordPager.collectAsLazyPagingItems()
            LazyColumn(modifier = Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)){
                items(recordList.itemCount, key = recordList.itemKey{it.id}) {
                    recordList[it]?.let {
                        RecordItem(modifier = Modifier.animateItem(), record = it){
                            viewModel.deleteRecord(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordItem(modifier: Modifier,record: Record,onDelete:()-> Unit){
    val format = remember {
        DecimalFormat("#.#kg")
    }
    var isShowDeleteDialog by remember {
        mutableStateOf(false)
    }
    if (isShowDeleteDialog){
        DeleteDialog(onDismissRequest = {
            isShowDeleteDialog = false
        }, onConfirmClick = {
            isShowDeleteDialog = false
            onDelete()
        }, content = "请确认是否要删除『${TimeUtils.convertMillisToTime(record.timestamp)}的记录』")
    }
    Card(modifier = modifier, onClick = {
        isShowDeleteDialog = true
    }) {
        Row (modifier = Modifier.padding(8.dp)){
            Text(text = TimeUtils.convertMillisToTime(record.timestamp))
            Spacer(Modifier.weight(1f))
            Text(text = format.format(record.weight))
        }
    }
}