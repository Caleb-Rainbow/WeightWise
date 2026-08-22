package com.example.weight.data.record

import org.junit.Assert.assertEquals
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
        // 结论项在前
        assertEquals("体型" to "虚胖型", items[0])
        assertEquals("身体得分" to "58", items[1])
        assertEquals("体脂率" to "43.6%", items[2])
        // 数值项逐个可寻
        assertTrue("内脏脂肪" to "等级 7" in items)
        assertTrue("肌肉量" to "54.3 kg" in items)
        assertTrue("骨量" to "3.2 kg" in items)
        assertTrue("阻抗" to "534 Ω" in items)
    }

    @Test
    fun `未测项自动跳过`() {
        // 回退路径：仅体脂率 + 体型/得分
        val c = BodyComposition(fatRatio = 19.8, bodyType = "标准型", bodyScore = 92)
        val items = c.metricItems()
        assertEquals(listOf("体型", "身体得分", "体脂率"), items.map { it.first })
    }

    @Test
    fun `空成分输出空列表`() {
        assertTrue(BodyComposition().metricItems().isEmpty())
    }

    @Test
    fun `整数化数值不带小数尾零`() {
        val c = BodyComposition(boneMass = 3.0, impedance = 500)
        val items = c.metricItems()
        assertTrue("骨量" to "3 kg" in items)
        assertTrue("阻抗" to "500 Ω" in items)
    }
}
