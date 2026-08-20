package com.example.weight.util

import com.example.weight.data.diet.DailyCalories
import com.example.weight.data.diet.TrafficLightCount
import com.example.weight.data.record.DailyMinWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportAggregatorTest {

    private fun day(date: String, weight: Double) = DailyMinWeight(
        minWeight = weight, recordDay = date, timestamp = 0L
    )

    @Test
    fun `空打卡列表返回null`() {
        assertNull(ReportAggregator.weightStats(emptyList()))
    }

    @Test
    fun `体重统计取首末与均值`() {
        val stats = ReportAggregator.weightStats(
            listOf(day("2026-08-17", 80.0), day("2026-08-18", 79.5), day("2026-08-20", 79.0))
        )!!
        assertEquals(80.0, stats.startWeight, 1e-9)
        assertEquals(79.0, stats.endWeight, 1e-9)
        assertEquals(-1.0, stats.netChange, 1e-9)
        assertEquals((80.0 + 79.5 + 79.0) / 3, stats.avgWeight, 1e-9)
        assertEquals(80.0, stats.maxWeight, 1e-9)
        assertEquals(79.0, stats.minWeight, 1e-9)
        assertEquals(3, stats.recordedDays)
    }

    @Test
    fun `较上期对比为两期净变化之差`() {
        val current = listOf(day("2026-08-17", 80.0), day("2026-08-23", 79.0))
        val previous = listOf(day("2026-08-10", 81.0), day("2026-08-16", 80.5))
        // 本期 -1.0，上期 -0.5，较上期多降 0.5
        assertEquals(-0.5, ReportAggregator.changeVsPrevPeriod(current, previous)!!, 1e-9)
        // 上期无数据
        assertNull(ReportAggregator.changeVsPrevPeriod(current, emptyList()))
        // 本期无数据
        assertNull(ReportAggregator.changeVsPrevPeriod(emptyList(), previous))
    }

    @Test
    fun `热量统计含日均超标与红绿灯`() {
        val stats = ReportAggregator.caloriesStats(
            dailyCalories = listOf(
                DailyCalories("2026-08-17", 1800),
                DailyCalories("2026-08-18", 2200),
                DailyCalories("2026-08-19", 1600),
            ),
            trafficLights = listOf(
                TrafficLightCount("GREEN", 4), TrafficLightCount("RED", 2),
            ),
            recommendedIntake = 1700,
        )!!
        assertEquals(1867, stats.avgCalories)
        assertEquals(3, stats.recordedDays)
        assertEquals(2, stats.daysOverTarget)
        assertEquals(true, stats.hasTarget)
        assertEquals(4, stats.greenCount)
        assertEquals(0, stats.yellowCount)
        assertEquals(2, stats.redCount)
    }

    @Test
    fun `未配置建议摄入不统计超标`() {
        val stats = ReportAggregator.caloriesStats(
            dailyCalories = listOf(DailyCalories("2026-08-17", 3000)),
            trafficLights = emptyList(),
            recommendedIntake = null,
        )!!
        assertEquals(false, stats.hasTarget)
        assertEquals(0, stats.daysOverTarget)
    }

    @Test
    fun `无饮食记录返回null`() {
        assertNull(ReportAggregator.caloriesStats(emptyList(), emptyList(), 1700))
    }

    @Test
    fun `BMI计算与身高未设置`() {
        assertEquals(24.22, ReportAggregator.bmi(70.0, 170.0)!!, 0.01)
        assertNull(ReportAggregator.bmi(70.0, 0.0))
    }
}
