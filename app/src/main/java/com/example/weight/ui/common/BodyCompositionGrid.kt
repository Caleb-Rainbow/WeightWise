package com.example.weight.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.BodyComposition
import com.example.weight.data.record.MetricDisplay
import com.example.weight.data.record.MetricGuide
import com.example.weight.ui.theme.DayNightColor
import com.example.weight.ui.theme.resolve

/**
 * 身体成分指标网格：3 列小卡（上数值下标签），渲染 [BodyComposition.metricItems] 全部指标。
 * 卡片右上角感叹号提示可点，点击弹出该指标的解读（是什么/正常范围/当前所处位置）。
 * 弹窗状态内聚在组件内，调用方（记录弹窗/记录详情）无需各自接线。
 */
@Composable
fun BodyCompositionGrid(composition: BodyComposition, modifier: Modifier = Modifier) {
    val items = composition.metricItems()
    if (items.isEmpty()) return

    val gender by LocalStorageData.gender.collectAsStateWithLifecycle()
    val sexMale = gender != "FEMALE"
    var explainTarget by remember { mutableStateOf<MetricDisplay?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    MetricCell(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = { explainTarget = item },
                    )
                }
                // 行内不足 3 个时补空位，保持同格等宽
                repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }

    explainTarget?.let { target ->
        val info = MetricGuide.info(target.key, composition.rawValueOf(target.key), sexMale)
        if (info != null) {
            MetricInfoDialog(
                display = target,
                info = info,
                onDismiss = { explainTarget = null },
            )
        }
    }
}

/** 单个指标小卡：数值 + 感叹号角标 + 标签 */
@Composable
private fun MetricCell(item: MetricDisplay, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = "${item.label}说明",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.TopEnd),
            )
        }
    }
}

/** 指标解读弹窗：名称+当前值、状态、是什么、正常范围、带当前位置的范围条 */
@Composable
private fun MetricInfoDialog(display: MetricDisplay, info: MetricGuide.Info, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.name, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = display.value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                info.status?.let { status ->
                    Surface(
                        shape = CircleShape,
                        color = when (status) {
                            MetricGuide.Status.NORMAL -> MaterialTheme.colorScheme.primaryContainer
                            MetricGuide.Status.VERY_HIGH -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = when (status) {
                                MetricGuide.Status.NORMAL -> MaterialTheme.colorScheme.onPrimaryContainer
                                MetricGuide.Status.VERY_HIGH -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = info.rangeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                info.bar?.let { bar ->
                    Spacer(modifier = Modifier.height(14.dp))
                    MetricRangeBar(bar)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

/** 健康指标状态语义色（DayNightColor 昼夜成对，色值与饮食红绿灯/宏量色板同族保持全局一致） */
private object MetricStatusColors {
    val Low = DayNightColor(Color(0xFF2B638B), Color(0xFF7DB0DC))       // 蓝：偏低（同 Protein 蓝）
    val Normal = DayNightColor(Color(0xFF378646), Color(0xFF87D98F))     // 绿：标准（同红绿灯绿）
    val High = DayNightColor(Color(0xFF9B7000), Color(0xFFF8BD42))       // 琥珀：偏高
    val VeryHigh = DayNightColor(Color(0xFFD44439), Color(0xFFFFB4AA))   // 红：过高

    @Composable
    fun of(status: MetricGuide.Status) = when (status) {
        MetricGuide.Status.LOW -> Low.resolve()
        MetricGuide.Status.NORMAL -> Normal.resolve()
        MetricGuide.Status.HIGH -> High.resolve()
        MetricGuide.Status.VERY_HIGH -> VeryHigh.resolve()
    }
}

/**
 * 分段式指标刻度条：
 * - 状态色段铺满显示区间（段间 2dp 间隙、圆角），正常段中央标「标准」；
 * - 当前值为带描边的指针圆点叠加在条上；
 * - 段边界标刻度数字（相邻 <26dp 自动省略防重叠，首尾必标）；
 * - 指针下方跟随显示当前数值（clamp 防溢出）。
 */
@Composable
private fun MetricRangeBar(bar: MetricGuide.Bar) {
    val span = (bar.max - bar.min).takeIf { it > 0 } ?: return
    fun frac(v: Double) = (((v - bar.min) / span).toFloat()).coerceIn(0f, 1f)

    // 先测宽再分三行布局（色段/刻度/当前值），各行用同一 trackWidth 定位保证严格对齐
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val trackWidth = maxWidth
        val gap = 2.dp
        val barHeight = 14.dp
        val dotSize = 14.dp
        val labelWidth = 24.dp // labelSmall 两字符近似宽，用于首尾防溢出与防重叠判定

        Column(modifier = Modifier.fillMaxWidth()) {
            // ── 色段条 + 指针圆点 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight),
            ) {
                bar.segments.forEach { seg ->
                    val segStart = trackWidth * frac(seg.start)
                    val segEnd = trackWidth * frac(seg.end) - gap
                    Box(
                        modifier = Modifier
                            .offset(x = segStart)
                            .width(maxOf(segEnd - segStart, 3.dp))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MetricStatusColors.of(seg.status)),
                    )
                }
                // 正常段中央「标准」小字（段宽放得下才显示）
                val normalSeg = bar.segments.firstOrNull { it.status == MetricGuide.Status.NORMAL }
                if (normalSeg != null) {
                    val normalWidth = trackWidth * (frac(normalSeg.end) - frac(normalSeg.start))
                    if (normalWidth >= 44.dp) {
                        Text(
                            text = "标准",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier
                                .offset(
                                    x = trackWidth * (frac(normalSeg.start) + frac(normalSeg.end)) / 2 -
                                        labelWidth / 2,
                                ),
                        )
                    }
                }
                // 指针：描边圆点叠在条上，clamp 进条内
                Box(
                    modifier = Modifier
                        .offset(x = (trackWidth - dotSize) * frac(bar.value))
                        .align(Alignment.TopStart)
                        .offset(y = (barHeight - dotSize) / 2)
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
            }

            // ── 刻度数字行：段边界（含首尾）标数值，相邻过近自动省略 ──
            val bounds = buildList {
                add(bar.min)
                bar.segments.forEach { add(it.end) }
            }.distinct()
            Box(modifier = Modifier.fillMaxWidth()) {
                var lastRight = (-100).dp
                bounds.forEachIndexed { index, bound ->
                    val x = when (index) {
                        0 -> 0.dp
                        bounds.lastIndex -> trackWidth - labelWidth
                        else -> trackWidth * frac(bound) - labelWidth / 2
                    }
                    if (index == 0 || index == bounds.lastIndex || x > lastRight + 4.dp) {
                        Text(
                            text = trim(bound),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(x = x),
                        )
                        lastRight = x + labelWidth
                    }
                }
            }

            // ── 当前值跟随行（clamp 防溢出）──
            Text(
                text = trim(bar.value),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .offset(
                        x = ((trackWidth - labelWidth) * frac(bar.value))
                            .coerceIn(0.dp, trackWidth - labelWidth),
                    ),
            )
        }
    }
}

private fun trim(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
