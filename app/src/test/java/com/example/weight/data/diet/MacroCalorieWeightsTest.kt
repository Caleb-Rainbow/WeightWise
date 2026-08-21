package com.example.weight.data.diet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 宏量堆叠条热量占比(D4/Q3A):kcal = g×4/4/9,归一化和为 1 */
class MacroCalorieWeightsTest {

    @Test
    fun `占比按热量系数归一`() {
        // 蛋白 10g=40kcal,碳水 25g=100kcal,脂肪 10g=90kcal,总 230
        val (p, c, f) = DailyMacroAggregator.macroCalorieWeights(10, 25, 10)!!
        assertEquals(40f / 230f, p, 1e-6f)
        assertEquals(100f / 230f, c, 1e-6f)
        assertEquals(90f / 230f, f, 1e-6f)
    }

    @Test
    fun `三段占比之和为 1`() {
        val (p, c, f) = DailyMacroAggregator.macroCalorieWeights(38, 112, 22)!!
        assertEquals(1f, p + c + f, 1e-6f)
    }

    @Test
    fun `全零宏量返回 null 不渲染堆叠条`() {
        assertNull(DailyMacroAggregator.macroCalorieWeights(0, 0, 0))
    }
}
