package com.example.weight.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalProgressCalculatorTest {

    @Test
    fun `正常减重进度`() {
        // 80→76，目标 72：走完 4/8 = 50%
        assertEquals(0.5f, GoalProgressCalculator.progress(80.0, 76.0, 72.0))
    }

    @Test
    fun `超目标达成封顶为1`() {
        assertEquals(1.0f, GoalProgressCalculator.progress(80.0, 70.0, 72.0))
    }

    @Test
    fun `反弹超过起始压底为0`() {
        assertEquals(0.0f, GoalProgressCalculator.progress(80.0, 81.0, 72.0))
    }

    @Test
    fun `起始与目标相同无法衡量`() {
        assertEquals(1.0f, GoalProgressCalculator.progress(70.0, 69.0, 70.0))
        assertEquals(0.0f, GoalProgressCalculator.progress(70.0, 71.0, 70.0))
    }

    @Test
    fun `未设目标不展示百分比`() {
        assertNull(GoalProgressCalculator.progressPercent(80.0, 76.0, 0.0))
    }

    @Test
    fun `百分比取整`() {
        assertEquals(50, GoalProgressCalculator.progressPercent(80.0, 76.0, 72.0))
    }
}
