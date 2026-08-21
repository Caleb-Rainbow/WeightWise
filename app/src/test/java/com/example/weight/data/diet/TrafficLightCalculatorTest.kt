package com.example.weight.data.diet

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficLightCalculatorTest {

    private fun food(healthy: Boolean) = RecognizedFoodItem(name = "x", estimatedCalories = 100, isHealthy = healthy)

    @Test
    fun `全部健康为绿灯`() {
        assertEquals("GREEN", TrafficLightCalculator.compute(listOf(food(true), food(true))))
        assertEquals("GREEN", TrafficLightCalculator.compute(listOf(food(true))))
    }

    @Test
    fun `不健康占比达到三分之一为红灯`() {
        // 1/3 不健康：3*1 >= 3 → RED
        assertEquals("RED", TrafficLightCalculator.compute(listOf(food(false), food(true), food(true))))
        // 超过 1/3：2/3 → RED
        assertEquals("RED", TrafficLightCalculator.compute(listOf(food(false), food(false), food(true))))
    }

    @Test
    fun `不健康占比不足三分之一为黄灯`() {
        // 1/4 不健康：3*1 < 4 → YELLOW
        assertEquals("YELLOW", TrafficLightCalculator.compute(listOf(food(false), food(true), food(true), food(true))))
    }

    @Test
    fun `空列表兜底黄灯防止存入空串`() {
        assertEquals("YELLOW", TrafficLightCalculator.compute(emptyList()))
    }
}
