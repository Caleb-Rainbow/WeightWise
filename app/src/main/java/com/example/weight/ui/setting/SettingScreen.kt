package com.example.weight.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.LocalStorageData
import com.example.weight.data.chat.ChatModel
import com.example.weight.ui.common.MyTopBar
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(modifier: Modifier = Modifier, goBack: () -> Unit) {
    Scaffold(modifier = modifier, topBar = {
        MyTopBar(title = "设置", goBack = goBack)
    }) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(horizontal = 15.dp)) {
            val height by LocalStorageData.height.collectAsStateWithLifecycle()
            val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
            val doubaoModelId by LocalStorageData.doubaoModelId.collectAsStateWithLifecycle()

            OutlinedTextField(value = height.toString(), onValueChange = {
                it.toDoubleOrNull()?.let { change ->
                    LocalStorageData.height.update { change }
                }
            }, label = {
                Text("身高(cm)")
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))

            OutlinedTextField(value = targetWeight.toString(), onValueChange = {
                it.toDoubleOrNull()?.let { change ->
                    LocalStorageData.targetWeight.update { change }
                }
            }, label = {
                Text("目标体重(kg)")
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))

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
        }
    }
}
