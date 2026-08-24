package com.example.weight.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weight.data.diet.MealType
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.data.diet.TrafficLightCalculator
import com.example.weight.ui.common.DatePickerDocked
import com.example.weight.util.TimeUtils

/**
 *@description: 已保存饮食记录的编辑弹窗（评审决策 #8/#23）：
 *               食物列表可增删改（含宏量）、餐次与日期可改（修复误判无法纠正）、
 *               红绿灯实时预览重算结果（让「突袭式惩罚」可见可预期）。
 *               保存走 DietRecordWriter.update（热量/红绿灯本地重算，不调 AI）
 *@author: 杨帅林
 *@create: 2026/8/21
 **/

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietRecordEditorSheet(
    foods: List<RecognizedFoodItem>,
    mealType: MealType,
    date: String,
    onMealTypeSelected: (MealType) -> Unit,
    onDateSelected: (String) -> Unit,
    onUpdateFood: (Int, RecognizedFoodItem) -> Unit,
    onRemoveFood: (Int) -> Unit,
    onAddFood: (RecognizedFoodItem) -> Unit,
    onSave: () -> Unit,
    onDeleteRecord: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var editingFoodIndex by remember { mutableStateOf(-1) }
    var editingFoodItem by remember { mutableStateOf<RecognizedFoodItem?>(null) }
    var showFoodEditor by remember { mutableStateOf(false) }
    var showEmptyDeleteConfirm by remember { mutableStateOf(false) }

    // 红绿灯实时预览：规则可见、可预期（与保存时 Writer 内的重算同源）
    val previewLight = TrafficLightCalculator.compute(foods)
    val lightColor = trafficLightColor(previewLight)
    val lightLabel = trafficLightLabel(previewLight)
    val totalCalories = foods.sumOf { it.estimatedCalories }

    if (showFoodEditor) {
        FoodItemEditorSheet(
            initialItem = editingFoodItem,
            onConfirm = { item ->
                if (editingFoodIndex >= 0) onUpdateFood(editingFoodIndex, item) else onAddFood(item)
                showFoodEditor = false
                editingFoodIndex = -1
                editingFoodItem = null
            },
            onDismiss = {
                showFoodEditor = false
                editingFoodIndex = -1
                editingFoodItem = null
            },
        )
    }

    if (showEmptyDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyDeleteConfirm = false },
            title = { Text("删除这条记录？") },
            text = { Text("食物列表已清空，无法保存空记录。可以删除整条记录（可在撤销窗口内恢复）。") },
            confirmButton = {
                TextButton(onClick = {
                    showEmptyDeleteConfirm = false
                    onDeleteRecord()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 15.dp, top = 8.dp, end = 15.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 标题 + 实时红绿灯/热量预览
            item(key = "editor_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("编辑记录", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(lightColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "$lightLabel · $totalCalories kcal",
                            style = MaterialTheme.typography.titleSmall,
                            color = lightColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // 餐次可改
            item(key = "meal_type") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MealType.entries.forEach { meal ->
                        FilterChipSmall(
                            selected = mealType == meal,
                            label = meal.displayName,
                            icon = meal.icon,
                            onClick = { onMealTypeSelected(meal) },
                        )
                    }
                }
            }

            // 日期可改
            item(key = "meal_date") {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = TimeUtils.convertDateToUtcMillis(date)
                )
                DatePickerDocked(
                    modifier = Modifier.fillMaxWidth(),
                    selectedDate = date,
                    datePickerState = datePickerState,
                    label = "用餐日期",
                    onDetermine = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateSelected(TimeUtils.convertUtcMillisToDate(millis))
                        }
                    },
                )
            }

            item(key = "food_divider") {
                HorizontalDivider()
            }

            // 食物列表
            if (foods.isEmpty()) {
                item(key = "empty_foods") {
                    Text(
                        "食物列表为空，保存将删除整条记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                item(key = "food_count") {
                    Text(
                        "食物 · ${foods.size} 项",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(foods, key = { index, food -> "food_${index}_${food.name}" }) { index, food ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(food.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            val macroText = buildString {
                                append("${food.estimatedGrams}g · ${food.estimatedCalories}kcal")
                                if (food.protein > 0 || food.carbs > 0 || food.fat > 0) {
                                    append(" · 蛋白${food.protein} 碳水${food.carbs} 脂肪${food.fat}")
                                }
                            }
                            Text(
                                macroText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row {
                            IconButton(onClick = {
                                editingFoodIndex = index
                                editingFoodItem = food
                                showFoodEditor = true
                            }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑食物", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onRemoveFood(index) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "移除食物", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item(key = "add_food") {
                OutlinedButton(
                    onClick = {
                        editingFoodIndex = -1
                        editingFoodItem = null
                        showFoodEditor = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加食物")
                }
            }

            item(key = "record_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { showEmptyDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("删除记录", color = MaterialTheme.colorScheme.error) }

                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(2f),
                        enabled = foods.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("保存修改", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun FilterChipSmall(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, maxLines = 1) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        },
        modifier = Modifier.minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(10.dp),
    )
}
