package com.example.weight.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.LocalStorageData
import com.example.weight.ui.common.DatePickerDocked
import com.example.weight.ui.common.NumberSelector
import com.example.weight.ui.common.TimePickerOutlineTextFiled
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.flow.update
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainDialog(viewModel: MainViewModel = koinViewModel()) {
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    if (dialogState.isShowAddDialog) {
        AddRecordDialog(onDismissRequest = {
            viewModel.hideAddDialog()
        })
    }
    if (dialogState.isShowSetHeightDialog) {
        SetHeightDialog(onDismissRequest = {
            viewModel.hideSetHeightDialog()
        })
    }
    LaunchedEffect(Unit) {
        if (LocalStorageData.isFirst) {
            viewModel.showSetHeightDialog()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordDialog(onDismissRequest: () -> Unit, viewModel: MainViewModel = koinViewModel()) {
    //日期-默认显示今日
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = TimeUtils.getTodayUtcMillis()
        )
    val date = pickerState.selectedDateMillis?.let {
        TimeUtils.convertUtcMillisToDate(it)
    } ?: ""
    //时间-默认显示当前时间
    var time: String by remember {
        mutableStateOf(TimeUtils.getCurrentTimeFormat3())
    }
    //体重
    var weight by remember {
        mutableDoubleStateOf(50.0)
    }
    //获取上一次体重
    var initialWeight by remember {
        mutableStateOf<Double?>(null)
    }
    //日志
    var log by remember {
        mutableStateOf("")
    }
    LaunchedEffect(Unit) {
        viewModel.getLastRecordWeight {
            initialWeight = it
        }
    }
    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Text("记录体重")
    }, text = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                DatePickerDocked(
                    modifier = Modifier.weight(1f),
                    label = "日期",
                    isRequired = true,
                    datePickerState = pickerState,
                    selectedDate = date
                )
                Spacer(modifier = Modifier.width(10.dp))
                TimePickerOutlineTextFiled(
                    modifier = Modifier.weight(0.7f),
                    hint = "时间",
                    value = time,
                    onValueChanged = {
                        time = it
                    },
                    isRequired = true,
                    singleLine = true
                )
            }
            NumberSelector(integerList = remember { (50..120).toList() }, decimalList = remember {
                (0..9).toList()
            }, onWeightChange = {
                weight = it
            }, initialWeight = initialWeight, unit = "kg")
            OutlinedTextField(value = log, onValueChange = {
                log = it
            }, label = {
                Text("日志/记录")
            })
        }
    }, confirmButton = {
        val snackBarShow = LocalSnackBarShow.current
        Button(onClick = {
            viewModel.insertRecord(date = date, time = time, weight = weight, log = log) {
                snackBarShow.invoke("添加成功")
                onDismissRequest()
            }
        }) {
            Text("添加")
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text("取消")
        }
    })
}

@Composable
fun SetHeightDialog(onDismissRequest: () -> Unit) {
    val height by LocalStorageData.height.collectAsStateWithLifecycle()
    val weight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Text("输入信息")
    }, text = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("你的身高")
            NumberSelector(integerList = remember { (150..200).toList() }, decimalList = remember {
                (0..9).toList()
            }, onWeightChange = { change ->
                LocalStorageData.height.update {
                    change
                }
            }, initialWeight = height, unit = "cm")
            HorizontalDivider()
            Text("你的目标")
            NumberSelector(integerList = remember { (50..120).toList() }, decimalList = remember {
                (0..9).toList()
            }, onWeightChange = { change ->
                LocalStorageData.targetWeight.update {
                    change
                }
            }, initialWeight = weight, unit = "kg")
        }
    }, confirmButton = {
        Button(onClick = {
            onDismissRequest()
            LocalStorageData.isFirst = false
        }) {
            Text("保存")
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text("取消")
        }
    }
    )
}

