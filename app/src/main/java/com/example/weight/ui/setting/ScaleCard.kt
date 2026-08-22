package com.example.weight.ui.setting

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.scale.ScaleBleEngine
import com.example.weight.data.widget.WidgetUpdater
import org.koin.compose.koinInject

/**
 * 体脂秤连接卡：发起称重会话 → 展示状态机进度 → 稳定体重自动入库。
 * 底部折叠原始蓝牙数据日志，供未知协议帧的现场分析。
 */
@Composable
fun ScaleCard() {
    val context = LocalContext.current
    val engine = koinInject<ScaleBleEngine>()
    val widgetUpdater = koinInject<WidgetUpdater>()
    val snackBarShow = LocalSnackBarShow.current

    val state by engine.state.collectAsStateWithLifecycle()
    val rawLog by engine.rawLog.collectAsStateWithLifecycle()
    var showLog by remember { mutableStateOf(false) }

    // 入库后刷新小组件并提示；state 变化驱动，Done 只会触发一次
    LaunchedEffect(state) {
        val s = state
        if (s is ScaleBleEngine.State.Done) {
            widgetUpdater.notifyDataChanged()
            snackBarShow("已记录体重 ${s.weightKg} kg")
        }
    }

    // 离开设置页即结束会话、释放蓝牙资源
    DisposableEffect(Unit) {
        onDispose { engine.stopSession() }
    }

    // Android 12+ 扫描与连接是两个独立运行时权限，必须同时请求；11 及以下 BLE 扫描走精确定位
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            engine.startSession()
        } else {
            snackBarShow("需要附近设备权限才能连接体脂秤")
        }
    }

    SettingsCard(title = "体脂秤") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val s = state) {
                is ScaleBleEngine.State.Idle -> {
                    Text(
                        "支持蓝牙名称为 icomon 的体脂秤，上秤称重后自动记入体重",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { permissionLauncher.launch(requiredPermissions) }, modifier = Modifier.fillMaxWidth()) {
                        Text("开始称重")
                    }
                }

                is ScaleBleEngine.State.Scanning -> {
                    StatusText("正在搜索体脂秤…（${s.secondsLeft}s）\n若一直搜不到，请先站上秤唤醒它")
                    OutlinedButton(
                        onClick = { engine.stopSession() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("取消") }
                }

                is ScaleBleEngine.State.Connecting -> StatusText("正在连接…")

                is ScaleBleEngine.State.Ready -> {
                    StatusText("已连接 ${s.deviceName}，请光脚上秤站稳（测体脂需光脚）")
                    OutlinedButton(
                        onClick = { engine.stopSession() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("结束") }
                }

                is ScaleBleEngine.State.Measuring -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${s.weightKg}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "kg · 测量中",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }

                is ScaleBleEngine.State.Stabilized -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${s.weightKg}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "kg · 已稳定，测量体脂中…",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }

                is ScaleBleEngine.State.Done -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${s.weightKg}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                "kg · 已记录" + (s.fatRatio?.let { " · 体脂 ${it}%" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (s.impedanceOhm != null) {
                                Text(
                                    "阻抗 ${s.impedanceOhm.toInt()} Ω",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Button(onClick = { engine.startSession() }, modifier = Modifier.fillMaxWidth()) {
                        Text("再称一次")
                    }
                }

                is ScaleBleEngine.State.Failed -> {
                    StatusText(s.message, isError = true)
                    Button(onClick = { engine.startSession() }, modifier = Modifier.fillMaxWidth()) {
                        Text("重试")
                    }
                }
            }

            if (rawLog.isNotEmpty()) {
                OutlinedButton(onClick = { showLog = !showLog }) {
                    Text(if (showLog) "收起调试日志" else "查看调试日志（${rawLog.size}）")
                }
                if (showLog) {
                    Text(
                        rawLog.joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}
