package com.example.weight.ui.diet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.diet.MealType
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.util.CalorieCalculator
import org.koin.androidx.compose.koinViewModel

/**
 *@description: 主屏快速记饮食弹层（长按工具栏「饮食」触发；点击直达饮食页）：
 *               常用食物 chips 一键勾选 → 保存成餐，离线零 AI 依赖；
 *               「进入饮食记录」跳转完整饮食页走 AI 识别
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    onGoFullDiet: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: QuickAddViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackBarShow = LocalSnackBarShow.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 选中项热量微调复用食物编辑弹窗
    var editingIndex by remember { mutableStateOf(-1) }
    var showFoodEditor by remember { mutableStateOf(false) }
    if (showFoodEditor && editingIndex >= 0) {
        FoodItemEditorSheet(
            initialItem = state.selectedFoods.getOrNull(editingIndex),
            onConfirm = { item ->
                viewModel.updateSelectedFood(editingIndex, item)
                showFoodEditor = false
            },
            onDismiss = { showFoodEditor = false },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickAddEvent.Saved -> {
                    snackBarShow(
                        event.remainingCalories?.let { "已记录，今日还可摄入 $it kcal" } ?: "已记录"
                    )
                    onDismiss()
                }
                QuickAddEvent.SaveFailed -> snackBarShow("保存失败，请重试")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("快速记一笔", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                state.recommendedCalories?.let { recommended ->
                    val status = CalorieCalculator.intakeStatus(state.todayTotalCalories, recommended)
                    Text(
                        "今日 ${state.todayTotalCalories}/$recommended",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 餐次
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealType.entries.forEach { mealType ->
                    FilterChip(
                        selected = state.selectedMealType == mealType,
                        onClick = { viewModel.setMealType(mealType) },
                        label = { Text(mealType.displayName, fontSize = 13.sp, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            // 常用食物 chips（点选/取消）
            if (state.frequentFoods.isEmpty()) {
                Text(
                    "暂无常用食物：先在饮食页记录几次，高频食物会出现在这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.frequentFoods.forEach { food ->
                        val selected = state.selectedFoods.any { it.name == food.name }
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleFood(food) },
                            label = { Text("${food.name} · ${food.estimatedCalories}", fontSize = 12.sp, maxLines = 1) },
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                }
            }

            // 已选列表：可改热量、可移除
            state.selectedFoods.forEachIndexed { index, food ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${food.name} · ${food.estimatedCalories} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        editingIndex = index
                        showFoodEditor = true
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "改热量", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { viewModel.removeSelectedFood(index) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "移除",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onGoFullDiet()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("进入饮食记录", maxLines = 1)
                }
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.weight(1f),
                    enabled = state.selectedFoods.isNotEmpty() && !state.isSaving,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
