package com.example.weight.ui.diet

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.LocalSnackbarHostState
import com.example.weight.data.diet.DailyMacros
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.DailyMacroAggregator
import com.example.weight.data.diet.Macros
import com.example.weight.data.diet.MealType
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.ui.common.DatePickerDocked
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.common.WeightWiseDimens
import com.example.weight.ui.common.WeightWiseEmptyState
import com.example.weight.ui.theme.resolve
import com.example.weight.util.CalorieCalculator
import com.example.weight.util.IntakeStatus
import com.example.weight.util.ImageCompressor
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * 识别结果卡(T4):复用 B 行语言;顶部红绿灯 chip+总热量;宏量图例按合并后 foods 现算;
 * 保存前额度预览条;「放弃本次识别」退出路径(OV10)
 */
@Composable
internal fun AiResultSection(
    trafficLight: String,
    aiAdvice: String,
    foods: List<RecognizedFoodItem>,
    isFallback: Boolean,
    todayTotalCalories: Int,
    recommendedCalories: Int?,
    /** 补记时的人性化日期文案;null=记今天(额度预览走今日口径) */
    addDateLabel: String?,
    onEditFood: (Int) -> Unit,
    onRemoveFood: (Int) -> Unit,
    onAddFood: () -> Unit,
    onDiscard: () -> Unit,
) {
    val chipColors = lightChipColors(trafficLight)
    val totalCalories = foods.sumOf { it.estimatedCalories }
    val macros = Macros(
        protein = foods.sumOf { it.protein ?: 0 },
        carbs = foods.sumOf { it.carbs ?: 0 },
        fat = foods.sumOf { it.fat ?: 0 },
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 离线兜底信任态:徽章 + 灰化红绿灯 + 置顶核对提示(评审 F8)
            if (isFallback) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                ) {
                    Text(
                        "离线估算:AI 暂不可用,以下为默认值,请核对修改",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(chipColors.container)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(chipColors.dot, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        trafficLightLabel(trafficLight),
                        style = MaterialTheme.typography.labelMedium,
                        color = chipColors.text,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "$totalCalories kcal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (macros.protein > 0 || macros.carbs > 0 || macros.fat > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MacroLegend("蛋白质", "${macros.protein}g", DietMacroColors.Protein.resolve())
                    MacroLegend("碳水", "${macros.carbs}g", DietMacroColors.Carbs.resolve())
                    MacroLegend("脂肪", "${macros.fat}g", DietMacroColors.Fat.resolve())
                }
            }

            if (aiAdvice.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    aiAdvice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            val lightColor = trafficLightColor(trafficLight)
            foods.forEachIndexed { index, food ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "编辑${food.name}") { onEditFood(index) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 32.dp)
                                .background(lightColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                food.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val detail = buildString {
                                if (food.estimatedGrams > 0) append("${food.estimatedGrams}g · ")
                                append("${food.estimatedCalories}kcal")
                                if (food.isManuallyAdded) append(" · 手动添加")
                            }
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onRemoveFood(index) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "删除${food.name}",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onAddFood,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加食物")
            }

            // 保存前额度预览:延续编辑器「不突袭」原则,保存前看到这餐的影响。
            // 补记(addDateLabel 非空)时今日额度不适用,改为告知记录落点
            if (addDateLabel != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "这餐 $totalCalories kcal · 将记入 $addDateLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            } else if (recommendedCalories != null && recommendedCalories > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val after = recommendedCalories - todayTotalCalories - totalCalories
                Text(
                    if (after >= 0) "这餐 $totalCalories kcal · 保存后今日还可摄入 $after kcal"
                    else "这餐 $totalCalories kcal · 保存后今日将超出 ${-after} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            TextButton(
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("放弃本次识别", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
