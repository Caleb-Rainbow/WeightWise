package com.example.weight.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
                "每格一天",
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

/** 窗口列数（列=周）不超过该值时用日历形态；超过则格子会太小，改紧凑形态 */
private const val CALENDAR_FORM_MAX_COLUMNS = 6

/**
 * 迷你热力格（周一为行/列之首），格子尺寸自适应铺满卡宽，右侧不留空白：
 * - 短窗口（30 天，≤6 列）：7 列日历形态，行=周，格内带日期数字；
 * - 长窗口（90/180 天）：列=周的紧凑形态（贡献图式），纯色格铺满整行。
 */
@Composable
private fun MiniHeatGrid(days: List<HistoryDay>, statuses: List<HistoryCellStatus>) {
    if (days.isEmpty()) return
    val isDark = LocalIsDarkTheme.current
    // days 新→旧：反转为旧→新后从左到右排布
    val cells = remember(days, statuses) { statuses.asReversed() }
    val dayNumbers = remember(days) { days.asReversed().map { LocalDate.parse(it.date).dayOfMonth } }
    val leadBlanks = remember(days) {
        // LocalDate.dayOfWeek.ordinal：MONDAY=0 … SUNDAY=6，与格子行号一致
        LocalDate.parse(days.last().date).dayOfWeek.ordinal
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val todayIndex = cells.lastIndex // 旧→新序列末位即今天
        val columns = (leadBlanks + cells.size + 6) / 7

        if (columns <= CALENDAR_FORM_MAX_COLUMNS) {
            // 日历形态：行=周、列=周一~周日，格子放大铺满 7 列并显示日期数字
            val gap = 3.dp
            val cellSize = (maxWidth - gap * 6) / 7
            val rows = columns
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                var index = -leadBlanks
                repeat(rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(7) {
                            CalendarDayCell(
                                status = cells.getOrNull(index),
                                dayNumber = dayNumbers.getOrNull(index),
                                isToday = index == todayIndex,
                                size = cellSize,
                                isDark = isDark,
                            )
                            index++
                        }
                    }
                }
            }
        } else {
            // 紧凑形态：列=周（每列 7 格纵向），格子自适应铺满整行宽度
            val gap = 2.dp
            val cellSize = (maxWidth - gap * (columns - 1)) / columns
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(columns) { col ->
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(7) { row ->
                            val index = col * 7 + row - leadBlanks
                            val status = cells.getOrNull(index)
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
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
    }
}

/** 日历形态单格：状态色底 + 居中日期数字；窗口外补位（status/dayNumber 双 null）完全透明 */
@Composable
private fun CalendarDayCell(
    status: HistoryCellStatus?,
    dayNumber: Int?,
    isToday: Boolean,
    size: androidx.compose.ui.unit.Dp,
    isDark: Boolean,
) {
    val shape = RoundedCornerShape(6.dp)
    if (dayNumber == null) {
        // 窗口外的前后补位：不渲染任何内容，仅占位对齐
        Box(modifier = Modifier.size(size))
        return
    }
    val filled = status != null && status != HistoryCellStatus.EMPTY
    Box(
        modifier = Modifier
            .size(size)
            .let { m -> if (isToday) m.border(1.dp, MaterialTheme.colorScheme.onSurface, shape) else m }
            .background(
                when (status) {
                    null -> androidx.compose.ui.graphics.Color.Transparent
                    else -> status.cellColor(isDark)
                },
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            dayNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            // 状态色块按昼夜取深浅反色数字；空档日无底色，用次要文字色
            color = if (!filled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            else if (isDark) androidx.compose.ui.graphics.Color(0xFF10201A)
            else androidx.compose.ui.graphics.Color.White,
        )
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
