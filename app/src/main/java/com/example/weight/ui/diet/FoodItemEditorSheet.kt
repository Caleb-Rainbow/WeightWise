package com.example.weight.ui.diet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.weight.data.diet.FoodQuality
import com.example.weight.data.diet.RecognizedFoodItem

/**
 *@description: 食物项编辑弹窗。宏量三输入（评审决策 #23）：编辑后逐食物宏量随热量一起修正，
 *               否则日宏量聚合会系统性保留陈旧值
 *@author: 杨帅林
 *@create: 2026/4/11
 **/

private val FOOD_CATEGORIES = listOf("主食", "蔬菜", "肉类", "蛋奶", "饮品", "水果", "零食", "调味品", "其他")

private fun qualityLabel(quality: FoodQuality): String = when (quality) {
    FoodQuality.OFTEN -> "适合经常吃"
    FoodQuality.SOMETIMES -> "偶尔吃"
    FoodQuality.INDULGENT -> "放纵一下"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodItemEditorSheet(
    initialItem: RecognizedFoodItem? = null,
    onConfirm: (RecognizedFoodItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    // 以 initialItem 为 remember key：复用组合实例时也能正确显示当前编辑项而非陈旧数据
    var name by remember(initialItem) { mutableStateOf(initialItem?.name ?: "") }
    var calories by remember(initialItem) { mutableStateOf(initialItem?.estimatedCalories?.toString() ?: "") }
    var grams by remember(initialItem) { mutableStateOf(initialItem?.estimatedGrams?.toString() ?: "") }
    var category by remember(initialItem) { mutableStateOf(initialItem?.category ?: "") }
    var quality by remember(initialItem) { mutableStateOf(initialItem?.effectiveQuality ?: FoodQuality.OFTEN) }
    // null（无数据）与 0（真 0）都原样显示：留空保存=无数据，填 0=真 0 宏量
    var protein by remember(initialItem) { mutableStateOf(initialItem?.protein?.toString() ?: "") }
    var carbs by remember(initialItem) { mutableStateOf(initialItem?.carbs?.toString() ?: "") }
    var fat by remember(initialItem) { mutableStateOf(initialItem?.fat?.toString() ?: "") }
    var showAdvanced by remember(initialItem) {
        mutableStateOf(
            initialItem?.let {
                it.category.isNotBlank() || it.protein != null || it.carbs != null || it.fat != null
            } ?: false
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            contentPadding = PaddingValues(start = 15.dp, top = 8.dp, end = 15.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (initialItem == null) "添加食物" else "编辑食物",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "先填写名称和热量，其他信息可选",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "food_name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("食物名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            item(key = "amount") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it.filter { c -> c.isDigit() } },
                        label = { Text("热量") },
                        suffix = { Text("kcal") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = grams,
                        onValueChange = { grams = it.filter { c -> c.isDigit() } },
                        label = { Text("份量") },
                        suffix = { Text("g") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            item(key = "frequency") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "饮食频率",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FoodQuality.entries.forEach { q ->
                            FilterChip(
                                selected = quality == q,
                                onClick = { quality = q },
                                label = { Text(qualityLabel(q)) },
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                    Text(
                        "用于计算整餐的饮食质量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "advanced_toggle") {
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (showAdvanced) "收起更多信息" else "更多信息：分类和营养素")
                }
            }

            if (showAdvanced) {
                item(key = "category") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "食物分类（可选）",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FOOD_CATEGORIES.forEach { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = cat },
                                    label = { Text(cat) },
                                    shape = RoundedCornerShape(10.dp),
                                )
                            }
                        }
                    }
                }

                // 宏量营养素（可选填，留空保存为无数据，不计入日聚合）
                item(key = "macros") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "营养素（可选）",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                Triple("蛋白质", protein, { value: String -> protein = value }),
                                Triple("碳水", carbs, { value: String -> carbs = value }),
                                Triple("脂肪", fat, { value: String -> fat = value }),
                            ).forEach { (label, value, update) ->
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { update(it.filter(Char::isDigit)) },
                                    label = { Text(label) },
                                    suffix = { Text("g") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 确认按钮
            item(key = "confirm") {
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = {
                        val item = RecognizedFoodItem(
                            name = name.ifBlank { "未知食物" },
                            estimatedCalories = calories.toIntOrNull() ?: 0,
                            estimatedGrams = grams.toIntOrNull() ?: 0,
                            category = category,
                            quality = quality,
                            protein = protein.toIntOrNull(),
                            carbs = carbs.toIntOrNull(),
                            fat = fat.toIntOrNull(),
                            isManuallyAdded = initialItem?.isManuallyAdded ?: true,
                        )
                        onConfirm(item)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank() && calories.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (initialItem == null) "添加食物" else "保存修改")
                }
            }
        }
    }
}
