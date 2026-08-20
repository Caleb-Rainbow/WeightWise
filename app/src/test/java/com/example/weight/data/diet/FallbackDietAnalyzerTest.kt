package com.example.weight.data.diet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FallbackDietAnalyzerTest {

    @Test
    fun `各餐型使用对应默认热量`() {
        mapOf(
            "BREAKFAST" to 400,
            "LUNCH" to 600,
            "DINNER" to 500,
            "SNACK" to 200,
        ).forEach { (mealType, calories) ->
            assertEquals(calories, FallbackDietAnalyzer.generateFallback("牛肉面", mealType).totalCalories)
        }
    }

    @Test
    fun `未知餐型按早餐热量兜底`() {
        assertEquals(400, FallbackDietAnalyzer.generateFallback("牛奶", "UNKNOWN").totalCalories)
    }

    @Test
    fun `空输入的食物名为未知食物`() {
        val response = FallbackDietAnalyzer.generateFallback("", "LUNCH")
        assertEquals("未知食物", response.foods.single().name)
    }

    @Test
    fun `兜底结果标记为黄灯且不可直接信任`() {
        val response = FallbackDietAnalyzer.generateFallback("一碗牛肉面", "LUNCH")
        assertEquals("YELLOW", response.trafficLight)
        assertFalse(response.foods.single().isHealthy)
        assertEquals(response.foods.sumOf { it.estimatedCalories }, response.totalCalories)
    }
}
