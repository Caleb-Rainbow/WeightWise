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

/**
 * 数字编辑弹窗：滚轮选择（复用 NumberSelector，与记体重弹窗同交互）。
 * 传入 resetValue 时展示「恢复默认」，点击回填该值（如 0 = 跟随默认/清除手动值）。
 */
@Composable
internal fun NumberEditDialog(
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
internal fun SingleChoiceDialog(
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
internal fun DialogOptionRow(
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
