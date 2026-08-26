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

@Composable
internal fun TodayTabPage(
    state: TodayTabState,
    onEditRecord: (DietRecord) -> Unit,
    onGoAdd: () -> Unit,
    goSetting: () -> Unit,
) {
    val mealTypeByName = remember { MealType.entries.associateBy { it.name } }
    // 按餐次分组结果 remember 键住记录列表：无关重组（额度刷新等）不必重建分组；
    // LazyListScope 构建块不是 @Composable 上下文，remember 须放在 LazyColumn 之外
    val groupedByMeal = remember(state.records) { state.records.groupBy { it.mealType } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = WeightWiseDimens.PageHorizontal,
            vertical = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(WeightWiseDimens.SectionGap),
    ) {
        item(key = "today_summary") {
            IntakeHero(
                totalCalories = state.totalCalories,
                recommendedCalories = state.recommendedCalories,
                macros = state.macros,
                isEmpty = state.records.isEmpty(),
                goSetting = goSetting,
            )
        }

        if (state.records.isEmpty()) {
            item(key = "empty_today") {
                WeightWiseEmptyState(
                    icon = MealType.LUNCH.icon,
                    title = "今天还没记录",
                    message = "拍照识别，或从常用食物快速添加",
                    actionLabel = "去记一笔",
                    onAction = onGoAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 310.dp)
                        .padding(horizontal = 24.dp),
                )
            }
        } else {
            // 按餐次分组:组头=图标+餐次+首条时间+小计（分组结果见 LazyColumn 上方的 remember）
            MealType.entries.forEach { mealType ->
                val mealRecords = groupedByMeal[mealType.name] ?: return@forEach
                val subtotal = mealRecords.sumOf { it.estimatedCalories }
                val firstTime = TimeUtils.convertMillisToHM(mealRecords.minOf { it.timestamp })
                item(key = "meal_header_${mealType.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            mealType.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            mealType.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            firstTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "$subtotal kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(
                    count = mealRecords.size,
                    key = { i -> "today_${mealRecords[i].id}" },
                ) { i ->
                    val record = mealRecords[i]
                    val texts = rememberRecordTexts(record, mealLabel = null)
                    DietRecordRow(
                        record = record,
                        title = texts.first,
                        subtitle = texts.second,
                        onClick = { onEditRecord(record) },
                    )
                }
            }
            item(key = "go_add_footer") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClickLabel = "补记一笔") { onGoAdd() }
                        .padding(vertical = 12.dp)
                        .minimumInteractiveComponentSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "补记一笔",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/**
 * Hero 预算卡(T1/D3):大数字=已记录;环填充=已用比例、环色=额度状态(IntakeRingColors);
 * 满配/接近/超支/档案缺失四态;零记录日隐藏堆叠条
 */
@Composable
internal fun IntakeHero(
    totalCalories: Int,
    recommendedCalories: Int?,
    macros: DailyMacros?,
    isEmpty: Boolean,
    goSetting: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 30.dp, bottomStart = 30.dp, bottomEnd = 8.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Column(modifier = Modifier.padding(WeightWiseDimens.CardPadding)) {
            if (recommendedCalories != null && recommendedCalories > 0) {
                val status = CalorieCalculator.intakeStatus(totalCalories, recommendedCalories)
                val remaining = recommendedCalories - totalCalories
                val over = status == IntakeStatus.OVER
                val usedPercent = ((totalCalories.toDouble() / recommendedCalories) * 100).roundToInt()
                val today = remember { LocalDate.now() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RingBox(
                        usedPercent = usedPercent,
                        color = status.ringColor(),
                        over = over,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "今日饮食 / ${today.monthValue}月${today.dayOfMonth}日",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.58f),
                        )
                        Text(
                            if (over) "超出 ${-remaining}" else "剩余 $remaining",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (over) IntakeRingColors.Over.resolve()
                            else MaterialTheme.colorScheme.inversePrimary,
                            maxLines = 1,
                        )
                        Text(
                            "已摄入 $totalCalories kcal  ·  目标 $recommendedCalories kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.68f),
                            maxLines = 1,
                        )
                    }
                }

                if (!isEmpty) {
                    val weights = macros
                        ?.takeIf { it.hasMacroData }
                        ?.let { DailyMacroAggregator.macroCalorieWeights(it.protein, it.carbs, it.fat) }
                    if (macros != null && macros.hasMacroData && weights != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        MacroStackedBar(weights)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            MacroLegend("蛋白质", "${macros.protein}g", DietMacroColors.Protein.resolve(), MaterialTheme.colorScheme.inverseOnSurface)
                            MacroLegend("碳水", "${macros.carbs}g", DietMacroColors.Carbs.resolve(), MaterialTheme.colorScheme.inverseOnSurface)
                            MacroLegend("脂肪", "${macros.fat}g", DietMacroColors.Fat.resolve(), MaterialTheme.colorScheme.inverseOnSurface)
                        }
                        if (macros.skippedRecords > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${macros.skippedRecords} 条记录数据异常未计入宏量",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.62f),
                            )
                        }
                        if (macros.partialMacroData) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "部分食物无营养数据,以上为部分和",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.62f),
                            )
                        }
                    } else if (macros != null && !macros.hasMacroData) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "宏量数据 —(本日记录早于宏量统计功能,未记录该数据)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.62f),
                        )
                    }
                }
            } else {
                // 档案不全降级(D6/4A):大数字回退已摄入,环置灰隐藏,引导去设置页
                Text(
                    "今日已摄入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.62f),
                )
                Text(
                    "$totalCalories kcal",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "今天 · 补全身体档案后开启每日额度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.62f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClickLabel = "去补全档案") { goSetting() }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                        .minimumInteractiveComponentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "补全档案,开启每日额度",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/** 状态圆环:填充=已用比例,环色=额度状态;超支画满;环心中性「已用 N%」 */
@Composable
internal fun RingBox(
    usedPercent: Int,
    color: Color,
    over: Boolean,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = (usedPercent.coerceIn(0, 100)) / 100f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "intakeRing",
    )
    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Canvas 内是 DrawScope 非组合期,轨道色先在组合期解析
        val trackColor = IntakeRingColors.Track.resolve()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = if (over) 360f else animatedFraction * 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            if (over) "已用\n$usedPercent%" else "已用 $usedPercent%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun MacroStackedBar(weights: Triple<Float, Float, Float>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        // weight(0f) 会抛 IllegalArgumentException:零权重段直接不渲染
        // (单食物常见:如纯碳水食物蛋白/脂肪为 0)
        if (weights.first > 0f) {
            Box(
                modifier = Modifier
                    .weight(weights.first)
                    .fillMaxHeight()
                    .background(DietMacroColors.Protein.resolve())
            )
        }
        if (weights.second > 0f) {
            Box(
                modifier = Modifier
                    .weight(weights.second)
                    .fillMaxHeight()
                    .background(DietMacroColors.Carbs.resolve())
            )
        }
        if (weights.third > 0f) {
            Box(
                modifier = Modifier
                    .weight(weights.third)
                    .fillMaxHeight()
                    .background(DietMacroColors.Fat.resolve())
            )
        }
    }
}

@Composable
internal fun MacroLegend(
    label: String,
    value: String,
    color: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            "$label $value",
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.72f),
        )
    }
}
