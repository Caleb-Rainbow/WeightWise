package com.example.weight.ui.diet

import kotlin.math.roundToInt

/** 历史页趋势摘要（T-5）：三数字行 + 热力格共用一份聚合结果 */
data class HistoryTrend(
    val recordedDays: Int,
    /** 有记录日的日均热量（roundToInt），无记录为 0 */
    val avgCalories: Int,
    /** 超出建议摄入的天数；null=未配置建议摄入，不可比（UI 显示「—」降级） */
    val overDays: Int?,
    /** 达标天数（≤建议摄入，含「接近建议」档）；null 同上 */
    val onTargetDays: Int?,
)

/** 热力格单日状态：空档/有记录但不可比/摄入三档（与 CalorieCalculator.intakeStatus 分档一致） */
enum class HistoryCellStatus { EMPTY, UNKNOWN, ENOUGH, NEAR_LIMIT, OVER }

/**
 * 历史页趋势聚合纯函数。超标口径与 ReportAggregator.caloriesStats 一致：
 * target 非正/未配置 → 不可比；calories > target 记超标。
 * 热力格细分三档复用 intakeStatus 的 90% 阈值（≤90% 达标有余量，90%~100% 接近，>100% 超出）。
 */
object HistoryTrendAggregator {

    fun trend(days: List<HistoryDay>, recommendedIntake: Int?): HistoryTrend {
        val recorded = days.filter { it.records.isNotEmpty() }
        val target = recommendedIntake?.takeIf { it > 0 }
        return HistoryTrend(
            recordedDays = recorded.size,
            avgCalories = if (recorded.isEmpty()) 0
            else recorded.mapNotNull { it.totalCalories }.average().roundToInt(),
            overDays = target?.let { t -> recorded.count { (it.totalCalories ?: 0) > t } },
            onTargetDays = target?.let { t -> recorded.count { (it.totalCalories ?: 0) <= t } },
        )
    }

    /** 与输入 [days] 同序（新→旧）的格子状态序列，供热力格反转后按周渲染 */
    fun cellStatuses(days: List<HistoryDay>, recommendedIntake: Int?): List<HistoryCellStatus> {
        val target = recommendedIntake?.takeIf { it > 0 }
        return days.map { day ->
            if (day.records.isEmpty()) {
                HistoryCellStatus.EMPTY
            } else if (target == null) {
                HistoryCellStatus.UNKNOWN
            } else {
                val calories = day.totalCalories ?: return@map HistoryCellStatus.UNKNOWN
                when {
                    calories > target -> HistoryCellStatus.OVER
                    calories > target * 0.9 -> HistoryCellStatus.NEAR_LIMIT
                    else -> HistoryCellStatus.ENOUGH
                }
            }
        }
    }
}
