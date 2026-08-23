package com.example.weight.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.weight.data.diet.RecognizedFoodItem

/**
 *@description: 已选食物共享区(Q2A):汇总条「已选 n 项 · 共 X kcal」+ 可改可删条目行。
 *               添加页快速路径(T3)与 QuickAddSheet(T5)共用,避免同构 UI 两处漂移
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
@Composable
fun SelectedFoodsSection(
    foods: List<RecognizedFoodItem>,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val total = foods.sumOf { it.estimatedCalories }
        // “已选中”不代表食物质量，使用中性容器，避免与健康红绿灯混淆。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    RoundedCornerShape(10.dp),
                )
                .padding(start = 12.dp, end = 4.dp)
                .minimumInteractiveComponentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "已选 ${foods.size} 项 · 共 $total kcal",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (onClear != null) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        foods.forEachIndexed { index, food ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        food.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val detail = buildString {
                        if (food.estimatedGrams > 0) append("${food.estimatedGrams}g · ")
                        append("${food.estimatedCalories} kcal")
                    }
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEdit(index) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑${food.name}",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "移除${food.name}",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
