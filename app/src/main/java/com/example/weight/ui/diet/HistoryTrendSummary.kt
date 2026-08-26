package com.example.weight.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.weight.ui.theme.LocalIsDarkTheme
import com.example.weight.ui.theme.resolve
import java.time.LocalDate

/**
 * 历史页趋势摘要卡（T-5）：三数字行 + 周对齐迷你热力格 + 图例。
 * 着色用额度状态语义色（IntakeRingColors）——热量对建议摄入的分档，
 * 与食物红绿灯（TrafficLightColors）语义隔离，不可混用。
 */
@Composable
internal fun HistoryTrendSummary(state: HistoryTabState, modifier: Modifier = Modifier) {
    val isDark = LocalIsDarkTheme.current
    val trend = remember(state.days, state.recommendedIntake) {
        HistoryTrendAggregator.trend(state.days, state.recommendedIntake)
    }
    val statuses = remember(state.days, state.recommendedIntake) {
        HistoryTrendAggregator.cellStatuses(state.days, state.recommendedIntake)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "近${state.rangeDays}天概况",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            TrendStatCell("记录", trend.recordedDays.toString(), "天", Modifier.weight(1f))
            TrendStatCell("日均", trend.avgCalories.toString(), "kcal", Modifier.weight(1f))
            TrendStatCell(
                "超出额度",
                trend.overDays?.toString() ?: "—",
                if (trend.overDays != null) "天" else "",
                Modifier.weight(1f),
            )
        }
        if (state.recommendedIntake == null || state.recommendedIntake <= 0) {
            Text(
                "补全个人档案后，可按建议摄入评估每日额度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MiniHeatGrid(days = state.days, statuses = statuses)

        // 图例：颜色语义必须配文字（DESIGN.md）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HistoryLegendDot(IntakeRingCellColorResolved.ENOUGH, isDark, "有余量")
            HistoryLegendDot(IntakeRingCellColorResolved.NEAR_LIMIT, isDark, "接近")
            HistoryLegendDot(IntakeRingCellColorResolved.OVER, isDark, "超出")
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "格子=天，左旧右新",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendStatCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (unit.isNotEmpty()) {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 周对齐（周一为一行之首）迷你热力格：列=周，超出屏宽横向滚动，最新一天在最右 */
@Composable
private fun MiniHeatGrid(days: List<HistoryDay>, statuses: List<HistoryCellStatus>) {
    if (days.isEmpty()) return
    val isDark = LocalIsDarkTheme.current
    // days 新→旧：反转为旧→新后按周分列
    val cells = remember(days, statuses) { statuses.asReversed() }
    val leadBlanks = remember(days) {
        // LocalDate.dayOfWeek.ordinal：MONDAY=0 … SUNDAY=6，与格子行号一致
        LocalDate.parse(days.last().date).dayOfWeek.ordinal
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val totalSlots = leadBlanks + cells.size
        val columns = (totalSlots + 6) / 7
        val todayIndex = cells.lastIndex // 旧→新序列末位即今天
        repeat(columns) { col ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { row ->
                    val index = col * 7 + row - leadBlanks
                    val status = cells.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .let { m ->
                                if (index == todayIndex) m.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface,
                                    RoundedCornerShape(3.dp),
                                ) else m
                            }
                            .background(
                                status.cellColor(isDark),
                                RoundedCornerShape(3.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryLegendDot(colorResolvable: IntakeRingCellColorResolved, isDark: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colorResolvable.color(isDark), RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 额度状态格子的着色解析：集中一处，避免散落的 when */
private enum class IntakeRingCellColorResolved {
    ENOUGH, NEAR_LIMIT, OVER, UNKNOWN, EMPTY;

    fun color(isDark: Boolean): androidx.compose.ui.graphics.Color = when (this) {
        ENOUGH -> IntakeRingColors.Enough.resolve(isDark)
        NEAR_LIMIT -> IntakeRingColors.NearLimit.resolve(isDark)
        OVER -> IntakeRingColors.Over.resolve(isDark)
        UNKNOWN -> IntakeRingColors.Track.resolve(isDark)
        EMPTY -> IntakeRingColors.Track.resolve(isDark).copy(alpha = 0.45f)
    }
}

private fun HistoryCellStatus?.cellColor(isDark: Boolean): androidx.compose.ui.graphics.Color =
    when (this) {
        null -> IntakeRingCellColorResolved.EMPTY.color(isDark) // 周首对齐补位
        HistoryCellStatus.EMPTY -> IntakeRingCellColorResolved.EMPTY.color(isDark)
        HistoryCellStatus.UNKNOWN -> IntakeRingCellColorResolved.UNKNOWN.color(isDark)
        HistoryCellStatus.ENOUGH -> IntakeRingCellColorResolved.ENOUGH.color(isDark)
        HistoryCellStatus.NEAR_LIMIT -> IntakeRingCellColorResolved.NEAR_LIMIT.color(isDark)
        HistoryCellStatus.OVER -> IntakeRingCellColorResolved.OVER.color(isDark)
    }
