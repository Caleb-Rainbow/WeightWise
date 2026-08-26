package com.example.weight.data.diet

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RecognizedFoodItem 序列化跨版本兼容（T-6 配套）：
 * 旧 JSON（缺宏量键 / 缺 quality / isHealthy 布尔 / 显式 0）与新语义的双向兼容，
 * 以及新写入（encodeDefaults=false + explicitNulls=false，与 KoinModule 共享 Json 同配）不破坏旧读方。
 */
class RecognizedFoodItemJsonCompatTest {

    // 与 KoinModule.provideJson 生产配置一致：null 字段省略、默认值不写出、未知键忽略
    private val productionJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    private fun decodeOne(text: String): RecognizedFoodItem =
        productionJson.decodeFromString<List<RecognizedFoodItem>>(text).single()

    @Test
    fun `旧JSON缺宏量键反序列化为null_不再伪零`() {
        val item = decodeOne("""[{"name":"米饭","estimatedCalories":300,"estimatedGrams":200}]""")
        assertNull(item.protein)
        assertNull(item.carbs)
        assertNull(item.fat)
    }

    @Test
    fun `显式零宏量保持真零`() {
        val item = decodeOne(
            """[{"name":"零卡可乐","estimatedCalories":0,"protein":0,"carbs":0,"fat":0}]"""
        )
        assertEquals(0, item.protein)
        assertEquals(0, item.carbs)
        assertEquals(0, item.fat)
    }

    @Test
    fun `旧JSON的isHealthy布尔映射到effectiveQuality_且不升级惩罚`() {
        // false → SOMETIMES（保守），true → OFTEN
        assertEquals(FoodQuality.SOMETIMES, decodeOne("""[{"name":"a","estimatedCalories":1,"isHealthy":false}]""").effectiveQuality)
        assertEquals(FoodQuality.OFTEN, decodeOne("""[{"name":"a","estimatedCalories":1,"isHealthy":true}]""").effectiveQuality)
        // 无 isHealthy 无 quality（最老记录）：默认 true → OFTEN
        assertEquals(FoodQuality.OFTEN, decodeOne("""[{"name":"a","estimatedCalories":1}]""").effectiveQuality)
    }

    @Test
    fun `quality优先于isHealthy`() {
        // 显式 quality 与 isHealthy 冲突时 quality 胜出
        val item = decodeOne("""[{"name":"a","estimatedCalories":1,"isHealthy":false,"quality":"INDULGENT"}]""")
        assertEquals(FoodQuality.INDULGENT, item.effectiveQuality)
    }

    @Test
    fun `未知quality值整体解析失败由调用方跳过`() {
        // 非法枚举名不是静默回退而是解析异常——与脏 JSON 同路径（DailyMacroAggregator 跳过该条）
        val bad = """[{"name":"a","estimatedCalories":1,"quality":"WHATEVER"}]"""
        var thrown = false
        try {
            productionJson.decodeFromString<List<RecognizedFoodItem>>(bad)
        } catch (e: Exception) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `新写入round-trip_null宏量不写出且读回仍为null`() {
        val item = RecognizedFoodItem(
            name = "鸡蛋",
            estimatedCalories = 150,
            quality = FoodQuality.OFTEN,
            protein = 12,
            carbs = null,
            fat = null,
        )
        val encoded = productionJson.encodeToString(listOf(item))
        // carbs/fat 为 null 不落盘；isHealthy=true 是默认值不落盘（旧版本读方按缺省 true 处理）
        assertFalse(encoded.contains("carbs"))
        assertFalse(encoded.contains("isHealthy"))
        val decoded = decodeOne(encoded)
        assertEquals(12, decoded.protein)
        assertNull(decoded.carbs)
        assertEquals(FoodQuality.OFTEN, decoded.effectiveQuality)
    }

    @Test
    fun `新写入的quality对旧版本读方是未知键_被ignoreUnknownKeys吸收`() {
        val encoded = productionJson.encodeToString(
            listOf(RecognizedFoodItem(name = "a", estimatedCalories = 1, quality = FoodQuality.INDULGENT))
        )
        // 旧版本语义读取：quality 键被忽略，isHealthy 缺省 true（降级为健康——已知且可接受的自用降级）
        val oldReader = Json { ignoreUnknownKeys = true }
        val legacyView = oldReader.decodeFromString<List<RecognizedFoodItem>>(encoded).single()
        assertTrue(legacyView.isHealthy)
    }
}
