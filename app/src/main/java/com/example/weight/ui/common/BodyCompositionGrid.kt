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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.BodyComposition
import com.example.weight.data.record.MetricDisplay
import com.example.weight.data.record.MetricGuide
import com.example.weight.ui.theme.DayNightColor
import com.example.weight.ui.theme.resolve
import java.util.Locale

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

/** 指标解读弹窗：名称+当前值、状态、是什么、正常范围、带当前位置的范围条（成分趋势页复用） */
@Composable
internal fun MetricInfoDialog(display: MetricDisplay, info: MetricGuide.Info, onDismiss: () -> Unit) {
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

/** 健康指标状态语义色（DayNightColor 昼夜成对，色值与饮食红绿灯/宏量色板同族保持全局一致；成分趋势页复用） */
internal object MetricStatusColors {
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
 * - 当前值气泡跟随指针，先回答「我在哪」；
 * - 状态色段收成一条连续细轨，当前区间强调、其他区间降噪；
 * - 区间文字按比例对齐在轨道下方，颜色不是唯一的状态表达。
 */
@Composable
private fun MetricRangeBar(bar: MetricGuide.Bar) {
    val span = (bar.max - bar.min).takeIf { it > 0 } ?: return
    fun frac(v: Double) = (((v - bar.min) / span).toFloat()).coerceIn(0f, 1f)

    // 分带口径是「偏低 [min, low)、标准 [low, normal]、偏高 (normal, high]」。
    // 边界值不能只用 first/last 匹配，否则 10、20 这类临界值会落到错误区间。
    val currentSegment = bar.segments.firstOrNull { segment ->
        when (segment.status) {
            MetricGuide.Status.LOW -> bar.value < segment.end
            else -> bar.value <= segment.end
        }
    } ?: if (bar.value < bar.min) bar.segments.first() else bar.segments.last()
    val currentColor = MetricStatusColors.of(currentSegment.status)
    val currentLabel = "当前 ${trim(bar.value)}"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "$currentLabel，${currentSegment.status.label}，显示范围 ${trim(bar.min)} 到 ${trim(bar.max)}"
            },
    ) {
        val trackWidth = maxWidth
        val trackHeight = 8.dp
        val markerSize = 18.dp
        val badgeWidth = 80.dp
        val segmentLabelWidth = 36.dp
        val compactLabelWidth = 20.dp
        val markerCenter = trackWidth * frac(bar.value)
        val markerX = (markerCenter - markerSize / 2).coerceIn(0.dp, trackWidth - markerSize)
        val badgeX = (markerCenter - badgeWidth / 2).coerceIn(0.dp, trackWidth - badgeWidth)

        Column(modifier = Modifier.fillMaxWidth()) {
            // 当前值从轨道中移出，避免数字、指针和区间文字挤在同一层。
            Box(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                Surface(
                    modifier = Modifier
                        .offset(x = badgeX)
                        .width(badgeWidth)
                        .height(26.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = currentColor.copy(alpha = 0.14f),
                    contentColor = currentColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }

            // 连续细轨道：用明度区分当前区间，减少整条高饱和色块带来的视觉噪声。
            Box(modifier = Modifier.fillMaxWidth().height(markerSize)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    bar.segments.forEachIndexed { index, segment ->
                        val segmentStart = trackWidth * frac(segment.start)
                        val segmentWidth = trackWidth * (frac(segment.end) - frac(segment.start))
                        Box(
                            modifier = Modifier
                                .offset(x = segmentStart)
                                .width(maxOf(segmentWidth, 1.dp))
                                .height(trackHeight)
                                .background(
                                    MetricStatusColors.of(segment.status).copy(
                                        alpha = if (segment == currentSegment) 0.82f else 0.34f,
                                    ),
                                ),
                        )
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .offset(x = segmentStart)
                                    .width(1.dp)
                                    .height(trackHeight)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                            )
                        }
                    }
                }

                // 空心把手比黑色实心圆更轻，内点继续使用当前状态色保证定位清晰。
                Box(
                    modifier = Modifier
                        .offset(x = markerX)
                        .size(markerSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, currentColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(currentColor),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 每个色段都有文字标签，兼顾色觉差异，也让分区结构一眼可扫。
            Box(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                bar.segments.forEach { segment ->
                    val segmentWidth = trackWidth * (frac(segment.end) - frac(segment.start))
                    val (label, labelWidth) = when {
                        segmentWidth >= segmentLabelWidth -> segment.status.label to segmentLabelWidth
                        segmentWidth >= compactLabelWidth -> when (segment.status) {
                            MetricGuide.Status.LOW -> "低"
                            MetricGuide.Status.NORMAL -> "标"
                            MetricGuide.Status.HIGH -> "高"
                            MetricGuide.Status.VERY_HIGH -> "高+"
                        } to compactLabelWidth
                        else -> null to compactLabelWidth
                    }
                    if (label != null) {
                        val center = trackWidth * (frac(segment.start) + frac(segment.end)) / 2
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (segment == currentSegment) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (segment == currentSegment) {
                                currentColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.offset(x = center - labelWidth / 2),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun trim(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.CHINA, "%.1f", v)
