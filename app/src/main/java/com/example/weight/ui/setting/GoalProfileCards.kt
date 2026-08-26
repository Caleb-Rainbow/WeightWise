package com.example.weight.ui.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.weight.data.record.DailyStatMode
import com.example.weight.data.record.RecordDao
import com.example.weight.data.widget.WidgetUpdater
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.common.PageLead
import com.example.weight.ui.common.WeightWiseDimens
import com.example.weight.ui.common.NumberSelector
import com.example.weight.ui.theme.AppearanceMode
import com.example.weight.ui.theme.ThemePreset
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

/** 目标：目标/起始体重，改动实时刷新小组件与首页进度 */
@Composable
internal fun GoalCard() {
    val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
    val startWeight by LocalStorageData.startWeight.collectAsStateWithLifecycle()
    val weeklyTargetChangeKg by LocalStorageData.weeklyTargetChangeKg.collectAsStateWithLifecycle()
    val stageGoalStepKg by LocalStorageData.stageGoalStepKg.collectAsStateWithLifecycle()
    val targetWaistCm by LocalStorageData.targetWaistCm.collectAsStateWithLifecycle()
    val targetBodyFatPercent by LocalStorageData.targetBodyFatPercent.collectAsStateWithLifecycle()
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
    var editingWeeklyRate by remember { mutableStateOf(false) }
    var editingStageStep by remember { mutableStateOf(false) }
    var editingTargetWaist by remember { mutableStateOf(false) }
    var editingTargetBodyFat by remember { mutableStateOf(false) }
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
        SettingsRow(
            label = "每周目标速度",
            value = "${weeklyTargetChangeKg.formatPlain()} kg/周",
            onClick = { editingWeeklyRate = true },
        )
        SettingsRow(
            label = "阶段目标间隔",
            value = "${stageGoalStepKg.formatPlain()} kg",
            onClick = { editingStageStep = true },
        )
        SettingsRow(
            label = "目标腰围",
            value = if (targetWaistCm > 0) "${targetWaistCm.formatPlain()} cm" else "未设置",
            valueColor = unsetOrValue(targetWaistCm > 0),
            onClick = { editingTargetWaist = true },
        )
        SettingsRow(
            label = "目标体脂率",
            value = if (targetBodyFatPercent > 0) "${targetBodyFatPercent.formatPlain()} %" else "未设置",
            valueColor = unsetOrValue(targetBodyFatPercent > 0),
            onClick = { editingTargetBodyFat = true },
        )
        SettingsFootnote("目标速度会用于计划日期与减重热量建议；阶段目标帮助拆分长期目标")
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
    if (editingWeeklyRate) {
        NumberEditDialog(
            title = "每周目标速度",
            initialValue = weeklyTargetChangeKg,
            integerRange = 0..1,
            unit = "kg/周",
            onConfirm = { value ->
                LocalStorageData.weeklyTargetChangeKg.update { value.coerceIn(0.1, 1.0) }
            },
            onDismiss = { editingWeeklyRate = false },
        )
    }
    if (editingStageStep) {
        NumberEditDialog(
            title = "阶段目标间隔",
            initialValue = stageGoalStepKg,
            integerRange = 1..10,
            unit = "kg",
            onConfirm = { value -> LocalStorageData.stageGoalStepKg.update { value.coerceAtLeast(0.5) } },
            onDismiss = { editingStageStep = false },
        )
    }
    if (editingTargetWaist) {
        NumberEditDialog(
            title = "目标腰围",
            initialValue = if (targetWaistCm > 0) targetWaistCm else 75.0,
            integerRange = 40..200,
            unit = "cm",
            resetValue = 0.0,
            onConfirm = { value -> LocalStorageData.targetWaistCm.update { value } },
            onDismiss = { editingTargetWaist = false },
        )
    }
    if (editingTargetBodyFat) {
        NumberEditDialog(
            title = "目标体脂率",
            initialValue = if (targetBodyFatPercent > 0) targetBodyFatPercent else 20.0,
            integerRange = 5..60,
            unit = "%",
            resetValue = 0.0,
            onConfirm = { value -> LocalStorageData.targetBodyFatPercent.update { value } },
            onDismiss = { editingTargetBodyFat = false },
        )
    }
}

/** 身体档案：身高/年龄/性别/活动水平，齐全时展示基础代谢与建议摄入成果条 */@Composable
internal fun ProfileCard() {
    val age by LocalStorageData.age.collectAsStateWithLifecycle()
    val gender by LocalStorageData.gender.collectAsStateWithLifecycle()
    val activityLevel by LocalStorageData.activityLevel.collectAsStateWithLifecycle()
    val height by LocalStorageData.height.collectAsStateWithLifecycle()
    val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
    val weeklyTargetChangeKg by LocalStorageData.weeklyTargetChangeKg.collectAsStateWithLifecycle()
    val currentWaistCm by LocalStorageData.currentWaistCm.collectAsStateWithLifecycle()
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
    var editingWaist by remember { mutableStateOf(false) }

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
        SettingsRow(
            label = "当前腰围",
            value = if (currentWaistCm > 0) "${currentWaistCm.formatPlain()} cm" else "未设置",
            valueColor = unsetOrValue(currentWaistCm > 0),
            onClick = { editingWaist = true },
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
                weeklyTargetChangeKg = weeklyTargetChangeKg,
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
    if (editingWaist) {
        NumberEditDialog(
            title = "当前腰围",
            initialValue = if (currentWaistCm > 0) currentWaistCm else 80.0,
            integerRange = 40..200,
            unit = "cm",
            resetValue = 0.0,
            onConfirm = { value -> LocalStorageData.currentWaistCm.update { value } },
            onDismiss = { editingWaist = false },
        )
    }
}

/** 建议摄入成果条：档案卡的回报，主题色容器高亮 */
@Composable
internal fun IntakeResultStrip(bmr: Int, intake: Int) {
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

/** 未设置值用主题色提示可补充，已设置用默认次要色 */
@Composable
internal fun unsetOrValue(isSet: Boolean) =
    if (isSet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
