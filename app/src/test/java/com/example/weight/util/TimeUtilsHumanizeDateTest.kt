package com.example.weight.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 历史页日期人性化(OV4B/E3A)与空档日枚举 */
class TimeUtilsHumanizeDateTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 21) // 周五

    @Test
    fun `当天显示今天`() {
        assertEquals("今天", TimeUtils.humanizeDate(today, "2026-08-21"))
    }

    @Test
    fun `前一天显示昨天_跨午夜边界`() {
        assertEquals("昨天", TimeUtils.humanizeDate(today, "2026-08-20"))
    }

    @Test
    fun `更早显示月日与星期`() {
        assertEquals("8月19日 周三", TimeUtils.humanizeDate(today, "2026-08-19"))
        assertEquals("12月31日 周三", TimeUtils.humanizeDate(today, "2025-12-31"))
    }

    @Test
    fun `非法日期原样返回不抛异常`() {
        assertEquals("脏数据", TimeUtils.humanizeDate(today, "脏数据"))
    }

    @Test
    fun `近 N 天序列含今天且升序`() {
        val dates = TimeUtils.lastNDates(today, 3)
        assertEquals(listOf("2026-08-19", "2026-08-20", "2026-08-21"), dates)
    }

    @Test
    fun `近一天只含今天`() {
        assertEquals(listOf("2026-08-21"), TimeUtils.lastNDates(today, 1))
    }

    @Test
    fun `零或负天数返回空序列`() {
        assertEquals(emptyList<String>(), TimeUtils.lastNDates(today, 0))
    }
}
