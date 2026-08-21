package com.example.weight.ui.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalHideLoadingDialog
import com.example.weight.LocalShowLoadingDialog
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.LocalStorageData
import com.example.weight.data.backup.BackupException
import com.example.weight.data.backup.BackupRepository
import com.example.weight.data.backup.ImportPreview
import com.example.weight.data.chat.ChatModel
import com.example.weight.data.record.RecordDao
import com.example.weight.data.widget.WidgetUpdater
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.common.NumberSelector
import com.example.weight.util.ActivityLevel
import com.example.weight.util.CalorieCalculator
import com.example.weight.util.Gender
import com.example.weight.util.TimeUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(modifier: Modifier = Modifier, goBack: () -> Unit) {
    Scaffold(modifier = modifier, topBar = {
        MyTopBar(title = "设置", goBack = goBack)
    }) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { GoalCard() }
            item { ProfileCard() }
            item { ReminderPushCard() }
            item { AiModelCard() }
            item { DataManagementCard() }
        }
    }
}

/** 目标：目标/起始体重，改动实时刷新小组件与首页进度 */
@Composable
private fun GoalCard() {
    val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
    val startWeight by LocalStorageData.startWeight.collectAsStateWithLifecycle()
    val recordDao = koinInject<RecordDao>()

    // 未手动设置起始体重时，回显第一条记录的体重作为默认值
    var firstRecordWeight by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            firstRecordWeight = recordDao.getFirstData()?.weight
        }
    }

    // 目标/起始体重直接影响小组件进度，改动后防抖半秒刷新一次；
    // drop(1) 跳过进入页面时的初始值，避免无谓刷新
    val widgetUpdater = koinInject<WidgetUpdater>()
    LaunchedEffect(Unit) {
        combine(
            LocalStorageData.targetWeight,
            LocalStorageData.startWeight,
        ) { target, start -> target to start }
            .drop(1)
            .collectLatest {
                delay(500)
                widgetUpdater.notifyDataChanged()
            }
    }

    var editingTarget by remember { mutableStateOf(false) }
    var editingStart by remember { mutableStateOf(false) }
    val effectiveStart = if (startWeight > 0) startWeight else firstRecordWeight

    SettingsCard(title = "目标") {
        SettingsRow(
            label = "目标体重",
            value = if (targetWeight > 0) "${targetWeight.formatPlain()} kg" else "未设置",
            valueColor = unsetOrValue(targetWeight > 0),
            onClick = { editingTarget = true },
        )
        SettingsRow(
            label = "起始体重",
            value = effectiveStart?.let { "${it.formatPlain()} kg" } ?: "未设置",
            valueColor = unsetOrValue(effectiveStart != null),
            onClick = { editingStart = true },
        )
        SettingsFootnote("起始体重默认取第一条记录，改动实时刷新小组件")
    }

    if (editingTarget) {
        NumberEditDialog(
            title = "目标体重",
            initialValue = if (targetWeight > 0) targetWeight else 75.0,
            integerRange = 30..200,
            unit = "kg",
            resetValue = 0.0,
            onConfirm = { value -> LocalStorageData.targetWeight.update { value } },
            onDismiss = { editingTarget = false },
        )
    }
    if (editingStart) {
        NumberEditDialog(
            title = "起始体重",
            initialValue = effectiveStart ?: 70.0,
            integerRange = 30..200,
            unit = "kg",
            resetValue = 0.0,
            onConfirm = { value -> LocalStorageData.startWeight.update { value } },
            onDismiss = { editingStart = false },
        )
    }
}

/** 身体档案：身高/年龄/性别/活动水平，齐全时展示基础代谢与建议摄入成果条 */
@Composable
private fun ProfileCard() {
    val age by LocalStorageData.age.collectAsStateWithLifecycle()
    val gender by LocalStorageData.gender.collectAsStateWithLifecycle()
    val activityLevel by LocalStorageData.activityLevel.collectAsStateWithLifecycle()
    val height by LocalStorageData.height.collectAsStateWithLifecycle()
    val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
    val recordDao = koinInject<RecordDao>()

    // 最新体重，用于档案热量预览
    var latestWeight by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            latestWeight = recordDao.getLastData()?.weight
        }
    }

    var editingHeight by remember { mutableStateOf(false) }
    var editingAge by remember { mutableStateOf(false) }
    var editingGender by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf(false) }

    val selectedGender = Gender.entries.find { it.name == gender }
    val selectedActivity = ActivityLevel.entries.find { it.name == activityLevel }

    SettingsCard(title = "身体档案") {
        SettingsRow(
            label = "身高",
            value = if (height > 0) "${height.formatPlain()} cm" else "未设置",
            valueColor = unsetOrValue(height > 0),
            onClick = { editingHeight = true },
        )
        SettingsRow(
            label = "年龄",
            value = if (age > 0) "$age 岁" else "未设置",
            valueColor = unsetOrValue(age > 0),
            onClick = { editingAge = true },
        )
        SettingsRow(
            label = "性别",
            value = selectedGender?.displayName ?: "未设置",
            valueColor = unsetOrValue(selectedGender != null),
            onClick = { editingGender = true },
        )
        SettingsRow(
            label = "活动水平",
            value = selectedActivity?.displayName ?: "未设置",
            valueColor = unsetOrValue(selectedActivity != null),
            onClick = { editingActivity = true },
        )

        val latest = latestWeight
        val intakeInfo = if (latest != null && selectedGender != null && selectedActivity != null) {
            val bmrValue = CalorieCalculator.bmr(selectedGender, latest, height, age)
            val intake = CalorieCalculator.recommendedIntake(
                gender = selectedGender,
                weightKg = latest,
                heightCm = height,
                age = age,
                activityLevel = selectedActivity,
                targetWeightKg = targetWeight,
            )
            if (bmrValue != null && intake != null) bmrValue.roundToInt() to intake else null
        } else null

        if (intakeInfo != null) {
            IntakeResultStrip(bmr = intakeInfo.first, intake = intakeInfo.second)
        } else {
            SettingsFootnote("补全年龄、性别与活动水平后，这里会显示每日建议摄入")
        }
    }

    if (editingHeight) {
        NumberEditDialog(
            title = "身高",
            initialValue = if (height > 0) height else 170.0,
            integerRange = 100..250,
            unit = "cm",
            onConfirm = { value -> LocalStorageData.height.update { value } },
            onDismiss = { editingHeight = false },
        )
    }
    if (editingAge) {
        NumberEditDialog(
            title = "年龄",
            initialValue = if (age > 0) age.toDouble() else 25.0,
            integerRange = 10..100,
            unit = "岁",
            showDecimal = false,
            resetValue = 0.0,
            onConfirm = { value -> LocalStorageData.age.update { value.toInt() } },
            onDismiss = { editingAge = false },
        )
    }
    if (editingGender) {
        SingleChoiceDialog(
            title = "选择性别",
            options = Gender.entries.map { it.name to it.displayName },
            selected = gender,
            includeUnset = true,
            onSelect = { value -> LocalStorageData.gender.update { value } },
            onDismiss = { editingGender = false },
        )
    }
    if (editingActivity) {
        SingleChoiceDialog(
            title = "选择活动水平",
            options = ActivityLevel.entries.map { it.name to it.displayName },
            selected = activityLevel,
            includeUnset = true,
            onSelect = { value -> LocalStorageData.activityLevel.update { value } },
            onDismiss = { editingActivity = false },
        )
    }
}

/** 提醒与推送：每日称重提醒与周报推送合入一张卡 */
@Composable
private fun ReminderPushCard() {
    SettingsCard(title = "提醒与推送") {
        DailyReminderRows()
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        WeeklyPushRows()
    }
}

/** AI 分析：模型选择，用于 AI 分析与运动计划生成 */
@Composable
private fun AiModelCard() {
    val doubaoModelId by LocalStorageData.doubaoModelId.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val selectedModel = ChatModel.entries.find { it.value == doubaoModelId }
        ?: ChatModel.DOUBAO_SEED_2_0_LITE

    SettingsCard(title = "AI 分析") {
        SettingsRow(
            label = "AI 模型",
            value = selectedModel.displayName,
            onClick = { editing = true },
        )
        SettingsFootnote("用于 AI 分析与运动计划生成")
    }

    if (editing) {
        SingleChoiceDialog(
            title = "选择 AI 模型",
            options = ChatModel.entries.map { it.value to it.displayName },
            selected = selectedModel.value,
            includeUnset = false,
            onSelect = { value -> LocalStorageData.doubaoModelId.update { value } },
            onDismiss = { editing = false },
        )
    }
}

/** 数据管理：全量备份导出/导入等入口，导入需确认、全程有 loading */
@Composable
private fun DataManagementCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarShow = LocalSnackBarShow.current
    val showLoading = LocalShowLoadingDialog.current
    val hideLoading = LocalHideLoadingDialog.current
    val backupRepository = koinInject<BackupRepository>()
    var pendingImport by remember { mutableStateOf<ImportPreview?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            showLoading()
            try {
                val result = backupRepository.export(context, uri)
                snackBarShow("已导出 ${result.recordCount} 条体重记录、${result.dietCount} 条饮食记录")
            } catch (e: Exception) {
                snackBarShow("导出失败：${e.message ?: "未知错误"}")
            } finally {
                hideLoading()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            showLoading()
            try {
                val backup = backupRepository.parseBackup(context, uri)
                val preview = backupRepository.previewImport(backup)
                if (preview.newRecordCount == 0 && preview.newDietCount == 0) {
                    snackBarShow("备份里没有新数据，均与现有记录重复")
                } else {
                    pendingImport = preview
                }
            } catch (e: BackupException) {
                snackBarShow(e.message ?: "导入失败")
            } catch (e: Exception) {
                snackBarShow("导入失败：${e.message ?: "未知错误"}")
            } finally {
                hideLoading()
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            showLoading()
            try {
                val count = backupRepository.exportRecordsCsv(context, uri)
                snackBarShow("已导出 $count 条体重记录（CSV）")
            } catch (e: Exception) {
                snackBarShow("导出失败：${e.message ?: "未知错误"}")
            } finally {
                hideLoading()
            }
        }
    }

    SettingsCard(title = "数据管理") {
        SettingsRow(
            label = "导出数据（JSON）",
            value = "",
            onClick = {
                exportLauncher.launch(
                    BackupRepository.defaultExportFileName(TimeUtils.getCurrentDate())
                )
            },
        )
        SettingsRow(
            label = "导出体重（CSV）",
            value = "",
            onClick = {
                csvExportLauncher.launch(
                    BackupRepository.defaultCsvFileName(TimeUtils.getCurrentDate())
                )
            },
        )
        SettingsRow(
            label = "导入数据（JSON）",
            value = "",
            onClick = { importLauncher.launch(arrayOf("application/json")) },
        )
        SettingsFootnote("导出全部体重与饮食记录及设置；导入时重复记录自动跳过，饮食图片不入备份包")
    }

    // 导入确认弹窗：展示去重后的数量，用户确认才写入
    pendingImport?.let { preview ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("确认导入") },
            text = {
                Text(
                    "将导入 ${preview.newRecordCount} 条体重记录" +
                        "（跳过重复 ${preview.skippedRecordCount} 条）、" +
                        "${preview.newDietCount} 条饮食记录" +
                        "（跳过重复 ${preview.skippedDietCount} 条）。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val toImport = preview
                    pendingImport = null
                    scope.launch {
                        showLoading()
                        try {
                            val result = backupRepository.importBackup(context, toImport)
                            snackBarShow(
                                "导入完成：新增 ${result.insertedRecords} 条体重、" +
                                    "${result.insertedDietRecords} 条饮食" +
                                    if (result.settingsApplied) "，设置已更新" else ""
                            )
                        } catch (e: Exception) {
                            snackBarShow("导入失败：${e.message ?: "未知错误"}")
                        } finally {
                            hideLoading()
                        }
                    }
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            },
        )
    }
}

/** 建议摄入成果条：档案卡的回报，主题色容器高亮 */
@Composable
private fun IntakeResultStrip(bmr: Int, intake: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row {
                Text(
                    text = "每日建议摄入 ",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = "$intake",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = " kcal",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(
                text = "基础代谢约 $bmr kcal · 已结合目标体重调整",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 数字编辑弹窗：滚轮选择（复用 NumberSelector，与记体重弹窗同交互）。
 * 传入 resetValue 时展示「恢复默认」，点击回填该值（如 0 = 跟随默认/清除手动值）。
 */
@Composable
private fun NumberEditDialog(
    title: String,
    initialValue: Double,
    integerRange: IntRange,
    unit: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
    showDecimal: Boolean = true,
    resetValue: Double? = null,
) {
    var selected by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NumberSelector(
                    integerList = remember(integerRange) { integerRange.toList() },
                    decimalList = remember { (0..9).toList() },
                    onWeightChange = { selected = it },
                    initialWeight = initialValue,
                    unit = unit,
                    showDecimal = showDecimal,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(selected)
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            Row {
                if (resetValue != null) {
                    TextButton(onClick = {
                        onConfirm(resetValue)
                        onDismiss()
                    }) { Text("恢复默认") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/** 单选弹窗：性别/活动水平/AI 模型；includeUnset 时首项为「未设置」清除项 */
@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    includeUnset: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (includeUnset) {
                    DialogOptionRow(
                        text = "未设置",
                        isSelected = selected.isNullOrEmpty(),
                        onClick = {
                            onSelect("")
                            onDismiss()
                        },
                    )
                }
                options.forEach { (value, displayName) ->
                    DialogOptionRow(
                        text = displayName,
                        isSelected = selected == value,
                        onClick = {
                            onSelect(value)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun DialogOptionRow(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 未设置值用主题色提示可补充，已设置用默认次要色 */
@Composable
private fun unsetOrValue(isSet: Boolean) =
    if (isSet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
