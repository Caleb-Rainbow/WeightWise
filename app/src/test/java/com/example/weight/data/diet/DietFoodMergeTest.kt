package com.example.weight.data.diet

import org.junit.Assert.assertEquals
import org.junit.Test

/** mergeFoods(E1A)与 resolveTrafficLight(OV1B)的合并与评级来源判定 */
class DietFoodMergeTest {

    private fun food(name: String, healthy: Boolean = true, manual: Boolean = false) =
        RecognizedFoodItem(name = name, estimatedCalories = 100, isHealthy = healthy, isManuallyAdded = manual)

    @Test
    fun `手动项保留且排在 AI 项之前`() {
        val existing = listOf(food("苹果", manual = true), food("蛋糕", healthy = false, manual = true))
        val ai = listOf(food("鸡胸肉沙拉"))
        val merged = mergeFoods(existing, ai)
        assertEquals(listOf("苹果", "蛋糕", "鸡胸肉沙拉"), merged.map { it.name })
    }

    @Test
    fun `重新分析时旧 AI 项被整体替换_手动项跨分析存活`() {
        val existing = listOf(food("苹果", manual = true), food("旧识别项"))
        val ai = listOf(food("新识别项"))
        val merged = mergeFoods(existing, ai)
        assertEquals(listOf("苹果", "新识别项"), merged.map { it.name })
    }

    @Test
    fun `AI 空结果时仅保留现有列表`() {
        val existing = listOf(food("苹果", manual = true))
        assertEquals(existing, mergeFoods(existing, emptyList()))
    }

    @Test
    fun `食物清单与快照一致时采信 AI 评级`() {
        val foods = listOf(food("苹果"))
        assertEquals("GREEN", resolveTrafficLight(foods, ratedFoods = foods, aiTrafficLight = "GREEN"))
    }

    @Test
    fun `食物被增删改后本地重算_不用陈旧 AI 评级`() {
        val rated = listOf(food("苹果"))
        val edited = listOf(food("苹果"), food("蛋糕", healthy = false))
        // 本地规则:1/2 不健康 → RED;若误用 AI 的 GREEN 会污染报告页统计
        assertEquals("RED", resolveTrafficLight(edited, ratedFoods = rated, aiTrafficLight = "GREEN"))
    }

    @Test
    fun `无 AI 路径直接本地重算`() {
        val foods = listOf(food("蛋糕", healthy = false))
        // 1/1 不健康:1*3 >= 1 → RED
        assertEquals("RED", resolveTrafficLight(foods, ratedFoods = null, aiTrafficLight = null))
    }

    @Test
    fun `快照缺失时即便有 AI 评级也本地重算`() {
        val foods = listOf(food("蛋糕", healthy = false), food("蛋糕", healthy = false), food("蛋糕", healthy = false))
        assertEquals("RED", resolveTrafficLight(foods, ratedFoods = null, aiTrafficLight = "GREEN"))
    }
}
