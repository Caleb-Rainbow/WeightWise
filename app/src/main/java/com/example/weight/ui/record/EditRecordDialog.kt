package com.example.weight.ui.record

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.weight.data.record.Record
import com.example.weight.ui.common.DatePickerDocked
import com.example.weight.ui.common.NumberSelector
import com.example.weight.ui.common.TimePickerOutlineTextFiled
import com.example.weight.util.TimeUtils

/**
 * 编辑已有的体重记录弹窗，日期/时间/体重/日志均按原记录回填，
 * 确认后回调携带完整的新 Record（保留原 id）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecordDialog(
    record: Record,
    onDismissRequest: () -> Unit,
    onConfirm: (Record) -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = TimeUtils.convertDateToUtcMillis(
            TimeUtils.convertMillisToDate(record.timestamp)
        )
    )
    val date = pickerState.selectedDateMillis?.let {
        TimeUtils.convertUtcMillisToDate(it)
    } ?: ""
    var time by remember { mutableStateOf(TimeUtils.convertMillisToHM(record.timestamp)) }
    var weight by remember { mutableDoubleStateOf(record.weight) }
    var log by remember { mutableStateOf(record.log) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("编辑记录") },
        text = {
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
                        onValueChanged = { time = it },
                        isRequired = true,
                        singleLine = true
                    )
                }
                NumberSelector(
                    integerList = remember { (50..120).toList() },
                    decimalList = remember { (0..9).toList() },
                    onWeightChange = { weight = it },
                    initialWeight = record.weight,
                    unit = "kg"
                )
                OutlinedTextField(
                    value = log,
                    onValueChange = { log = it },
                    label = { Text("日志/记录") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    record.copy(
                        weight = weight,
                        log = log,
                        timestamp = TimeUtils.convertTimeToMillis("$date $time:00")
                    )
                )
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        }
    )
}
