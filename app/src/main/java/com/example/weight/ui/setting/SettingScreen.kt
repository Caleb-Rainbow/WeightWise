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
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.runtime.Composable
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
import com.example.weight.data.backup.BackupRepository
import com.example.weight.data.chat.ChatModel
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.common.NumberTextField
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
            val doubaoModelId by LocalStorageData.doubaoModelId.collectAsStateWithLifecycle()

            NumberTextField(
                value = height,
                onValueChange = { newValue -> LocalStorageData.height.update { newValue } },
                label = "身高(cm)",
                modifier = Modifier.fillMaxWidth(),
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

            DataManagementSection()
        }
    }
}

/** 数据管理：全量备份导出等入口 */
@Composable
private fun DataManagementSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarShow = LocalSnackBarShow.current
    val backupRepository = koinInject<BackupRepository>()

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
    Text(
        text = "导出全部体重与饮食记录及设置，饮食图片不入包",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
