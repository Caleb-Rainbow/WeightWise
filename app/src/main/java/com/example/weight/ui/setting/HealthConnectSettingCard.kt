package com.example.weight.ui.setting

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.health.HealthConnectAvailability
import com.example.weight.data.health.HealthConnectManager
import com.example.weight.data.health.HealthConnectState
import com.example.weight.util.TimeUtils
import java.text.DecimalFormat
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun HealthConnectSettingCard(
    manager: HealthConnectManager = koinInject(),
) {
    val state by manager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showSnackbar = LocalSnackBarShow.current
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        scope.launch {
            manager.onPermissionsResult(granted)
            if (granted.containsAll(HealthConnectManager.PERMISSIONS)) {
                showSnackbar("Health Connect 已连接并完成同步")
            } else {
                showSnackbar("权限不完整，暂未开启同步")
            }
        }
    }

    LaunchedEffect(manager) { manager.refreshStatus() }

    SettingsCard(title = "Health Connect") {
        when (state.availability) {
            HealthConnectAvailability.AVAILABLE -> {
                SettingsSwitchRow(
                    label = "同步健康数据",
                    checked = state.enabled && state.hasAllPermissions,
                    subtitle = healthConnectSubtitle(state),
                    onCheckedChange = { checked ->
                        if (checked && !state.hasAllPermissions) {
                            permissionLauncher.launch(HealthConnectManager.PERMISSIONS)
                        } else {
                            scope.launch { manager.setEnabled(checked) }
                        }
                    },
                )
                if (state.enabled) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        label = "立即同步",
                        value = if (state.isSyncing) "同步中…" else lastSyncText(state.lastSyncAt),
                        enabled = !state.isSyncing && state.hasAllPermissions,
                        onClick = {
                            scope.launch {
                                val result = manager.syncNow()
                                if (result != null) {
                                    showSnackbar(
                                        "同步完成：导入 ${result.importedWeights + result.importedNutrition} 条，" +
                                            "写入 ${result.exportedRecords + result.exportedNutrition} 条"
                                    )
                                } else if (manager.state.value.error != null) {
                                    showSnackbar(manager.state.value.error!!)
                                }
                            }
                        },
                    )
                    SettingsRow(
                        label = "管理访问权限",
                        value = "前往 Health Connect",
                        onClick = {
                            runCatching { context.startActivity(manager.manageAccessIntent()) }
                                .onFailure { showSnackbar("无法打开 Health Connect") }
                        },
                    )
                    state.summary?.let { summary ->
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        HealthSummaryRow(
                            days = summary.rangeDays,
                            steps = summary.steps,
                            calories = summary.totalCaloriesBurned,
                            sleepMinutes = summary.sleepMinutes,
                        )
                    }
                }
                state.error?.let { SettingsFootnote("同步提示：$it") }
                SettingsFootnote("同步体重、体脂、去脂体重、水分量、骨量与饮食；读取步数、总消耗和睡眠。仅在 App 前台同步。")
            }

            HealthConnectAvailability.UPDATE_REQUIRED -> {
                SettingsRow(
                    label = "安装或更新 Health Connect",
                    value = "前往应用商店",
                    onClick = {
                        try {
                            context.startActivity(manager.installOrUpdateIntent())
                        } catch (_: ActivityNotFoundException) {
                            showSnackbar("未找到可用的应用商店")
                        }
                    },
                )
                SettingsFootnote("Android 13 及以下需要安装 Health Connect；Android 14 起由系统提供。")
            }

            HealthConnectAvailability.UNAVAILABLE -> {
                SettingsFootnote("此设备暂不支持 Health Connect。该能力需要 Android 9 以上及 Google Play 服务。")
            }
        }
    }
}

@Composable
private fun HealthSummaryRow(days: Int, steps: Long, calories: Int, sleepMinutes: Long) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("近 $days 天健康摘要", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HealthSummaryItem("日均步数", DecimalFormat("#,###").format(steps / days.coerceAtLeast(1)))
            Spacer(Modifier.width(12.dp))
            HealthSummaryItem("总消耗", "$calories kcal")
            Spacer(Modifier.width(12.dp))
            val sleepHours = sleepMinutes.toDouble() / 60 / days.coerceAtLeast(1)
            HealthSummaryItem("日均睡眠", String.format(java.util.Locale.CHINA, "%.1f h", sleepHours))
        }
    }
}

@Composable
private fun HealthSummaryItem(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.bodyMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun healthConnectSubtitle(state: HealthConnectState): String = when {
    state.isSyncing -> "正在同步"
    state.enabled && state.hasAllPermissions -> "已连接 · 前台自动同步"
    state.enabled -> "需要重新授权"
    else -> "与其他健康应用交换数据"
}

private fun lastSyncText(timestamp: Long): String =
    if (timestamp <= 0) "尚未同步" else "上次 ${TimeUtils.convertMillisToDate(timestamp)} ${TimeUtils.convertMillisToHM(timestamp)}"
