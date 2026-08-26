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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.example.weight.ui.diet.QuickAddSheet
import com.example.weight.util.GoalProgressCalculator
import com.example.weight.util.StreakInfo
import com.example.weight.util.TimeUtils
import com.example.weight.util.WeightPredictor
import com.example.weight.util.FluctuationDirection
import com.example.weight.util.TrendConfidence
import com.example.weight.util.WeightTrendAnalyzer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.common.vicoTheme
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.absoluteValue

internal const val SCOPE_MENU_ANCHOR_TEST_TAG = "scope_menu_anchor"


/** 轻量化的统计范围选择器：当前范围文字 + 下拉箭头，不与左侧体重数抢视觉 */
@Composable
internal fun ScopeSelector(
    selected: StatisticsScope,
    onSelected: (StatisticsScope) -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(contentColor.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClickLabel = "选择统计范围") { expanded = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "观察周期",
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.62f),
        )
        Spacer(Modifier.weight(1f))
        // 菜单只以右侧当前值为锚点；外层整行仍保持 48dp 以上的点击区域。
        Box(modifier = Modifier.testTag(SCOPE_MENU_ANCHOR_TEST_TAG)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = contentColor,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                StatisticsScope.entries.forEach { scope ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = scope.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (scope == selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelected(scope)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun StatisticChart(
    currentScopeDataList: List<DailyWeight>,
    selectedScope: StatisticsScope,
    maxWeight: Double,
    minWeight: Double,
    onMarkerClick: (DailyWeight) -> Unit,
) {
    // 根据收集到的数据构建 LineChart 所需的参数，当 currentScopeDataList 变化时重组
    val labels = remember(currentScopeDataList) { currentScopeDataList.map { it.recordDay } }
    val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
    val insight = remember(currentScopeDataList, selectedScope, targetWeight) {
        WeightTrendAnalyzer.analyze(
            dailyWeights = currentScopeDataList,
            totalDays = selectedScope.totalDays,
            targetWeight = targetWeight,
        )
    }

    // 当 chartData 不为空时才显示图表
    if (currentScopeDataList.isNotEmpty()) {
        // producer 保持稳定，数据变化只增量提交事务；不销毁整棵图表子树（含滚动状态）
        val modelProducer = remember { CartesianChartModelProducer() }
        LaunchedEffect(currentScopeDataList, insight) {
            modelProducer.runTransaction {
                lineModel {
                    series(currentScopeDataList.map { it.value })
                    if (insight.smoothedTrend.isNotEmpty()) {
                        series(
                            x = insight.smoothedTrend.map { it.index },
                            y = insight.smoothedTrend.map { it.value },
                        )
                    }
                    if (insight.sevenDayAverage.isNotEmpty()) {
                        series(
                            x = insight.sevenDayAverage.map { it.index },
                            y = insight.sevenDayAverage.map { it.value },
                        )
                    }
                }
            }
        }
        // 稳定的索引回调：内联 lambda 每次重组生成新实例，会顺着 WeightChart 的
        // remember 链触发 marker listener 重建乃至整张图表重建
        val onMarkerIndexClick = remember(onMarkerClick, currentScopeDataList) {
            { index: Int -> onMarkerClick(currentScopeDataList[index]) }
        }
        Column {
            WeightChart(
                lineColor = vicoTheme.lineColor,
                modelProducer = modelProducer,
                maxWeight = maxWeight,
                minWeight = minWeight,
                xLabels = labels,
                showMovingAverage = insight.sevenDayAverage.isNotEmpty(),
                showSmoothedTrend = insight.smoothedTrend.isNotEmpty(),
                targetWeight = targetWeight,
                onMarkerClick = onMarkerIndexClick,
            )
            TrendInsightSummary(insight = insight)
        }
    }
}

@Composable
private fun TrendInsightSummary(insight: com.example.weight.util.WeightTrendInsight) {
    val legend = if (insight.sevenDayAverage.isNotEmpty()) {
        "实线为平滑趋势，辅助线为7日均值"
    } else {
        "实线为平滑趋势；覆盖完整7日后显示均值线"
    }
    val headline = when {
        insight.confidence == TrendConfidence.LOW -> "继续记录后再判断长期趋势"
        insight.isPlateau -> "近两周趋势接近平稳，可能进入平台期"
        insight.fluctuation != null -> {
            val position = if (insight.fluctuation.direction == FluctuationDirection.ABOVE_TREND) "高于" else "低于"
            "最新值${position}趋势 ${String.format(Locale.CHINA, "%.1f", kotlin.math.abs(insight.fluctuation.deltaKg))} kg，更像短期波动"
        }
        insight.weeklyRateKg != null -> {
            val verb = if (insight.weeklyRateKg < 0) "下降" else if (insight.weeklyRateKg > 0) "上升" else "变化"
            "平滑趋势每周$verb ${String.format(Locale.CHINA, "%.2f", kotlin.math.abs(insight.weeklyRateKg))} kg"
        }
        else -> "趋势暂时平稳"
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(headline, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                "${insight.confidence.label} · ${insight.confidenceReason} · $legend",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
            )
        }
    }
}

/** 周期摘要采用深色横向刊头，不再是三个等权小指标卡。 */
@Composable
internal fun PeriodDigest(
    modifier: Modifier,
    maxWeightRecord: DailyWeight?,
    minWeightRecord: DailyWeight?,
    firstWeightRecord: DailyWeight?,
    lastWeightRecord: DailyWeight?
) {
    val delta = lastWeightRecord?.value?.minus(firstWeightRecord?.value ?: 0.0) ?: 0.0
    val deltaFormat = remember { DecimalFormat("+#.#;-#.#") }
    // 口径披露：摘要极值与变化按每日口径折算，与图表同源（见设置-统计）
    val statMode = DailyStatMode.fromId(LocalStorageData.dailyStatMode.collectAsStateWithLifecycle().value)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1.25f)) {
                Text(
                    "周期变化",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.58f),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        deltaFormat.format(delta),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (delta <= 0) MaterialTheme.colorScheme.inversePrimary
                        else MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        "kg",
                        modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                    )
                }
                Text(
                    buildString {
                        firstWeightRecord?.timestamp?.let { append(TimeUtils.convertMillisToDate(it).takeLast(5)) }
                        append(" → ")
                        lastWeightRecord?.timestamp?.let { append(TimeUtils.convertMillisToDate(it).takeLast(5)) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.52f),
                )
                Text(
                    "按每日${statMode.label}统计",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.42f),
                )
            }
            VerticalDivider(
                Modifier
                    .height(76.dp)
                    .padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.14f),
            )
            Column(modifier = Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DigestExtrema("最高", maxWeightRecord)
                DigestExtrema("最低", minWeightRecord)
            }
        }
    }
}

@Composable
internal fun DigestExtrema(
    label: String,
    record: DailyWeight?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = String.format(Locale.CHINA, "%.1f", record?.value ?: 0.0),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

enum class StatisticsScope(val label: String, val totalDays: Int) {
    LAST_7DAYS("近7天", 7),
    LAST_14DAYS("近14天", 14),
    LAST_1MONTH("近1月", 31),
    LAST_3MONTHS("近3月", 92),
    LAST_6MONTHS("近6月", 183),
    LAST_1YEARS("近1年", 365),
    LAST_2YEARS("近2年", 730),
    LAST_3YEARS("近3年", 1095);

    /**
     * 该统计范围的起始时间戳（范围第一天的午夜）。
     * 图表取数与 AI 分析取数共用这一映射，保证分析的数据范围与界面所选范围一致
     */
    fun startTimeMillis(): Long = when (this) {
        LAST_7DAYS -> TimeUtils.getStartTimeForLastDays(7)
        LAST_14DAYS -> TimeUtils.getStartTimeForLastDays(14)
        LAST_1MONTH -> TimeUtils.getStartTimeForLastMonths(1)
        LAST_3MONTHS -> TimeUtils.getStartTimeForLastMonths(3)
        LAST_6MONTHS -> TimeUtils.getStartTimeForLastMonths(6)
        LAST_1YEARS -> TimeUtils.getStartTimeForLastMonths(12)
        LAST_2YEARS -> TimeUtils.getStartTimeForLastMonths(24)
        LAST_3YEARS -> TimeUtils.getStartTimeForLastMonths(36)
    }
}
