package com.example.weight.data.diet

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMacroAggregatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun record(json: String) = DietRecord(
        date = "2026-08-21",
        timestamp = 0L,
        mealType = "LUNCH",
        recognizedFoodJson = json,
    )

    @Test
    fun `逐食物求和并标记有宏量数据`() {
        val macros = DailyMacroAggregator.aggregate(
            listOf(
                record("""[{"name":"米饭","estimatedCalories":300,"protein":6,"carbs":66,"fat":1}]"""),
                record("""[{"name":"鸡蛋","estimatedCalories":150,"protein":12,"carbs":1,"fat":10}]"""),
            ),
            json,
        )
        assertEquals(18, macros.protein)
        assertEquals(67, macros.carbs)
        assertEquals(11, macros.fat)
        assertTrue(macros.hasMacroData)
        assertEquals(0, macros.skippedRecords)
    }

    @Test
    fun `旧记录无宏量字段时聚合为零且标记无数据`() {
        val macros = DailyMacroAggregator.aggregate(
            listOf(record("""[{"name":"米饭","estimatedCalories":300,"estimatedGrams":200}]""")),
            json,
        )
        assertEquals(0, macros.protein)
        assertEquals(0, macros.carbs)
        assertEquals(0, macros.fat)
        assertFalse(macros.hasMacroData)
    }

    @Test
    fun `混合日返回部分和并保持有数据标记`() {
        val macros = DailyMacroAggregator.aggregate(
            listOf(
                record("""[{"name":"旧记录食物","estimatedCalories":300}]"""),
                record("""[{"name":"新记录食物","estimatedCalories":200,"protein":10,"carbs":20,"fat":5}]"""),
            ),
            json,
        )
        assertEquals(10, macros.protein)
        assertTrue(macros.hasMacroData)
    }

    @Test
    fun `单条脏JSON只跳过该条不灭掉整日聚合`() {
        val macros = DailyMacroAggregator.aggregate(
            listOf(
                record("""[{"name":"脏数据","estimatedCalories":"不是数字"}]"""),
                record("""[{"name":"正常食物","estimatedCalories":200,"protein":10,"carbs":20,"fat":5}]"""),
            ),
            json,
        )
        assertEquals(10, macros.protein)
        assertEquals(1, macros.skippedRecords)
    }

    @Test
    fun `空记录列表返回零值无数据`() {
        val macros = DailyMacroAggregator.aggregate(emptyList(), json)
        assertEquals(0, macros.protein)
        assertFalse(macros.hasMacroData)
        assertEquals(0, macros.skippedRecords)
    }
}
