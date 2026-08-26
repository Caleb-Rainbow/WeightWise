package com.example.weight.ui.diet

import org.junit.Assert.assertEquals
import org.junit.Test

/** 历史页趋势聚合（T-5）：达标口径与 ReportAggregator/intakeStatus 对齐 */
class HistoryTrendAggregatorTest {

    private fun recordedDay(date: String, calories: Int) = HistoryDay(
        date = date,
        records = listOf(
            DietRecordFixture(date),
        ),
        totalCalories = calories,
        trafficLight = "GREEN",
    )

    private fun DietRecordFixture(date: String) = com.example.weight.data.diet.DietRecord(
        date = date,
        timestamp = 0L,
        mealType = "LUNCH",
        recognizedFoodJson = "[]",
        estimatedCalories = 100,
    )

    @Test
    fun `空窗口全为零值且不可比字段为null`() {
        val trend = HistoryTrendAggregator.trend(emptyList(), recommendedIntake = 2000)
        assertEquals(0, trend.recordedDays)
        assertEquals(0, trend.avgCalories)
        assertEquals(0, trend.overDays)
        assertEquals(0, trend.onTargetDays)
    }

    @Test
    fun `日均热量只含有记录日_空档日不计入`() {
        val days = listOf(
            recordedDay("2026-08-25", 1800),
            recordedDay("2026-08-24", 2200),
            HistoryDay("2026-08-23"), // 空档日
        )
        val trend = HistoryTrendAggregator.trend(days, recommendedIntake = 2000)
        assertEquals(2, trend.recordedDays)
        assertEquals(2000, trend.avgCalories)
    }

    @Test
    fun `超标口径与报告页一致_严格大于target`() {
        val days = listOf(
            recordedDay("2026-08-25", 2000), // == target 不算超标
            recordedDay("2026-08-24", 2001), // 刚超算超标
            recordedDay("2026-08-23", 1500),
        )
        val trend = HistoryTrendAggregator.trend(days, recommendedIntake = 2000)
        assertEquals(1, trend.overDays)
        assertEquals(2, trend.onTargetDays)
    }

    @Test
    fun `未配置建议摄入时达标字段不可比为null`() {
        val days = listOf(recordedDay("2026-08-25", 1800))
        val trend = HistoryTrendAggregator.trend(days, recommendedIntake = null)
        assertEquals(1, trend.recordedDays)
        assertEquals(1800, trend.avgCalories)
        assertEquals(null, trend.overDays)
        assertEquals(null, trend.onTargetDays)
    }

    @Test
    fun `非正建议摄入视为未配置`() {
        val days = listOf(recordedDay("2026-08-25", 1800))
        val trend = HistoryTrendAggregator.trend(days, recommendedIntake = 0)
        assertEquals(null, trend.overDays)
    }

    @Test
    fun `格子状态按intakeStatus分档_90百分比阈值`() {
        val days = listOf(
            recordedDay("2026-08-25", 2500), // >100% OVER
            recordedDay("2026-08-24", 2000), // ==100% NEAR_LIMIT
            recordedDay("2026-08-23", 1850), // 92.5% NEAR_LIMIT
            recordedDay("2026-08-22", 1800), // ==90% ENOUGH
            recordedDay("2026-08-21", 1000), // ENOUGH
            HistoryDay("2026-08-20"),        // 空档 EMPTY
        )
        val statuses = HistoryTrendAggregator.cellStatuses(days, recommendedIntake = 2000)
        assertEquals(
            listOf(
                HistoryCellStatus.OVER,
                HistoryCellStatus.NEAR_LIMIT,
                HistoryCellStatus.NEAR_LIMIT,
                HistoryCellStatus.ENOUGH,
                HistoryCellStatus.ENOUGH,
                HistoryCellStatus.EMPTY,
            ),
            statuses,
        )
    }

    @Test
    fun `未配置建议摄入时有记录日格子为UNKNOWN_空档仍为EMPTY`() {
        val days = listOf(
            recordedDay("2026-08-25", 1800),
            HistoryDay("2026-08-24"),
        )
        assertEquals(
            listOf(HistoryCellStatus.UNKNOWN, HistoryCellStatus.EMPTY),
            HistoryTrendAggregator.cellStatuses(days, recommendedIntake = null),
        )
    }
}
