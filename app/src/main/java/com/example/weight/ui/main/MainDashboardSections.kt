package com.example.weight.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.DailyStatMode
import com.example.weight.data.record.DailyWeight
import com.example.weight.data.record.Record
import com.example.weight.ui.common.WeightChart
import com.example.weight.ui.common.SectionHeader
import com.example.weight.ui.common.StatusPill
import com.example.weight.ui.common.WeightWiseDimens
import com.example.weight.ui.common.movingAverage
import com.example.weight.ui.diet.QuickAddSheet
import com.example.weight.util.GoalProgressCalculator
import com.example.weight.util.GoalPlanCalculator
import com.example.weight.util.StreakInfo
import com.example.weight.util.TimeUtils
import com.example.weight.util.WeightPredictor
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.common.vicoTheme
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.absoluteValue

internal const val HERO_RING_TEST_TAG = "hero_ring"
internal const val TREND_CHART_TEST_TAG = "trend_chart"


/** 首次进入、数据库尚未发出第一份数据时的短暂加载态 */
@Composable
internal fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

/** 无任何记录时的引导空状态：文案 + 主动作，而不是满屏 0.0 */
@Composable
internal fun EmptyContent(onAddRecord: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MonitorWeight,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "开始记录体重吧", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "每天称一称，看见变化的发生",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddRecord, modifier = Modifier.height(48.dp)) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "记第一笔体重")
        }
    }
}

/** 手机优先展示体重、总目标和趋势；宽屏并排利用横向空间。 */
@Composable
internal fun DashboardLayout(
    viewportHeight: Dp,
    heroContent: @Composable (Modifier) -> Unit,
    trendContent: @Composable (Dp) -> Unit,
    statsContent: @Composable () -> Unit,
    bmiContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 使用滚动容器外的可用高度，短屏缩小图表，同时保留坐标和触控空间。
    val chartHeight = (viewportHeight * 0.30f).coerceIn(180.dp, 220.dp)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= 700.dp) {
            Row(
                modifier = Modifier.padding(horizontal = WeightWiseDimens.PageHorizontal),
                horizontalArrangement = Arrangement.spacedBy(WeightWiseDimens.SectionGap),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(0.42f)) {
                    heroContent(Modifier.clip(MaterialTheme.shapes.extraLarge))
                    statsContent()
                }
                Column(modifier = Modifier.weight(0.58f)) {
                    trendContent(chartHeight)
                    bmiContent()
                }
            }
        } else {
            Column {
                heroContent(Modifier)
                Column(modifier = Modifier.padding(horizontal = WeightWiseDimens.PageHorizontal)) {
                    trendContent(chartHeight)
                    statsContent()
                    bmiContent()
                }
            }
        }
    }
}

/** 杂志式章节头：编号负责节奏，大标题负责扫描。 */
@Composable
internal fun DashboardSectionTitle(
    index: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = index,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

@Composable
internal fun GoalProgressContent(
    modifier: Modifier,
    currentRecord: Record?,
    firstRecord: Record?,
    recentDailyWeights: List<DailyWeight>
) {
    currentRecord?.let {
        val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
        val configuredStartWeight by LocalStorageData.startWeight.collectAsStateWithLifecycle()
        val weeklyTargetChangeKg by LocalStorageData.weeklyTargetChangeKg.collectAsStateWithLifecycle()
        val currentWaistCm by LocalStorageData.currentWaistCm.collectAsStateWithLifecycle()
        val targetWaistCm by LocalStorageData.targetWaistCm.collectAsStateWithLifecycle()
        val targetBodyFatPercent by LocalStorageData.targetBodyFatPercent.collectAsStateWithLifecycle()
        // 手动设置的起始体重优先，未设置时跟随第一条记录；设置页修改后此处实时重组
        val startWeight =
            GoalProgressCalculator.effectiveStartWeight(configuredStartWeight, firstRecord?.weight)
                ?: 0.0
        val currentWeight = it.weight
        if (targetWeight > 0) {
            val losing = targetWeight < startWeight
            // 维持体重（起始=目标）：贴住目标（±0.05 显示精度）才算达成，与 progress 的 0/1 口径一致；
            // 否则会同屏出现"目标进度 100%"+"还差 X kg"的矛盾
            val maintaining = (targetWeight - startWeight).absoluteValue < 1e-9
            val goalReached = when {
                maintaining -> (currentWeight - targetWeight).absoluteValue <= 0.05
                losing -> currentWeight <= targetWeight
                else -> currentWeight >= targetWeight
            }

            // 核心逻辑：计算从起始到目标的进度百分比（算法与桌面小组件共用）
            val progress = remember(startWeight, currentWeight, targetWeight) {
                GoalProgressCalculator.progress(startWeight, currentWeight, targetWeight)
            }
            // 使用 animateFloatAsState 为进度条增加平滑的动画效果
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                label = "目标进度",
            )
            // 基于近 90 天每日代表值的加权回归趋势估算剩余天数，
            // 趋势停滞、反向或数据不足时返回 null，不显示天数
            val remainingDays = remember(recentDailyWeights, currentWeight, targetWeight) {
                WeightPredictor.estimateDaysToTarget(recentDailyWeights, currentWeight, targetWeight)
            }
            val plannedDays = remember(currentWeight, targetWeight, weeklyTargetChangeKg) {
                GoalPlanCalculator.plannedDays(currentWeight, targetWeight, weeklyTargetChangeKg)
            }
            GoalProgressSummary(
                modifier = modifier,
                startWeight = startWeight,
                currentWeight = currentWeight,
                targetWeight = targetWeight,
                progress = animatedProgress,
                goalReached = goalReached,
                plannedDays = plannedDays,
                remainingDays = remainingDays,
                currentWaistCm = currentWaistCm,
                targetWaistCm = targetWaistCm,
                currentBodyFatPercent = it.fatRatio,
                targetBodyFatPercent = targetBodyFatPercent,
            )
        }
    }
}

/** 总目标展示与存储分离，便于验证小屏布局和不同目标方向的文案。 */
@Composable
internal fun GoalProgressSummary(
    startWeight: Double,
    currentWeight: Double,
    targetWeight: Double,
    progress: Float,
    goalReached: Boolean,
    plannedDays: Long?,
    remainingDays: Long?,
    modifier: Modifier = Modifier,
    currentWaistCm: Double = 0.0,
    targetWaistCm: Double = 0.0,
    currentBodyFatPercent: Double = 0.0,
    targetBodyFatPercent: Double = 0.0,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 30.dp, bottomStart = 30.dp, bottomEnd = 10.dp),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.09f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoalRing(
                progress = progress,
                modifier = Modifier.size(60.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (goalReached) "总目标已达成" else "总目标进度",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f),
                    )
                    val remainingWeight = (currentWeight - targetWeight).absoluteValue
                    Text(
                        text = if (goalReached) {
                            val changedWeight = (currentWeight - startWeight).absoluteValue
                            when {
                                changedWeight < 0.05 -> "稳住现在的节奏"
                                // 方向按实际增减判定：维持目标下减轻也说"已减轻"，不能跟 losing 走
                                currentWeight < startWeight -> "已减轻 ${String.format(Locale.CHINA, "%.1f", changedWeight)} kg"
                                else -> "已增加 ${String.format(Locale.CHINA, "%.1f", changedWeight)} kg"
                            }
                        } else {
                            "还差 ${String.format(Locale.CHINA, "%.1f", remainingWeight)} kg"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("起点 ${String.format(Locale.CHINA, "%.1f", startWeight)}  ·  目标 ${String.format(Locale.CHINA, "%.1f", targetWeight)}")
                        if (!goalReached && (plannedDays != null || remainingDays != null)) {
                            append("\n")
                            if (plannedDays != null) append("计划 $plannedDays 天")
                            if (plannedDays != null && remainingDays != null) append("  ·  ")
                            if (remainingDays != null) append("趋势 $remainingDays 天")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f),
                )
                val bodyGoals = buildList {
                    if (targetWaistCm > 0) {
                        add(
                            if (currentWaistCm > 0) {
                                "腰围 ${String.format(Locale.CHINA, "%.1f", currentWaistCm)} → ${String.format(Locale.CHINA, "%.1f", targetWaistCm)} cm"
                            } else "腰围目标 ${String.format(Locale.CHINA, "%.1f", targetWaistCm)} cm"
                        )
                    }
                    if (targetBodyFatPercent > 0) {
                        add(
                            if (currentBodyFatPercent > 0) {
                                "体脂 ${String.format(Locale.CHINA, "%.1f", currentBodyFatPercent)} → ${String.format(Locale.CHINA, "%.1f", targetBodyFatPercent)}%"
                            } else "体脂目标 ${String.format(Locale.CHINA, "%.1f", targetBodyFatPercent)}%"
                        )
                    }
                }
                if (bodyGoals.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = bodyGoals.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun GoalRing(progress: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
    val indicator = MaterialTheme.colorScheme.inversePrimary
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = indicator,
                startAngle = 135f,
                sweepAngle = 270f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke,
            )
        }
        Text(
            text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
