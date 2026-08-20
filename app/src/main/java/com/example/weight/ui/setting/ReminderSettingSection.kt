package com.example.weight.ui.setting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.LocalStorageData
import com.example.weight.data.reminder.ReminderScheduler
import kotlinx.coroutines.flow.update

/**
 * 每日称重提醒设置：开关（含通知权限请求）+ 时间选择。
 * 打开或改时间都会以 REPLACE 策略重排 WorkManager 链。
 */
@Composable
fun ReminderSettingSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snackBarShow = LocalSnackBarShow.current
    val enabled by LocalStorageData.reminderEnabled.collectAsStateWithLifecycle()
    val reminderTime by LocalStorageData.reminderTime.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }

    fun enableReminder() {
        LocalStorageData.reminderEnabled.update { true }
        ReminderScheduler.schedule(context)
        snackBarShow("将在每天 $reminderTime 提醒你称体重")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableReminder()
        else snackBarShow("未授予通知权限，无法发送提醒")
    }

    fun onCheckedChange(checked: Boolean) {
        if (checked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                enableReminder()
            }
        } else {
            LocalStorageData.reminderEnabled.update { false }
            ReminderScheduler.cancel(context)
            snackBarShow("已关闭每日提醒")
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "每日称重提醒", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "固定时间提醒，点通知直达记体重",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = ::onCheckedChange)
        }
        if (enabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimePicker = true }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "提醒时间", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = reminderTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "部分国产系统可能拦截后台提醒，建议在系统设置中允许本应用通知与后台运行",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }

    if (showTimePicker) {
        val parts = reminderTime.split(":")
        val pickerState = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 7,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 30,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择提醒时间") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val time = String.format(
                        java.util.Locale.CHINA, "%02d:%02d", pickerState.hour, pickerState.minute
                    )
                    LocalStorageData.reminderTime.update { time }
                    ReminderScheduler.schedule(context, time)
                    showTimePicker = false
                    snackBarShow("提醒时间已改为 $time")
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
        )
    }
}
