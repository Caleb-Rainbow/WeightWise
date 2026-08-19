package com.example.weight.util

import com.example.weight.data.record.DailyMinWeight
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightPredictorTest {

    private val dayMillis = 24L * 60 * 60 * 1000
    private val baseTs = 1_750_000_000_000L // 任意固定基准时间

    /** 生成按天递增的每日最低体重记录，weights[i] 为第 i 天的体重 */
    private fun dailyWeights(weights: List<Double>): List<DailyMinWeight> =
        weights.mapIndexed { i, w ->
            DailyMinWeight(minWeight = w, recordDay = "day-$i", timestamp = baseTs + i * dayMillis)
        }

    @Test
    fun `稳定下降时按当前速率预测`() {
        // 60 天每天 -0.1kg：80 -> 74.1，剩余 3.85kg，理论 38.5 天，向上取整 39
        val records = dailyWeights(List(60) { 80.0 - 0.1 * it })
        val days = WeightPredictor.estimateDaysToTarget(records, currentWeight = 74.1, targetWeight = 70.25)
        assertTrue("预期 39 天，实际 $days", days == 39L)
    }

    @Test
    fun `单日波动被回归平滑`() {
        // 在稳定下降的基础上叠加 ±0.3kg 的交替噪声，预测应仍接近理论值 38.5 天
        val records = dailyWeights(List(60) { 80.0 - 0.1 * it + if (it % 2 == 0) 0.3 else -0.3 })
        val days = WeightPredictor.estimateDaysToTarget(records, currentWeight = 74.1, targetWeight = 70.25)
        assertTrue("预期在 30~50 天之间，实际 $days", days != null && days in 30..50)
    }

    @Test
    fun `近期停滞时不会被早期的快速减重带偏`() {
        // 前 60 天每天 -0.2kg（90 -> 78.2），后 30 天一直停在 78.2。
        // 旧算法按全历史平均会得出约 61 天；新算法应给出明显更久的天数或拒绝预测
        val records = dailyWeights(
            buildList {
                repeat(60) { add(90.0 - 0.2 * it) }
                repeat(30) { add(78.2) }
            }
        )
        val days = WeightPredictor.estimateDaysToTarget(records, currentWeight = 78.2, targetWeight = 70.0)
        assertTrue("不应给出乐观的 61 天以内的预测，实际 $days", days == null || days > 61L)
    }

    @Test
    fun `平台期返回null`() {
        val records = dailyWeights(List(30) { 75.0 })
        assertNull(WeightPredictor.estimateDaysToTarget(records, currentWeight = 75.0, targetWeight = 70.0))
    }

    @Test
    fun `趋势反向返回null`() {
        // 减重目标，但近 40 天每天 +0.05kg 正在增重
        val records = dailyWeights(List(40) { 70.0 + 0.05 * it })
        assertNull(WeightPredictor.estimateDaysToTarget(records, currentWeight = 71.95, targetWeight = 68.0))
    }

    @Test
    fun `支持增重目标`() {
        // 40 天每天 +0.04kg：60 -> 61.56，距目标 63.1 还差 1.54kg，理论 38.5 天
        val records = dailyWeights(List(40) { 60.0 + 0.04 * it })
        val days = WeightPredictor.estimateDaysToTarget(records, currentWeight = 61.56, targetWeight = 63.1)
        assertTrue("预期 39 天，实际 $days", days == 39L)
    }

    @Test
    fun `趋势过慢超出可信范围返回null`() {
        // 每天仅 -0.01kg，距 60kg 目标理论上要近 3000 天，超出 730 天上限
        val records = dailyWeights(List(30) { 90.0 - 0.01 * it })
        assertNull(WeightPredictor.estimateDaysToTarget(records, currentWeight = 89.71, targetWeight = 60.0))
    }

    @Test
    fun `数据点不足返回null`() {
        assertNull(
            WeightPredictor.estimateDaysToTarget(
                dailyWeights(listOf(80.0, 79.5, 79.2, 79.0)),
                currentWeight = 79.0,
                targetWeight = 70.0
            )
        )
    }

    @Test
    fun `时间跨度过短返回null`() {
        // 5 个点但只覆盖 4 天，趋势不可信
        assertNull(
            WeightPredictor.estimateDaysToTarget(
                dailyWeights(listOf(80.0, 79.6, 79.3, 79.5, 79.0)),
                currentWeight = 79.0,
                targetWeight = 70.0
            )
        )
    }

    @Test
    fun `目标体重未设置返回null`() {
        val records = dailyWeights(List(30) { 80.0 - 0.1 * it })
        assertNull(WeightPredictor.estimateDaysToTarget(records, currentWeight = 77.1, targetWeight = 0.0))
    }
}
