package com.example.weight.data.diet

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficLightCalculatorTest {

    private fun food(healthy: Boolean) = RecognizedFoodItem(name = "x", estimatedCalories = 100, isHealthy = healthy)

    private fun foodOf(quality: FoodQuality) = RecognizedFoodItem(name = "x", estimatedCalories = 100, quality = quality)

    @Test
    fun `全部健康为绿灯`() {
        assertEquals("GREEN", TrafficLightCalculator.compute(listOf(food(true), food(true))))
        assertEquals("GREEN", TrafficLightCalculator.compute(listOf(food(true))))
    }

    @Test
    fun `放纵占比达到三分之一为红灯`() {
        // 1/3 INDULGENT：score=1，1*3 >= 3 → RED（与旧「1/3 不健康→RED」严格度一致）
        assertEquals("RED", TrafficLightCalculator.compute(listOf(foodOf(FoodQuality.INDULGENT), foodOf(FoodQuality.OFTEN), foodOf(FoodQuality.OFTEN))))
        // 超过 1/3 → RED
        assertEquals("RED", TrafficLightCalculator.compute(listOf(foodOf(FoodQuality.INDULGENT), foodOf(FoodQuality.INDULGENT), foodOf(FoodQuality.OFTEN))))
    }

    @Test
    fun `偶尔吃只计半分_三分之一时为黄灯`() {
        // T-6 口径修正：旧数据 isHealthy=false 映射 SOMETIMES（半分），
        // 1/3 SOMETIMES：score=0.5，0.5*3 < 3 → YELLOW（旧规则判 RED）
        assertEquals("YELLOW", TrafficLightCalculator.compute(listOf(food(false), food(true), food(true))))
        // 2/3 SOMETIMES：score=1，1*3 >= 3 → RED（全 SOMETIMES 半分累积仍能触发红灯）
        assertEquals("RED", TrafficLightCalculator.compute(listOf(food(false), food(false), food(true))))
    }

    @Test
    fun `放纵不足三分之一为黄灯`() {
        // 1/4 INDULGENT：score=1，1*3 < 4 → YELLOW
        assertEquals("YELLOW", TrafficLightCalculator.compute(listOf(foodOf(FoodQuality.INDULGENT), foodOf(FoodQuality.OFTEN), foodOf(FoodQuality.OFTEN), foodOf(FoodQuality.OFTEN))))
    }

    @Test
    fun `半数偶尔吃为黄灯_放纵加偶尔过半为红`() {
        // 1/2 SOMETIMES：score=0.5，0.5*3=1.5 < 2 → YELLOW（旧规则 1/2 不健康判 RED）
        assertEquals("YELLOW", TrafficLightCalculator.compute(listOf(food(false), food(true))))
        // INDULGENT+SOMETIMES：score=1.5，1.5*3 >= 2 → RED
        assertEquals("RED", TrafficLightCalculator.compute(listOf(foodOf(FoodQuality.INDULGENT), foodOf(FoodQuality.SOMETIMES))))
    }

    @Test
    fun `空列表兜底黄灯防止存入空串`() {
        assertEquals("YELLOW", TrafficLightCalculator.compute(emptyList()))
    }
}
