package com.example.weight.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalShowMessageDialog
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.DailyStatMode
import com.example.weight.data.record.DailyWeight
import com.example.weight.data.health.HealthActivitySummary
import com.example.weight.ui.common.AnalysisBottomSheet
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.common.SectionHeader
import com.example.weight.ui.common.WeightChart
import com.example.weight.ui.common.WeightWiseDimens
import com.example.weight.ui.common.WeightWiseEmptyState
import com.example.weight.ui.diet.TrafficLightColors
import com.example.weight.ui.theme.resolve
import com.example.weight.util.ReportCaloriesStats
import com.example.weight.util.ReportType
import com.example.weight.util.ReportWeightStats
import com.example.weight.util.FluctuationDirection
import com.example.weight.util.TrendConfidence
import com.example.weight.util.WeightTrendInsight
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.common.vicoTheme
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportViewModel = koinViewModel(),
    goBack: () -> Unit = {},
) {
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val anchor by viewModel.anchor.collectAsStateWithLifecycle()
    // null 表示类型/周期切换的加载瞬间，用上一次数据兜底，避免内容闪烁
    val reportNullable by viewModel.reportFlow.collectAsStateWithLifecycle(initialValue = null)
    var cachedReport by remember { mutableStateOf<ReportData?>(null) }
    val report = reportNullable ?: cachedReport
    LaunchedEffect(reportNullable) {
        reportNullable?.let { cachedReport = it }
    }

    // 顶层一次性收集：放在条件分支里会随翻页加载态销毁重建订阅
    val recommendedIntake by viewModel.recommendedIntake.collectAsStateWithLifecycle()
    val showMessageDialog = LocalShowMessageDialog.current
    val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()

    // AI 弹窗独立成组：流式期间每帧只重组这个小组件，而不是整个报告页
    ReportAiSheet(viewModel)

    Scaffold(modifier = modifier, topBar = { MyTopBar(title = "健康报告", goBack = goBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            PeriodTypeTabs(selectedType, viewModel::selectType)
            PeriodNavigator(
                title = report?.takeIf { it.type == selectedType && it.anchor == anchor }?.title
                    ?: selectedType.titleOf(anchor),
                canGoNext = selectedType.canGoNext(anchor, LocalDate.now()),
                onPrevious = viewModel::previousPeriod,
                onNext = viewModel::nextPeriod,
            )
            when {
                report == null -> ReportLoadingContent()
                report.dailyWeights.isEmpty() || report.weightStats == null -> ReportEmptyContent()
                else -> Column(modifier = Modifier.padding(horizontal = WeightWiseDimens.PageHorizontal)) {
                    SummaryCard(
                        modifier = Modifier.fillMaxWidth(),
                        weightStats = report.weightStats,
                        changeVsPrevPeriod = report.changeVsPrevPeriod,
                        endBmi = report.endBmi,
                    )
                    CheckInCard(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                        recordedDays = report.weightStats.recordedDays,
                        totalDays = report.totalDays,
                    )
                    SectionHeader(
                        title = "趋势回顾",
                        subtitle = "目标线与周期内每日体重代表值",
                        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
                    )
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        ReportChart(
                            dailyWeights = report.dailyWeights,
                            targetWeight = targetWeight,
                            insight = report.trendInsight,
                        )
                    }
                    SectionHeader(
                        title = "区间数据",
                        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
                    )
                    WeightStatsCard(
                        modifier = Modifier.fillMaxWidth(),
                        weightStats = report.weightStats,
                    )
                    report.caloriesStats?.let {
                        CaloriesCard(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            caloriesStats = it,
                            recommendedIntake = recommendedIntake,
                        )
                    }
                    report.healthSummary?.let {
                        HealthActivityCard(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            summary = it,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            viewModel.aiSummarize { showMessageDialog("提示", it) {} }
                        },
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 深度总结")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** AI 总结弹窗：单独收集 aiState，把流式期间的高频重组范围限制在弹窗内 */
@Composable
private fun ReportAiSheet(viewModel: ReportViewModel) {
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val showMessageDialog = LocalShowMessageDialog.current
    AnalysisBottomSheet(
        showSheet = aiState.isShowSheet,
        onDismissRequest = viewModel::hideAiSheet,
        analysisResult = aiState.response,
        isLoading = aiState.isLoading,
        isStreaming = aiState.isStreaming,
        errorMessage = aiState.error,
        onRetry = { viewModel.aiSummarize { showMessageDialog("提示", it) {} } }
    )
}

/** 周报 / 月报 / 年报 类型切换；切换后回到该类型的当前周期 */
@Composable
private fun PeriodTypeTabs(
    selected: ReportType,
    onSelected: (ReportType) -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = WeightWiseDimens.PageHorizontal, vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            ReportType.entries.forEach { type ->
                val isSelected = type == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClickLabel = type.label) { onSelected(type) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.inverseSurface else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.inverseOnSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = type.label,
                        modifier = Modifier.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 周期翻页行：◀ 标题 ▶，未来的周期不可进入 */
@Composable
private fun PeriodNavigator(
    title: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周期")
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周期")
        }
    }
}

/** 首次进入或切换周期、数据库尚未发出第一份数据时的加载态 */
@Composable
private fun ReportLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

/** 该周期内没有任何体重记录 */
@Composable
private fun ReportEmptyContent() {
    WeightWiseEmptyState(
        icon = Icons.Default.MonitorWeight,
        title = "这个周期还没有记录",
        message = "换个周期看看，或从今天开始记录体重",
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .padding(horizontal = 32.dp),
    )
}

/** 核心摘要卡：净变化（大数字）+ 期初→期末 + 较上一期 + 期末 BMI */
@Composable
private fun SummaryCard(
    modifier: Modifier,
    weightStats: ReportWeightStats,
    changeVsPrevPeriod: Double?,
    endBmi: Double?,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 32.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "体重净变化",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.58f)
            )
            Text(
                text = String.format(Locale.CHINA, "%+.1fkg", weightStats.netChange),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (weightStats.netChange <= 0) MaterialTheme.colorScheme.inversePrimary
                else MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = String.format(Locale.CHINA, "%.1fkg → %.1fkg（期初 → 期末）", weightStats.startWeight, weightStats.endWeight),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.68f)
            )
            if (changeVsPrevPeriod != null || endBmi != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (changeVsPrevPeriod != null) {
                        Text(
                            text = String.format(Locale.CHINA, "较上一期 %+.1fkg", changeVsPrevPeriod),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (changeVsPrevPeriod <= 0) MaterialTheme.colorScheme.inversePrimary
                            else MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (endBmi != null) {
                        Text(
                            text = "期末 BMI ${String.format(Locale.CHINA, "%.1f", endBmi)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.68f)
                        )
                    }
                }
            }
        }
    }
}

/** 打卡卡：打卡 X/Y 天 + 打卡率进度条 */
@Composable
private fun CheckInCard(
    modifier: Modifier,
    recordedDays: Int,
    totalDays: Int,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "记录打卡",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$recordedDays/$totalDays 天",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (totalDays > 0) recordedDays.toFloat() / totalDays else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** 周期内体重趋势图：与首页同款（主线 + 7 日均线 + 目标虚线） */
@Composable
private fun ReportChart(
    dailyWeights: List<DailyWeight>,
    targetWeight: Double,
    insight: WeightTrendInsight,
) {
    val labels = remember(dailyWeights) { dailyWeights.map { it.recordDay } }
    // Y 轴范围并入目标体重，保证目标参考虚线始终可见（与首页同规则）
    val chartMaxWeight = remember(dailyWeights, targetWeight) {
        val raw = dailyWeights.maxOfOrNull { it.value } ?: 0.0
        (if (targetWeight > 0) maxOf(raw, targetWeight) else raw).plus(1)
    }
    val chartMinWeight = remember(dailyWeights, targetWeight) {
        val raw = dailyWeights.minOfOrNull { it.value } ?: 0.0
        (if (targetWeight > 0) minOf(raw, targetWeight) else raw).minus(1)
    }
    // producer 提到数据键之外保持稳定，数据变化只 runTransaction 增量提交，不重建图表
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(dailyWeights, insight) {
        modelProducer.runTransaction {
            lineModel {
                series(dailyWeights.map { it.value })
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
    Column {
        WeightChart(
            lineColor = vicoTheme.lineColor,
            modelProducer = modelProducer,
            maxWeight = chartMaxWeight,
            minWeight = chartMinWeight,
            xLabels = labels,
            showMovingAverage = insight.sevenDayAverage.isNotEmpty(),
            showSmoothedTrend = insight.smoothedTrend.isNotEmpty(),
            targetWeight = targetWeight,
        )
        ReportTrendInsight(insight)
    }
}

@Composable
private fun ReportTrendInsight(insight: WeightTrendInsight) {
    val message = when {
        insight.confidence == TrendConfidence.LOW -> "数据较少，本期仅展示记录事实，不做趋势定论"
        insight.isPlateau -> "近两周变化很小，出现平台期信号"
        insight.fluctuation != null -> {
            val position = if (insight.fluctuation.direction == FluctuationDirection.ABOVE_TREND) "高于" else "低于"
            "最新体重${position}平滑趋势 ${String.format(Locale.CHINA, "%.1f", kotlin.math.abs(insight.fluctuation.deltaKg))} kg，优先视作短期波动"
        }
        insight.weeklyRateKg != null ->
            "平滑趋势 ${insight.direction.label} · ${String.format(Locale.CHINA, "%+.2f", insight.weeklyRateKg)} kg/周"
        else -> "暂时没有足够跨度计算每周趋势"
    }
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Text(message, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            "${insight.confidence.label} · ${insight.confidenceReason}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 统计卡：最高 / 最低 / 平均 三列，底部披露当前每日口径 */
@Composable
private fun WeightStatsCard(
    modifier: Modifier,
    weightStats: ReportWeightStats,
) {
    val statMode = DailyStatMode.fromId(LocalStorageData.dailyStatMode.collectAsStateWithLifecycle().value)
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
            ReportStatItem(
                modifier = Modifier.weight(1f),
                value = String.format(Locale.CHINA, "%.1f", weightStats.maxWeight),
                label = "最高",
            )
            VerticalDivider(Modifier.height(40.dp))
            ReportStatItem(
                modifier = Modifier.weight(1f),
                value = String.format(Locale.CHINA, "%.1f", weightStats.minWeight),
                label = "最低",
            )
            VerticalDivider(Modifier.height(40.dp))
            ReportStatItem(
                modifier = Modifier.weight(1f),
                value = String.format(Locale.CHINA, "%.1f", weightStats.avgWeight),
                label = "平均",
            )
            }
            Text(
                text = "按每日${statMode.label}统计",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ReportStatItem(
    modifier: Modifier,
    value: String,
    label: String,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 热量卡：日均摄入 vs 建议摄入、超标天数、红绿灯分布 */
@Composable
private fun CaloriesCard(
    modifier: Modifier,
    caloriesStats: ReportCaloriesStats,
    recommendedIntake: Int?,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "热量摄入（${caloriesStats.recordedDays} 天有记录）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "日均 ${caloriesStats.avgCalories} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (caloriesStats.hasTarget) {
                    Text(
                        text = "超标 ${caloriesStats.daysOverTarget} 天（建议 ${recommendedIntake ?: 0} kcal/日）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (caloriesStats.daysOverTarget > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val total = caloriesStats.greenCount + caloriesStats.yellowCount + caloriesStats.redCount
            if (total > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                ) {
                    listOf(
                        TrafficLightColors.Green.resolve() to caloriesStats.greenCount,
                        TrafficLightColors.Amber.resolve() to caloriesStats.yellowCount,
                        TrafficLightColors.Red.resolve() to caloriesStats.redCount,
                    ).forEach { (color, count) ->
                        if (count > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(count.toFloat() / total)
                                    .fillMaxSize()
                                    .padding(end = 1.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(color)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TrafficLightLegend(TrafficLightColors.Green.resolve(), "健康饮食", caloriesStats.greenCount)
                    TrafficLightLegend(TrafficLightColors.Amber.resolve(), "尚可", caloriesStats.yellowCount)
                    TrafficLightLegend(TrafficLightColors.Red.resolve(), "放纵一下", caloriesStats.redCount)
                }
            }
        }
    }
}

/** Health Connect 周期聚合：步数、总能量消耗与睡眠，避免展示来源设备的逐条噪声。 */
@Composable
private fun HealthActivityCard(
    modifier: Modifier,
    summary: HealthActivitySummary,
) {
    val days = summary.rangeDays.coerceAtLeast(1)
    val avgSteps = summary.steps / days
    val avgBurned = summary.totalCaloriesBurned / days
    val avgSleepMinutes = summary.sleepMinutes / days
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                text = "活动与恢复（Health Connect）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReportStatItem(
                    modifier = Modifier.weight(1f),
                    value = String.format(Locale.CHINA, "%,d", avgSteps),
                    label = "日均步数",
                )
                VerticalDivider(Modifier.height(40.dp))
                ReportStatItem(
                    modifier = Modifier.weight(1f),
                    value = String.format(Locale.CHINA, "%,d", avgBurned),
                    label = "日均总消耗 kcal",
                )
                VerticalDivider(Modifier.height(40.dp))
                ReportStatItem(
                    modifier = Modifier.weight(1f),
                    value = "%d时%02d分".format(avgSleepMinutes / 60, avgSleepMinutes % 60),
                    label = "日均睡眠",
                )
            }
            Text(
                text = "总消耗包含基础代谢与活动消耗",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun TrafficLightLegend(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = RoundedCornerShape(4.dp),
            color = color,
        ) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
