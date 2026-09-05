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
import com.example.weight.data.scale.BodyCompositionRecalculator
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

/** 数据管理：全量备份导出/导入等入口，导入需确认、全程有 loading */
@Composable
internal fun DataManagementCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarShow = LocalSnackBarShow.current
    val showLoading = LocalShowLoadingDialog.current
    val hideLoading = LocalHideLoadingDialog.current
    val backupRepository = koinInject<BackupRepository>()
    val recalculator = koinInject<BodyCompositionRecalculator>()
    var pendingImport by remember { mutableStateOf<ImportPreview?>(null) }
    var pendingRecalcCount by remember { mutableStateOf<Int?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            showLoading()
            try {
                val result = backupRepository.export(context, uri)
                snackBarShow(
                    "已导出 ${result.recordCount} 条体重记录、${result.dietCount} 条饮食记录" +
                        if (result.imageCount > 0) "、${result.imageCount} 张照片" else ""
                )
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
                val preview = backupRepository.previewImport(context, backup)
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
            label = "导出数据（备份包）",
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
            label = "导入数据（备份包/JSON）",
            value = "",
            onClick = {
                importLauncher.launch(
                    arrayOf("application/zip", "application/octet-stream", "application/json")
                )
            },
        )
        SettingsRow(
            label = "重算身体成分",
            value = "",
            onClick = {
                scope.launch {
                    val count = recalculator.eligibleCount()
                    if (count == 0) {
                        snackBarShow("没有可重算的记录（需本机秤测且存有阻抗）")
                    } else {
                        pendingRecalcCount = count
                    }
                }
            },
        )
        SettingsFootnote("备份包含全部记录、设置与饮食照片（带完整性校验）；导入时重复记录自动跳过")
    }

    // 重算确认弹窗：公式升级后按当前公式与档案重刷历史，覆盖不可撤销
    pendingRecalcCount?.let { count ->
        val waist = LocalStorageData.currentWaistCm.value
        AlertDialog(
            onDismissRequest = { pendingRecalcCount = null },
            title = { Text("重算身体成分") },
            text = {
                Text(
                    "将按当前公式（阻抗×腰围双路融合）与当前身体档案重算 $count 条含阻抗测量的历史记录。" +
                        "历史数值会被覆盖且不可撤销（原始阻抗保留）；无阻抗与 Health Connect 导入的记录不受影响。" +
                        if (waist <= 0) "\n\n当前未设置腰围，将退回纯阻抗公式（结果与旧口径一致），建议先在目标档案中设置腰围。" else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRecalcCount = null
                    scope.launch {
                        showLoading()
                        try {
                            val result = recalculator.recalculate()
                            snackBarShow(
                                "已重算 ${result.recalculated} 条身体成分" +
                                    if (result.skipped > 0) "，跳过 ${result.skipped} 条" else ""
                            )
                        } catch (e: Exception) {
                            snackBarShow("重算失败：${e.message ?: "未知错误"}")
                        } finally {
                            hideLoading()
                        }
                    }
                }) { Text("重算") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRecalcCount = null }) { Text("取消") }
            },
        )
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
                        "（跳过重复 ${preview.skippedDietCount} 条）" +
                        if (preview.imageCount > 0) "、恢复 ${preview.imageCount} 张照片。" else "。"
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
                                    (if (result.restoredImages > 0) "、恢复 ${result.restoredImages} 张照片" else "") +
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
