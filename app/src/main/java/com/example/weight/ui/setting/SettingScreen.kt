package com.example.weight.ui.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.LocalStorageData
import com.example.weight.data.backup.BackupException
import com.example.weight.data.backup.BackupRepository
import com.example.weight.data.backup.ImportPreview
import com.example.weight.data.chat.ChatModel
import com.example.weight.data.record.RecordDao
import com.example.weight.data.widget.WidgetUpdater
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.common.NumberTextField
import com.example.weight.util.TimeUtils
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
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 15.dp)
        ) {
            val height by LocalStorageData.height.collectAsStateWithLifecycle()
            val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
            val startWeight by LocalStorageData.startWeight.collectAsStateWithLifecycle()
            val doubaoModelId by LocalStorageData.doubaoModelId.collectAsStateWithLifecycle()

            // 未手动设置起始体重时，编辑框回显第一条记录的体重作为默认值
            val recordDao = koinInject<RecordDao>()
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

            NumberTextField(
                value = height,
                onValueChange = { newValue -> LocalStorageData.height.update { newValue } },
                label = "身高(cm)",
                modifier = Modifier.fillMaxWidth(),
            )

            NumberTextField(
                value = if (startWeight > 0) startWeight else firstRecordWeight ?: 0.0,
                onValueChange = { newValue -> LocalStorageData.startWeight.update { newValue } },
                label = "起始体重(kg)",
                modifier = Modifier.fillMaxWidth(),
                supportingText = "默认取第一条记录体重，修改后实时生效；填 0 恢复默认",
            )

            NumberTextField(
                value = targetWeight,
                onValueChange = { newValue -> LocalStorageData.targetWeight.update { newValue } },
                label = "目标体重(kg)",
                modifier = Modifier.fillMaxWidth(),
            )

            // 模型选择
            var expanded by remember { mutableStateOf(false) }
            val selectedModel = ChatModel.entries.find { it.value == doubaoModelId }
                ?: ChatModel.DOUBAO_SEED_2_0_LITE
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                val fillMaxWidth = Modifier.fillMaxWidth()
                OutlinedTextField(
                    value = selectedModel.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("模型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = fillMaxWidth.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    ChatModel.entries.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.displayName) },
                            onClick = {
                                LocalStorageData.doubaoModelId.update { model.value }
                                expanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "提醒", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            ReminderSettingSection()

            DataManagementSection()
        }
    }
}

/** 数据管理：全量备份导出/导入等入口 */
@Composable
private fun DataManagementSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarShow = LocalSnackBarShow.current
    val backupRepository = koinInject<BackupRepository>()
    var pendingImport by remember { mutableStateOf<ImportPreview?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val result = backupRepository.export(context, uri)
                snackBarShow("已导出 ${result.recordCount} 条体重记录、${result.dietCount} 条饮食记录")
            } catch (e: Exception) {
                snackBarShow("导出失败：${e.message ?: "未知错误"}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
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
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val count = backupRepository.exportRecordsCsv(context, uri)
                snackBarShow("已导出 $count 条体重记录（CSV）")
            } catch (e: Exception) {
                snackBarShow("导出失败：${e.message ?: "未知错误"}")
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(12.dp))
    Text(text = "数据管理", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            exportLauncher.launch(
                BackupRepository.defaultExportFileName(TimeUtils.getCurrentDate())
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.Save,
            contentDescription = null,
            modifier = Modifier.width(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "导出数据（JSON）")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            csvExportLauncher.launch(
                BackupRepository.defaultCsvFileName(TimeUtils.getCurrentDate())
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.TableChart,
            contentDescription = null,
            modifier = Modifier.width(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "导出体重（CSV）")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { importLauncher.launch(arrayOf("application/json")) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.Restore,
            contentDescription = null,
            modifier = Modifier.width(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "导入数据（JSON）")
    }
    Text(
        text = "导出全部体重与饮食记录及设置；导入时重复记录自动跳过，饮食图片不入备份包",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )

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
                        try {
                            val result = backupRepository.importBackup(context, toImport.backup)
                            snackBarShow(
                                "导入完成：新增 ${result.insertedRecords} 条体重、" +
                                    "${result.insertedDietRecords} 条饮食" +
                                    if (result.settingsApplied) "，设置已更新" else ""
                            )
                        } catch (e: Exception) {
                            snackBarShow("导入失败：${e.message ?: "未知错误"}")
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
