package com.example.weight.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalPlanCalculatorTest {

    @Test
    fun `计划天数按每周目标速度向上取整`() {
        assertEquals(70L, GoalPlanCalculator.plannedDays(75.0, 70.0, 0.5))
        assertNull(GoalPlanCalculator.plannedDays(70.0, 70.0, 0.5))
    }
}
