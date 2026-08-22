package com.example.weight.data.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyCompositionMetricItemsTest {

    @Test
    fun `全指标按重要性排序输出`() {
        val c = BodyComposition(
            fatRatio = 43.6, waterRatio = 41.3, muscleRatio = 53.3, impedance = 534,
            ffm = 57.5, muscleMass = 54.3, boneMass = 3.2,
            skeletalMuscleMass = 32.9, skeletalMuscleRatio = 32.3,
            proteinRatio = 10.2, subcutaneousFatRatio = 41.5,
            visceralFatLevel = 7, bodyType = "虚胖型", bodyScore = 58,
        )
        val items = c.metricItems()
        assertEquals(13, items.size)
        // 结论项在前，key 供解读弹窗反查
        assertEquals(MetricDisplay("bodyType", "体型", "虚胖型"), items[0])
        assertEquals(MetricDisplay("bodyScore", "身体得分", "58"), items[1])
        assertEquals(MetricDisplay("fatRatio", "体脂率", "43.6%"), items[2])
        assertTrue(items.any { it == MetricDisplay("visceralFatLevel", "内脏脂肪", "等级 7") })
        assertTrue(items.any { it == MetricDisplay("muscleMass", "肌肉量", "54.3 kg") })
        assertTrue(items.any { it == MetricDisplay("impedance", "阻抗", "534 Ω") })
    }

    @Test
    fun `未测项自动跳过`() {
        // 回退路径：仅体脂率 + 体型/得分
        val c = BodyComposition(fatRatio = 19.8, bodyType = "标准型", bodyScore = 92)
        val items = c.metricItems()
        assertEquals(listOf("bodyType", "bodyScore", "fatRatio"), items.map { it.key })
    }

    @Test
    fun `空成分输出空列表`() {
        assertTrue(BodyComposition().metricItems().isEmpty())
    }

    @Test
    fun `整数化数值不带小数尾零`() {
        val c = BodyComposition(boneMass = 3.0, impedance = 500)
        val items = c.metricItems()
        assertTrue(items.any { it == MetricDisplay("boneMass", "骨量", "3 kg") })
        assertTrue(items.any { it == MetricDisplay("impedance", "阻抗", "500 Ω") })
    }

    @Test
    fun `rawValueOf反查与未测返回null`() {
        val c = BodyComposition(
            fatRatio = 43.6, visceralFatLevel = 7, bodyScore = 58, bodyType = "虚胖型",
        )
        assertEquals(43.6, c.rawValueOf("fatRatio")!!, 0.001)
        assertEquals(7.0, c.rawValueOf("visceralFatLevel")!!, 0.001)
        assertEquals(58.0, c.rawValueOf("bodyScore")!!, 0.001)
        assertNull(c.rawValueOf("bodyType"))     // 文本型
        assertNull(c.rawValueOf("waterRatio"))   // 未测
        assertNull(c.rawValueOf("nonexistent"))
    }
}
