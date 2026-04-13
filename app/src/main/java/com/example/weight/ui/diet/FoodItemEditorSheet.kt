package com.example.weight.ui.diet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.weight.data.diet.RecognizedFoodItem

/**
 *@description: 食物项编辑弹窗
 *@author: 杨帅林
 *@create: 2026/4/11
 **/

private val FOOD_CATEGORIES = listOf("主食", "蔬菜", "肉类", "蛋奶", "饮品", "水果", "零食", "调味品", "其他")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodItemEditorSheet(
    initialItem: RecognizedFoodItem? = null,
    onConfirm: (RecognizedFoodItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var calories by remember { mutableStateOf(initialItem?.estimatedCalories?.toString() ?: "") }
    var grams by remember { mutableStateOf(initialItem?.estimatedGrams?.toString() ?: "") }
    var category by remember { mutableStateOf(initialItem?.category ?: "") }
    var isHealthy by remember { mutableStateOf(initialItem?.isHealthy ?: true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (initialItem == null) "添加食物" else "编辑食物",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("食物名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it.filter { c -> c.isDigit() } },
                    label = { Text("热量 (kcal)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it.filter { c -> c.isDigit() } },
                    label = { Text("克数 (g)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // 分类选择
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FOOD_CATEGORIES.forEach { cat ->
                    val selected = category == cat
                    OutlinedButton(
                        onClick = { category = cat },
                        shape = RoundedCornerShape(8.dp),
                        colors = if (selected) {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text(cat, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 健康标签切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("健康程度:", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = { isHealthy = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = if (isHealthy) {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                        )
                    } else {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                    },
                ) { Text("健康") }
                OutlinedButton(
                    onClick = { isHealthy = false },
                    shape = RoundedCornerShape(8.dp),
                    colors = if (!isHealthy) {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                        )
                    } else {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                    },
                ) { Text("不健康") }
            }

            // 确认按钮
            androidx.compose.material3.Button(
                onClick = {
                    val item = RecognizedFoodItem(
                        name = name.ifBlank { "未知食物" },
                        estimatedCalories = calories.toIntOrNull() ?: 0,
                        estimatedGrams = grams.toIntOrNull() ?: 0,
                        category = category,
                        isHealthy = isHealthy,
                    )
                    onConfirm(item)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = name.isNotBlank() && calories.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (initialItem == null) "添加" else "确认修改")
            }
        }
    }
}
