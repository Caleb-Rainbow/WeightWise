package com.example.weight.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportTypeTest {

    private val wednesday = LocalDate.of(2026, 8, 19) // 2026-08-19 是周三

    @Test
    fun `周报锚点为所在周周一`() {
        assertEquals(LocalDate.of(2026, 8, 17), ReportType.WEEK.anchorOf(wednesday))
        // 周日属于上一周（周一为一周开始）
        val sunday = LocalDate.of(2026, 8, 23)
        assertEquals(LocalDate.of(2026, 8, 17), ReportType.WEEK.anchorOf(sunday))
        // 周一当天锚点即自身
        val monday = LocalDate.of(2026, 8, 17)
        assertEquals(monday, ReportType.WEEK.anchorOf(monday))
    }

    @Test
    fun `月报年报锚点为周期第一天`() {
        assertEquals(LocalDate.of(2026, 8, 1), ReportType.MONTH.anchorOf(wednesday))
        assertEquals(LocalDate.of(2026, 1, 1), ReportType.YEAR.anchorOf(wednesday))
    }

    @Test
    fun `翻页平移跨月跨年`() {
        assertEquals(LocalDate.of(2026, 8, 10), ReportType.WEEK.shift(LocalDate.of(2026, 8, 17), -1))
        assertEquals(LocalDate.of(2026, 7, 1), ReportType.MONTH.shift(LocalDate.of(2026, 8, 1), -1))
        assertEquals(LocalDate.of(2027, 1, 1), ReportType.YEAR.shift(LocalDate.of(2026, 1, 1), 1))
    }

    @Test
    fun `进行中周期不可再往未来翻`() {
        // 本周锚点：周中翻下周不可，翻上周可
        val anchor = ReportType.WEEK.anchorOf(wednesday)
        assertFalse(ReportType.WEEK.canGoNext(anchor, wednesday))
        assertTrue(ReportType.WEEK.canGoNext(ReportType.WEEK.shift(anchor, -1), wednesday))
        // 月初当天所在月已可翻到下月（锚点+1 是未来）——next 月锚点必然 > 本月锚点
        assertFalse(ReportType.MONTH.canGoNext(ReportType.MONTH.anchorOf(wednesday), wednesday))
    }

    @Test
    fun `周期标题格式`() {
        assertEquals("8月17日 - 8月23日", ReportType.WEEK.titleOf(LocalDate.of(2026, 8, 17)))
        // 跨月的周
        assertEquals("8月31日 - 9月6日", ReportType.WEEK.titleOf(LocalDate.of(2026, 8, 31)))
        assertEquals("2026年8月", ReportType.MONTH.titleOf(LocalDate.of(2026, 8, 1)))
        assertEquals("2026年", ReportType.YEAR.titleOf(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `周期总天数完整与进行中`() {
        // 已结束的完整周为 7 天
        assertEquals(7, ReportType.WEEK.daysOf(LocalDate.of(2026, 8, 10), wednesday))
        // 进行中的周（8-17 开始，今天 8-19）只算 3 天
        assertEquals(3, ReportType.WEEK.daysOf(LocalDate.of(2026, 8, 17), wednesday))
        // 已结束的二月：2026 年 2 月 28 天
        assertEquals(28, ReportType.MONTH.daysOf(LocalDate.of(2026, 2, 1), wednesday))
        // 完整年 365 天（2025 全部结束）
        assertEquals(365, ReportType.YEAR.daysOf(LocalDate.of(2025, 1, 1), wednesday))
    }
}
