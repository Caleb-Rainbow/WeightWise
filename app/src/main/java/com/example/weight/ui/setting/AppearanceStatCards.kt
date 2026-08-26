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

/** 统计口径：一天多次称重时的折算方式，影响首页图表、报告与小组件趋势 */
@Composable
internal fun DailyStatModeCard() {
    val modeId by LocalStorageData.dailyStatMode.collectAsStateWithLifecycle()
    val current = DailyStatMode.fromId(modeId)
    val widgetUpdater = koinInject<WidgetUpdater>()
    var editing by remember { mutableStateOf(false) }

    // 口径影响小组件趋势线与 7 天均值，切换后主动刷新（小组件取数是一次性读取）
    LaunchedEffect(Unit) {
        LocalStorageData.dailyStatMode
            .drop(1)
            .collectLatest { widgetUpdater.notifyDataChanged() }
    }

    SettingsCard(title = "统计") {
        SettingsRow(
            label = "每日体重口径",
            value = current.label,
            onClick = { editing = true },
        )
        SettingsFootnote("一天多次称重时的折算方式，影响首页图表、报告与小组件趋势")
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("每日体重口径") },
            text = {
                Column {
                    DailyStatMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LocalStorageData.dailyStatMode.update { mode.name }
                                    editing = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == current, onClick = null)
                            Text(mode.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

/** 外观(主题中心):5 套配色圆点即点即换 + 深浅模式三态;改动实时刷新小组件配色 */
@Composable
internal fun AppearanceCard() {
    val themeId by LocalStorageData.themeId.collectAsStateWithLifecycle()
    val appearanceMode by LocalStorageData.appearanceMode.collectAsStateWithLifecycle()
    val currentPreset = ThemePreset.fromId(themeId)
    val currentMode = AppearanceMode.fromId(appearanceMode)
    val widgetUpdater = koinInject<WidgetUpdater>()

    // 主题/深浅变化影响小组件配色,主动触发一次重绘
    LaunchedEffect(Unit) {
        combine(LocalStorageData.themeId, LocalStorageData.appearanceMode) { _, _ -> }
            .drop(1)
            .collectLatest { widgetUpdater.notifyDataChanged() }
    }

    SettingsCard(title = "外观") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemePreset.entries.forEach { preset ->
                ThemeSwatch(
                    preset = preset,
                    selected = preset == currentPreset,
                    onClick = { LocalStorageData.themeId.update { preset.name } },
                )
            }
        }
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            AppearanceMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == currentMode,
                    onClick = { LocalStorageData.appearanceMode.update { mode.name } },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AppearanceMode.entries.size,
                    ),
                ) {
                    Text(mode.label)
                }
            }
        }
        SettingsFootnote("主题即时生效，桌面小组件同步换色")
    }
}

/** 主题色板圆点:48dp 触控、40dp 视觉圆(浅色 primary 底),选中描边+勾 */
@Composable
internal fun ThemeSwatch(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(preset.swatch)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "已选${preset.label}",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Text(
            preset.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
