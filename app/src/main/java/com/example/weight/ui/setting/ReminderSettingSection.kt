package com.example.weight.ui.setting

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.LocalStorageData
import com.example.weight.data.reminder.ReminderScheduler
import com.example.weight.ui.common.AppPermissions
import com.example.weight.ui.common.PermissionOutcome
import com.example.weight.ui.common.rememberPermissionRequester
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * 每日称重提醒设置行组：开关（含通知权限请求与永久拒绝引导）+ 时间行。
 * 放在「提醒与推送」卡片内，由 SettingScreen 组合。
 * 打开或改时间都会以 REPLACE 策略重排 WorkManager 链。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DailyReminderRows(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snackBarShow = LocalSnackBarShow.current
    val enabled by LocalStorageData.reminderEnabled.collectAsStateWithLifecycle()
    val reminderTime by LocalStorageData.reminderTime.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }
    var showSettingsGuide by remember { mutableStateOf(false) }

    fun enableReminder() {
        LocalStorageData.reminderEnabled.update { true }
        ReminderScheduler.schedule(context)
        snackBarShow("将在每天 ${LocalStorageData.reminderTime.value} 提醒你称体重")
    }

    val notificationPermission = Manifest.permission.POST_NOTIFICATIONS

    fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AppPermissions.isGranted(context, notificationPermission)

    val permissionRequester = rememberPermissionRequester(notificationPermission) { outcome ->
        when (outcome) {
            PermissionOutcome.GRANTED -> enableReminder()
            PermissionOutcome.DENIED ->
                snackBarShow("未授予通知权限，提醒无法送达，可重新打开开关再次授权")
            PermissionOutcome.PERMANENTLY_DENIED -> showSettingsGuide = true
        }
    }

    fun onCheckedChange(checked: Boolean) {
        if (checked) {
            if (needsNotificationPermission()) permissionRequester() else enableReminder()
        } else {
            LocalStorageData.reminderEnabled.update { false }
            ReminderScheduler.cancel(context)
            snackBarShow("已关闭每日提醒")
        }
    }

    // 开关已开但权限缺失（被系统回收/永久拒绝）时展示警告行；
    // 从系统设置返回时复检，授权成功则警告自动消失
    var notificationMissing by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        notificationMissing = enabled && needsNotificationPermission()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationMissing = enabled && needsNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSwitchRow(
            label = "每日称重提醒",
            subtitle = "固定时间提醒，点通知直达记体重",
            checked = enabled,
            onCheckedChange = ::onCheckedChange,
        )
        if (notificationMissing) {
            PermissionWarningRow(
                text = "通知权限未授予，提醒将无法送达，点击去系统设置开启",
                onClick = { AppPermissions.openAppSettings(context) },
            )
        }
        // 关闭时常显置灰，避免行位置跳动
        SettingsRow(
            label = "提醒时间",
            value = reminderTime,
            valueColor = MaterialTheme.colorScheme.primary,
            enabled = enabled,
            onClick = { showTimePicker = true },
        )
        SettingsFootnote("部分国产系统可能拦截后台提醒，建议在系统设置中允许本应用通知与后台运行")
    }

    // 永久拒绝引导：系统弹窗不再出现，只能去系统设置手动开
    if (showSettingsGuide) {
        AlertDialog(
            onDismissRequest = { showSettingsGuide = false },
            title = { Text("需要通知权限") },
            text = { Text("你已选择\"不再询问\"，无法在应用内弹窗授权。请到系统设置中开启\"通知\"权限后，再回来打开提醒开关。") },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsGuide = false
                    AppPermissions.openAppSettings(context)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsGuide = false }) { Text("取消") }
            },
        )
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
                        Locale.CHINA, "%02d:%02d", pickerState.hour, pickerState.minute
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
