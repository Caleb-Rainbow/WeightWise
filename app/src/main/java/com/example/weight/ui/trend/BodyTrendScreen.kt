package com.example.weight.ui.trend

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.MetricDisplay
import com.example.weight.data.record.MetricGuide
import com.example.weight.data.record.MetricPoint
import com.example.weight.data.record.TrendMetric
import com.example.weight.ui.common.MetricInfoDialog
import com.example.weight.ui.common.MetricStatusColors
import com.example.weight.ui.common.MetricTrendChart
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.common.movingAverage
import com.example.weight.ui.main.StatisticsScope
import com.example.weight.util.TimeUtils
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.common.vicoTheme
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat
import kotlin.math.abs

/** 共享格式器：DecimalFormat 构造含 pattern 编译，文件级单例避免每次组合重建 */
private val deltaIntFormat = DecimalFormat("+#;-#")
private val deltaDecimalFormat = DecimalFormat("+#.#;-#.#")

/**
 * 身体成分趋势页：指标 Chips 切换 + 单指标大图 + 区间统计摘要。
 * 入口在记录页身体成分详情弹窗；12 项数值指标共用一条图表管线，数据随 Room 表失效自动刷新。
 */
@Composable
fun BodyTrendScreen(
    goBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BodyTrendViewModel = koinViewModel(),
) {
    val selectedScope by viewModel.selectedScope.collectAsStateWithLifecycle()
    val selectedMetric by viewModel.selectedMetric.collectAsStateWithLifecycle()
    // null 仅表示首次加载（同首页 currentScopeData 语义）；切范围先回放旧值不闪空态
    val rawRecords by viewModel.rawRecords.collectAsStateWithLifecycle()
    val series by viewModel.metricSeries.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { MyTopBar(title = "成分趋势", goBack = goBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            TrendScopeSelector(selected = selectedScope, onSelected = viewModel::selectScope)
            Spacer(modifier = Modifier.height(4.dp))
            MetricGrid(selected = selectedMetric, onSelected = viewModel::selectMetric)
            Spacer(modifier = Modifier.height(8.dp))
            when {
                rawRecords == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                series.isEmpty() -> TrendEmptyHint(
                    metric = selectedMetric,
                    hasCompositionRecord = rawRecords.orEmpty().isNotEmpty(),
                )
                else -> TrendContent(metric = selectedMetric, series = series)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "指标为 App 依据阻抗与身体档案估算（±3-5%），仅供参考",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TrendContent(metric: TrendMetric, series: List<MetricPoint>) {
    // 序列统一在页面层提取一次，图表与摘要卡共用，避免两处各自 mapNotNull
    val values = remember(series, metric) { series.mapNotNull { metric.valueOf(it.composition) } }
    TrendChartCard(metric = metric, series = series, values = values)
    Spacer(modifier = Modifier.height(12.dp))
    TrendStatsCard(metric = metric, series = series, values = values)
}

/** 图表卡：标题行（指标名 + 最新值 + 状态徽章，点击弹指标解读）+ 趋势折线 */
@Composable
private fun TrendChartCard(metric: TrendMetric, series: List<MetricPoint>, values: List<Double>) {
    val gender by LocalStorageData.gender.collectAsStateWithLifecycle()
    val sexMale = gender != "FEMALE"
    val currentValue = values.lastOrNull()
    val info = remember(metric, currentValue, sexMale) {
        currentValue?.let { MetricGuide.info(metric.key, it, sexMale) }
    }
    var showInfo by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = info != null, role = Role.Button) { showInfo = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                currentValue?.let {
                    Text(
                        text = "${metric.formatValue(it)}${metric.unit}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                info?.status?.let { status ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MetricStatusColors.of(status).copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MetricStatusColors.of(status),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                series.lastOrNull()?.let {
                    Text(
                        text = "最新 ${TimeUtils.convertMillisToDate(it.timestamp).takeLast(5)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TrendChartBody(metric = metric, series = series, values = values)
        }
    }

    if (showInfo && info != null && currentValue != null) {
        MetricInfoDialog(
            display = MetricDisplay(
                metric.key, metric.label, "${metric.formatValue(currentValue)}${metric.unit}",
            ),
            info = info,
            onDismiss = { showInfo = false },
        )
    }
}

/** 数据提交与 [MetricTrendChart] 接线，模式与首页 StatisticChart 一致 */
@Composable
private fun TrendChartBody(metric: TrendMetric, series: List<MetricPoint>, values: List<Double>) {
    val labels = remember(series) { series.map { it.day } }
    // 7 日移动平均：不足 7 点不画；均线从第 7 个点起用显式 x 对齐横轴
    val movingAverage = remember(values) { movingAverage(values) }
    // producer 保持稳定，数据变化只增量提交事务，不销毁整棵图表子树（含滚动状态）
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values, movingAverage) {
        modelProducer.runTransaction {
            lineModel {
                series(values)
                if (movingAverage.isNotEmpty()) {
                    series(x = values.indices.drop(6), y = movingAverage)
                }
            }
        }
    }
    val (minY, maxY) = remember(values) {
        val maxV = values.max()
        val minV = values.min()
        // 上下各留 15% 边距避免顶底贴边；单点序列给 5%（至少 1）防零高度范围
        val pad = if (maxV == minV) maxOf(abs(maxV) * 0.05, 1.0) else (maxV - minV) * 0.15
        (minV - pad) to (maxV + pad)
    }
    MetricTrendChart(
        modelProducer = modelProducer,
        xLabels = labels,
        maxValue = maxY,
        minValue = minY,
        lineColor = vicoTheme.lineColor,
        axisTitle = metric.label,
        unit = metric.unit,
        decimalCount = metric.decimals,
        showMovingAverage = movingAverage.isNotEmpty(),
    )
}

/** 区间统计摘要：最高 / 最低 / 变化 三列，布局与首页统计汇总卡一致 */
@Composable
private fun TrendStatsCard(metric: TrendMetric, series: List<MetricPoint>, values: List<Double>) {
    if (values.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val maxIndex = values.indices.maxBy(values::get)
            val minIndex = values.indices.minBy(values::get)
            val delta = values.last() - values.first()
            val deltaFormat = if (metric.decimals == 0) deltaIntFormat else deltaDecimalFormat
            TrendStatItem(
                modifier = Modifier.weight(1f),
                value = metric.formatValue(values[maxIndex]),
                label = "最高",
                sub = series.getOrNull(maxIndex)?.timestamp?.let { TimeUtils.convertMillisToDate(it).takeLast(5) },
            )
            VerticalDivider(modifier = Modifier.height(40.dp))
            TrendStatItem(
                modifier = Modifier.weight(1f),
                value = metric.formatValue(values[minIndex]),
                label = "最低",
                sub = series.getOrNull(minIndex)?.timestamp?.let { TimeUtils.convertMillisToDate(it).takeLast(5) },
            )
            VerticalDivider(modifier = Modifier.height(40.dp))
            TrendStatItem(
                modifier = Modifier.weight(1f),
                value = deltaFormat.format(delta),
                label = "变化",
                sub = buildString {
                    series.firstOrNull()?.timestamp?.let { append(TimeUtils.convertMillisToDate(it).takeLast(5)) }
                    append(" 至 ")
                    series.lastOrNull()?.timestamp?.let { append(TimeUtils.convertMillisToDate(it).takeLast(5)) }
                },
                // 升降配色与首页体重视觉语义一致：降=primary、升=error
                valueColor = if (delta <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TrendStatItem(
    modifier: Modifier,
    value: String,
    label: String,
    sub: String?,
    valueColor: Color = Color.Unspecified,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (sub != null) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 指标网格：12 项指标 3 列 × 4 行铺满宽度，免横向滑动（chunked 等宽布局与成分网格同款） */
@Composable
private fun MetricGrid(selected: TrendMetric, onSelected: (TrendMetric) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrendMetric.entries.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { metric ->
                    FilterChip(
                        selected = metric == selected,
                        onClick = { onSelected(metric) },
                        label = { Text(metric.label, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 当前 12 项被 3 整除；未来增删指标不整除时补空位保持同格等宽
                repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** 范围选择器：与首页 ScopeSelector 同款（当前范围文字 + 下拉），遍历全部统计范围 */
@Composable
private fun TrendScopeSelector(selected: StatisticsScope, onSelected: (StatisticsScope) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClickLabel = "选择统计范围") { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StatisticsScope.entries.forEach { scope ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = scope.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (scope == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelected(scope)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 空态：范围内无任何成分记录给称重引导；仅当前指标缺数据时说明原因 */
@Composable
private fun TrendEmptyHint(metric: TrendMetric, hasCompositionRecord: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (hasCompositionRecord) "该范围内暂无「${metric.label}」的测量数据"
            else "该范围内暂无体脂秤成分记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasCompositionRecord) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "用体脂秤完成一次称重后，这里会自动生成各指标的趋势",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
