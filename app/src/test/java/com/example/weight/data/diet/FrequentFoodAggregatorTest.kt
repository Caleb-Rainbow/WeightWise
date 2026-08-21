package com.example.weight.data.diet

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequentFoodAggregatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun foodJson(
        name: String,
        calories: Int = 200,
        grams: Int = 100,
        protein: Int = 0,
        carbs: Int = 0,
        fat: Int = 0,
        category: String = "主食",
        isHealthy: Boolean = true,
    ) = """[{"name":"$name","estimatedCalories":$calories,"estimatedGrams":$grams,"category":"$category","isHealthy":$isHealthy,"protein":$protein,"carbs":$carbs,"fat":$fat}]"""

    @Test
    fun `按出现频次排序取TopN`() {
        val result = FrequentFoodAggregator.topFoods(
            listOf(
                foodJson("米饭"), foodJson("米饭"), foodJson("米饭"),
                foodJson("鸡蛋"), foodJson("鸡蛋"),
                foodJson("牛肉"),
            ),
            json,
        )
        assertEquals("米饭", result[0].name)
        assertEquals("鸡蛋", result[1].name)
        assertEquals("牛肉", result[2].name)
    }

    @Test
    fun `中位数取正值样本过滤零克污染`() {
        // 3 条米饭：克数 0/200/300 → 正值样本中位数 250；热量 0/300/400 → 350
        val result = FrequentFoodAggregator.topFoods(
            listOf(
                foodJson("米饭", calories = 0, grams = 0),
                foodJson("米饭", calories = 300, grams = 200),
                foodJson("米饭", calories = 400, grams = 300),
            ),
            json,
        )
        val rice = result.single()
        assertEquals(250, rice.estimatedGrams)
        assertEquals(350, rice.estimatedCalories)
    }

    @Test
    fun `热量全为零时回退全样本中位数`() {
        val result = FrequentFoodAggregator.topFoods(
            listOf(foodJson("黄瓜", calories = 0, grams = 100), foodJson("黄瓜", calories = 0, grams = 200)),
            json,
        )
        assertEquals(0, result.single().estimatedCalories)
    }

    @Test
    fun `名字trim后分组且空名跳过`() {
        val result = FrequentFoodAggregator.topFoods(
            listOf(
                foodJson("米饭 "), foodJson(" 米饭"),
                foodJson("   "),
            ),
            json,
        )
        // 空白名跳过；trim 后同名合并为一条
        assertEquals(listOf("米饭"), result.map { it.name })
    }

    @Test
    fun `单条脏JSON跳过不影响其余聚合`() {
        val result = FrequentFoodAggregator.topFoods(
            listOf(
                """[{"name":"坏数据","estimatedCalories":"oops"}]""",
                foodJson("米饭"),
            ),
            json,
        )
        assertEquals(listOf("米饭"), result.map { it.name })
    }

    @Test
    fun `limit截断只保留前N个`() {
        val input = (1..15).map { foodJson("食物$it") }
        val result = FrequentFoodAggregator.topFoods(input, json, limit = 10)
        assertEquals(10, result.size)
    }

    @Test
    fun `空历史返回空列表`() {
        assertTrue(FrequentFoodAggregator.topFoods(emptyList(), json).isEmpty())
    }
}
