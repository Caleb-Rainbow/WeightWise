package com.example.weight.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.BodyComposition
import com.example.weight.data.record.BodyCompositionJson
import com.example.weight.data.scale.ScaleBleEngine
import com.example.weight.ui.common.AppPermissions
import com.example.weight.ui.common.BodyCompositionGrid
import com.example.weight.ui.common.DatePickerDocked
import com.example.weight.ui.common.NumberSelector
import com.example.weight.ui.common.TimePickerOutlineTextFiled
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.flow.update
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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

    // ── 体脂秤联动：打开弹窗即开始监听，读到稳定值自动填入滚轮；读不到就手动记录 ──
    val engine = koinInject<ScaleBleEngine>()
    val scaleState by engine.state.collectAsStateWithLifecycle()
    var measuredWeight by remember { mutableStateOf<Double?>(null) }

    // 权限未授予时不自动开扫描（避免打开弹窗就弹系统授权窗），由用户点按钮发起
    val context = LocalContext.current
    var hasBlePermission by remember {
        mutableStateOf(
            AppPermissions.isGranted(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Manifest.permission.BLUETOOTH_SCAN
                } else {
                    Manifest.permission.ACCESS_FINE_LOCATION
                },
            )
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasBlePermission = grants.values.all { it }
        if (hasBlePermission) engine.startSession(autoInsert = false)
    }
    LaunchedEffect(Unit) {
        if (hasBlePermission) engine.startSession(autoInsert = false)
    }
    DisposableEffect(Unit) {
        onDispose { engine.stopSession() }
    }
    LaunchedEffect(scaleState) {
        val s = scaleState
        if (s is ScaleBleEngine.State.Done) {
            measuredWeight = s.weightKg
            weight = s.weightKg
            initialWeight = s.weightKg // 复用回填通道，滚轮动画滚到测得值
        }
    }

    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Text("记录体重")
    }, text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasBlePermission) {
                ScaleSyncHint(
                    state = scaleState,
                    composition = engine.lastComposition,
                )
                // 读秤完成后展示全指标网格；手改体重过大则视为放弃秤数据一并隐藏
                measuredWeight?.takeIf { kotlin.math.abs(it - weight) <= 0.5 }
                    ?.let { engine.lastComposition }
                    ?.takeIf { it.hasAny }
                    ?.let {
                        BodyCompositionGrid(
                            composition = it,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
            } else {
                TextButton(onClick = {
                    permissionLauncher.launch(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT,
                            )
                        } else {
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    )
                }) {
                    Text("连接体脂秤自动读取", style = MaterialTheme.typography.labelSmall)
                }
            }
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
            // 体脂秤测得值与滚轮值一致（±0.5kg）才带上成分；用户手改过大视为放弃秤数据
            val compositionJson = measuredWeight
                ?.takeIf { kotlin.math.abs(it - weight) <= 0.5 }
                ?.let { engine.lastComposition }
                ?.let { BodyCompositionJson.encode(it) }
                ?: ""
            viewModel.insertRecord(
                date = date, time = time, weight = weight, log = log,
                bodyComposition = compositionJson,
            ) {
                snackBarShow.invoke(if (compositionJson.isNotBlank()) "添加成功（含身体成分）" else "添加成功")
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

/** 弹窗顶部的秤状态提示行：读到秤时展示实测概要，读不到时提示手动输入即可 */
@Composable
private fun ScaleSyncHint(state: ScaleBleEngine.State, composition: BodyComposition?) {
    val text = when (state) {
        is ScaleBleEngine.State.Idle -> ""
        is ScaleBleEngine.State.Scanning -> "正在寻找体脂秤…（${state.secondsLeft}s）"
        is ScaleBleEngine.State.Connecting -> "正在连接体脂秤…"
        is ScaleBleEngine.State.Ready -> "已连接 ${state.deviceName}，请上秤站稳"
        is ScaleBleEngine.State.Measuring -> "体脂秤 ${state.weightKg} kg · 测量中"
        is ScaleBleEngine.State.Stabilized -> "体重已稳定，测量体脂中…"
        is ScaleBleEngine.State.Done -> buildString {
            append("已读取 ${state.weightKg} kg")
            composition?.takeIf { it.fatRatio > 0 }?.let {
                append(" · 体脂 ${it.fatRatio}% · ${it.bodyType} · ${it.bodyScore}分")
            }
        }
        is ScaleBleEngine.State.Failed -> "未连上体脂秤，手动记录即可"
    }
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (state is ScaleBleEngine.State.Failed) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
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

