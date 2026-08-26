package com.example.weight.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalPlanCalculatorTest {

    @Test
    fun `减重长目标按间隔拆成阶段且末段对齐最终目标`() {
        val stages = GoalPlanCalculator.stages(80.0, 75.0, 71.0, 3.0)

        assertEquals(listOf(77.0, 74.0, 71.0), stages.map { it.targetWeight })
        assertTrue(stages.first().reached)
        assertEquals(2, GoalPlanCalculator.nextStage(80.0, 75.0, 71.0, 3.0)?.index)
    }

    @Test
    fun `增重目标同样支持阶段拆分`() {
        val stages = GoalPlanCalculator.stages(60.0, 62.5, 67.0, 2.0)

        assertEquals(listOf(62.0, 64.0, 66.0, 67.0), stages.map { it.targetWeight })
        assertEquals(2, GoalPlanCalculator.nextStage(60.0, 62.5, 67.0, 2.0)?.index)
    }

    @Test
    fun `计划天数按每周目标速度向上取整`() {
        assertEquals(70L, GoalPlanCalculator.plannedDays(75.0, 70.0, 0.5))
        assertNull(GoalPlanCalculator.plannedDays(70.0, 70.0, 0.5))
    }
}
