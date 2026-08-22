package com.example.weight.data.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 指标解读中心的状态判定锚点，含本机实测案例（男 102kg：体脂 43.6 / 阻抗 534Ω 派生指标）。
 */
class MetricGuideTest {

    @Test
    fun `体脂率男性分带`() {
        // 男 10-20 标准 / 20-25 偏高 / >25 过高
        assertEquals(MetricGuide.Status.NORMAL, MetricGuide.info("fatRatio", 15.0, true)!!.status)
        assertEquals(MetricGuide.Status.HIGH, MetricGuide.info("fatRatio", 23.0, true)!!.status)
        assertEquals(MetricGuide.Status.VERY_HIGH, MetricGuide.info("fatRatio", 43.6, true)!!.status)
        assertEquals(MetricGuide.Status.LOW, MetricGuide.info("fatRatio", 8.0, true)!!.status)
    }

    @Test
    fun `体脂率女性带平移`() {
        assertEquals(MetricGuide.Status.NORMAL, MetricGuide.info("fatRatio", 24.0, false)!!.status)
        assertEquals(MetricGuide.Status.VERY_HIGH, MetricGuide.info("fatRatio", 43.6, false)!!.status)
    }

    @Test
    fun `内脏脂肪分级`() {
        assertEquals(MetricGuide.Status.NORMAL, MetricGuide.info("visceralFatLevel", 7.0, true)!!.status)
        assertEquals(MetricGuide.Status.HIGH, MetricGuide.info("visceralFatLevel", 12.0, true)!!.status)
        assertEquals(MetricGuide.Status.VERY_HIGH, MetricGuide.info("visceralFatLevel", 16.0, true)!!.status)
    }

    @Test
    fun `水分率骨骼肌率蛋白率分带`() {
        assertEquals(MetricGuide.Status.LOW, MetricGuide.info("waterRatio", 41.3, true)!!.status)
        assertEquals(MetricGuide.Status.NORMAL, MetricGuide.info("waterRatio", 55.0, true)!!.status)
        assertEquals(MetricGuide.Status.LOW, MetricGuide.info("skeletalMuscleRatio", 32.3, true)!!.status)
        assertEquals(MetricGuide.Status.NORMAL, MetricGuide.info("skeletalMuscleRatio", 45.0, true)!!.status)
        assertEquals(MetricGuide.Status.NORMAL, MetricGuide.info("proteinRatio", 10.2, true)!!.status)
    }

    @Test
    fun `身体得分分段`() {
        assertEquals(MetricGuide.Status.LOW, MetricGuide.info("bodyScore", 58.0, true)!!.status)
        assertEquals(MetricGuide.Status.HIGH, MetricGuide.info("bodyScore", 75.0, true)!!.status)
        assertEquals(MetricGuide.Status.NORMAL, MetricGuide.info("bodyScore", 85.0, true)!!.status)
    }

    @Test
    fun `量类指标无状态与刻度条仅说明`() {
        val muscle = MetricGuide.info("muscleMass", 54.3, true)!!
        assertNull(muscle.status)
        assertNull(muscle.bar)
        assertNotNull(muscle.description)
        val impedance = MetricGuide.info("impedance", 534.0, true)!!
        assertNull(impedance.status)
        val bodyType = MetricGuide.info("bodyType", null, true)!!
        assertNull(bodyType.status)
    }

    @Test
    fun `数值型指标带刻度条且当前值在条内`() {
        val bar = MetricGuide.info("fatRatio", 43.6, true)!!.bar!!
        assertEquals(0.0, bar.min, 0.001)
        assertEquals(50.0, bar.max, 0.001)
        assertEquals(43.6, bar.value, 0.001)
        // 分段连续覆盖 [min,max] 且含四级状态
        assertEquals(0.0, bar.segments.first().start, 0.001)
        assertEquals(50.0, bar.segments.last().end, 0.001)
        assertEquals(MetricGuide.Status.LOW, bar.segments[0].status)
        assertEquals(MetricGuide.Status.NORMAL, bar.segments[1].status)
        assertEquals(MetricGuide.Status.HIGH, bar.segments[2].status)
        assertEquals(MetricGuide.Status.VERY_HIGH, bar.segments[3].status)
        // 正常段推导属性
        assertEquals(10.0, bar.normalStart, 0.001)
        assertEquals(20.0, bar.normalEnd, 0.001)
    }

    @Test
    fun `内脏脂肪无偏低段零宽段被过滤`() {
        val bar = MetricGuide.info("visceralFatLevel", 7.0, true)!!.bar!!
        // 1-9 标准 / 9-14 偏高 / 14-20 过高：无 [1,1) 空偏低段
        assertEquals(listOf(1.0, 9.0, 14.0, 20.0), bar.segments.flatMap { listOf(it.start, it.end) }.distinct())
        assertEquals(MetricGuide.Status.NORMAL, bar.segments.first().status)
    }

    @Test
    fun `身体得分正常段直达上界无空高段`() {
        val bar = MetricGuide.info("bodyScore", 75.0, true)!!.bar!!
        assertEquals(100.0, bar.segments.last().end, 0.001)
        assertEquals(MetricGuide.Status.NORMAL, bar.segments.last().status)
    }

    @Test
    fun `未知key与null数值返回null或无条`() {
        assertNull(MetricGuide.info("nonexistent", 1.0, true))
        // fatRatio 缺数值：无解读（网格里该卡不会出现，防御）
        assertNull(MetricGuide.info("fatRatio", null, true))
    }
}
