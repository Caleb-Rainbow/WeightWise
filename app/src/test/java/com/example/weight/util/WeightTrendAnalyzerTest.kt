package com.example.weight.util

import com.example.weight.data.record.DailyWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightTrendAnalyzerTest {

    @Test
    fun `七日均值按自然日窗口且完整覆盖后才输出`() {
        val insight = WeightTrendAnalyzer.analyze(
            dailyWeights = weights((0..7).map { 70.0 + it }),
            totalDays = 8,
        )

        assertEquals(2, insight.sevenDayAverage.size)
        assertEquals(6, insight.sevenDayAverage.first().index)
        assertEquals(73.0, insight.sevenDayAverage.first().value, 1e-9)
        assertEquals(74.0, insight.sevenDayAverage.last().value, 1e-9)
    }

    @Test
    fun `连续下降被识别为高可信下降趋势`() {
        val insight = WeightTrendAnalyzer.analyze(
            dailyWeights = weights((0..13).map { 80.0 - it * 0.15 }),
            totalDays = 14,
            targetWeight = 70.0,
        )

        assertEquals(TrendConfidence.HIGH, insight.confidence)
        assertEquals(WeightTrendDirection.LOSING, insight.direction)
        assertTrue(insight.weeklyRateKg!! < -0.5)
        assertFalse(insight.isPlateau)
    }

    @Test
    fun `两周稳定且距离目标较远时识别平台期`() {
        val values = (0..16).map { if (it % 2 == 0) 75.1 else 75.0 }
        val insight = WeightTrendAnalyzer.analyze(weights(values), totalDays = 17, targetWeight = 70.0)

        assertEquals(TrendConfidence.HIGH, insight.confidence)
        assertTrue(insight.isPlateau)
    }

    @Test
    fun `最新单日跳升与平滑线偏离时标记短期波动`() {
        val values = List(9) { 70.0 } + 72.0
        val insight = WeightTrendAnalyzer.analyze(weights(values), totalDays = 10)

        assertNotNull(insight.fluctuation)
        assertEquals(FluctuationDirection.ABOVE_TREND, insight.fluctuation!!.direction)
        assertTrue(insight.fluctuation.deltaKg > 1.0)
    }

    @Test
    fun `三天数据明确标为低可信且不报平台期`() {
        val insight = WeightTrendAnalyzer.analyze(weights(listOf(70.0, 70.2, 70.1)), 7, 65.0)

        assertEquals(TrendConfidence.LOW, insight.confidence)
        assertFalse(insight.isPlateau)
    }

    private fun weights(values: List<Double>): List<DailyWeight> = values.mapIndexed { index, value ->
        DailyWeight(
            value = value,
            recordDay = "2026-08-${String.format("%02d", index + 1)}",
            timestamp = BASE_TIME + index * DAY_MS,
        )
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val BASE_TIME = 1_775_001_600_000L
    }
}
