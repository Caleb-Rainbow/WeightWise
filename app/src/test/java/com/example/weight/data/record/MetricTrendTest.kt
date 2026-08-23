package com.example.weight.data.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 成分趋势指标中心与每日聚合口径测试。
 * 时间戳锚点用北京时间的显式构造，覆盖午夜边界（与 DAO `DATE(..., '+8 hours')` 分区同口径）。
 */
class MetricTrendTest {

    private fun beijingMillis(date: String, hour: Int, minute: Int): Long =
        LocalDateTime.parse("${date}T${"%02d".format(hour)}:${"%02d".format(minute)}:00")
            .toInstant(ZoneOffset.ofHours(8)).toEpochMilli()

    private fun raw(ts: Long, composition: BodyComposition) =
        RecordCompositionRaw(ts, BodyCompositionJson.encode(composition))

    private val sample = BodyComposition(
        fatRatio = 43.6, waterRatio = 41.3, muscleMass = 55.2,
        skeletalMuscleMass = 38.4, skeletalMuscleRatio = 37.6, proteinRatio = 10.2,
        subcutaneousFatRatio = 45.1, visceralFatLevel = 13, impedance = 527,
        ffm = 57.5, boneMass = 3.2, bodyScore = 55, bodyType = "虚胖型",
    )

    @Test
    fun `同日多次测量取最后一条`() {
        val morning = sample.copy(fatRatio = 44.5)
        val evening = sample.copy(fatRatio = 43.6)
        val points = dailyLastCompositions(
            listOf(
                raw(beijingMillis("2026-08-20", 8, 0), morning),
                raw(beijingMillis("2026-08-20", 21, 30), evening),
            )
        )
        assertEquals(1, points.size)
        assertEquals("2026-08-20", points[0].day)
        assertEquals(43.6, points[0].composition.fatRatio, 0.001)
    }

    @Test
    fun `跨日记录每日聚合成升序序列`() {
        val points = dailyLastCompositions(
            listOf(
                raw(beijingMillis("2026-08-18", 8, 0), sample),
                raw(beijingMillis("2026-08-19", 8, 0), sample),
                raw(beijingMillis("2026-08-21", 8, 0), sample),
            )
        )
        assertEquals(listOf("2026-08-18", "2026-08-19", "2026-08-21"), points.map { it.day })
    }

    @Test
    fun `坏JSON与全零成分记录跳过`() {
        val points = dailyLastCompositions(
            listOf(
                RecordCompositionRaw(beijingMillis("2026-08-18", 8, 0), "{oops"),
                RecordCompositionRaw(beijingMillis("2026-08-19", 8, 0), ""),
                raw(beijingMillis("2026-08-19", 9, 0), BodyComposition()), // 全零 hasAny=false
                raw(beijingMillis("2026-08-20", 8, 0), sample),
            )
        )
        assertEquals(listOf("2026-08-20"), points.map { it.day })
    }

    @Test
    fun `北京时间午夜边界分属两天`() {
        // 23:59 与次日 00:01 在 +8 口径下必须分成两个数据点（UTC 日期仍是同一天）
        val points = dailyLastCompositions(
            listOf(
                raw(beijingMillis("2026-08-20", 23, 59), sample),
                raw(beijingMillis("2026-08-21", 0, 1), sample),
            )
        )
        assertEquals(listOf("2026-08-20", "2026-08-21"), points.map { it.day })
    }

    @Test
    fun `空列表返回空序列`() {
        assertEquals(emptyList<MetricPoint>(), dailyLastCompositions(emptyList()))
    }

    @Test
    fun `率类与整数类指标提取`() {
        assertEquals(43.6, TrendMetric.FAT_RATIO.valueOf(sample)!!, 0.001)
        assertEquals(13.0, TrendMetric.VISCERAL_FAT.valueOf(sample)!!, 0.001)
        assertEquals(527.0, TrendMetric.IMPEDANCE.valueOf(sample)!!, 0.001)
        assertEquals(55.0, TrendMetric.BODY_SCORE.valueOf(sample)!!, 0.001)
        assertEquals(55.2, TrendMetric.MUSCLE_MASS.valueOf(sample)!!, 0.001)
        assertEquals(3.2, TrendMetric.BONE.valueOf(sample)!!, 0.001)
    }

    @Test
    fun `未测指标返回null`() {
        // 全零成分：所有指标均未测
        TrendMetric.entries.forEach { assertNull(it.valueOf(BodyComposition())) }
        // 旧记录仅存阻抗（公式失效回退路径）：阻抗有值、其余未测
        val impedanceOnly = BodyComposition(impedance = 500)
        assertEquals(500.0, TrendMetric.IMPEDANCE.valueOf(impedanceOnly)!!, 0.001)
        assertNull(TrendMetric.FAT_RATIO.valueOf(impedanceOnly))
        assertNull(TrendMetric.WATER.valueOf(impedanceOnly))
    }

    @Test
    fun `趋势指标集合与网格数值项对齐`() {
        // 体型是文本判定无趋势意义，排除后其余 12 项一一对应
        val gridNumericKeys = sample.metricItems().map { it.key }.filter { it != "bodyType" }
        assertEquals(gridNumericKeys, TrendMetric.entries.map { it.key })
        assertEquals(12, TrendMetric.entries.size)
        TrendMetric.entries.forEach { assertEquals(it, TrendMetric.fromKey(it.key)) }
        assertNull(TrendMetric.fromKey("bodyType"))
    }

    @Test
    fun `数值格式化按指标小数位`() {
        assertEquals("43.6", TrendMetric.FAT_RATIO.formatValue(43.6))
        assertEquals("13", TrendMetric.VISCERAL_FAT.formatValue(13.0))
        assertEquals("527", TrendMetric.IMPEDANCE.formatValue(527.0))
        assertEquals("3.2", TrendMetric.BONE.formatValue(3.2))
    }
}
