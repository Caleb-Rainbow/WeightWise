package com.example.weight.data.record

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DailyWeightAggregator 四口径聚合回归：跨 UTC 日界按北京日分组、
 * 各口径取值规则、AVG 代表时刻、非法口径回退。
 */
class DailyWeightAggregatorTest {

    /** 北京时刻 → epoch millis；固定 +8 偏移，不依赖运行环境时区 */
    private fun beijing(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 8, day, hour, minute)
            .toInstant(ZoneOffset.ofHours(8))
            .toEpochMilli()

    /** 8-20 有三次称重（07:00=76.0、12:00=74.0、23:30=74.5），其中 23:30 与次日 07:00 各跨一次 UTC 日界 */
    private val rows = listOf(
        RecordWeightRaw(beijing(19, 7, 0), 75.0),
        RecordWeightRaw(beijing(20, 7, 0), 76.0),
        RecordWeightRaw(beijing(20, 12, 0), 74.0),
        RecordWeightRaw(beijing(20, 23, 30), 74.5),
        RecordWeightRaw(beijing(21, 7, 0), 73.0),
    )

    @Test
    fun `跨UTC日界按北京日分组`() {
        val days = DailyWeightAggregator.aggregate(rows, DailyStatMode.MIN)

        assertEquals(listOf("2026-08-19", "2026-08-20", "2026-08-21"), days.map { it.recordDay })
        assertEquals(days.sortedBy { it.recordDay }, days) // 按日升序
    }

    @Test
    fun `MIN口径取每日最低`() {
        val days = DailyWeightAggregator.aggregate(rows, DailyStatMode.MIN)

        assertEquals(listOf(75.0, 74.0, 73.0), days.map { it.value })
    }

    @Test
    fun `MIN口径同重取较早时间戳`() {
        val same = listOf(
            RecordWeightRaw(beijing(20, 12, 0), 75.0),
            RecordWeightRaw(beijing(20, 7, 0), 75.0),
        )

        val days = DailyWeightAggregator.aggregate(same, DailyStatMode.MIN)

        assertEquals(1, days.size)
        assertEquals(beijing(20, 7, 0), days[0].timestamp)
    }

    @Test
    fun `MORNING_FIRST口径取每日首条`() {
        val days = DailyWeightAggregator.aggregate(rows, DailyStatMode.MORNING_FIRST)

        assertEquals(listOf(75.0, 76.0, 73.0), days.map { it.value })
        assertEquals(beijing(20, 7, 0), days[1].timestamp)
    }

    @Test
    fun `LAST口径取每日末条`() {
        val days = DailyWeightAggregator.aggregate(rows, DailyStatMode.LAST)

        assertEquals(listOf(75.0, 74.5, 73.0), days.map { it.value })
        assertEquals(beijing(20, 23, 30), days[1].timestamp)
    }

    @Test
    fun `AVG口径取均值且代表时刻为末条`() {
        val days = DailyWeightAggregator.aggregate(rows, DailyStatMode.AVG)

        assertEquals((76.0 + 74.0 + 74.5) / 3, days[1].value, 1e-9)
        assertEquals(beijing(20, 23, 30), days[1].timestamp)
    }

    @Test
    fun `单条日与空输入`() {
        val single = listOf(RecordWeightRaw(beijing(19, 7, 0), 75.0))
        DailyStatMode.entries.forEach { mode ->
            val days = DailyWeightAggregator.aggregate(single, mode)
            assertEquals(1, days.size)
            assertEquals(75.0, days[0].value, 1e-9)
        }
        DailyStatMode.entries.forEach { mode ->
            assertEquals(emptyList<DailyWeight>(), DailyWeightAggregator.aggregate(emptyList(), mode))
        }
    }

    @Test
    fun `fromId非法值回退MIN`() {
        assertEquals(DailyStatMode.MIN, DailyStatMode.fromId("NOT_A_MODE"))
        assertEquals(DailyStatMode.AVG, DailyStatMode.fromId("AVG"))
    }
}
