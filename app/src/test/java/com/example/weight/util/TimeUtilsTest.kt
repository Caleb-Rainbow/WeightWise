package com.example.weight.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun `时间字符串与毫秒互转保持一致`() {
        val source = "2026-08-20 08:30:00"
        assertEquals("2026-08-20 08:30", TimeUtils.convertMillisToTime(TimeUtils.convertTimeToMillis(source)))
    }

    @Test
    fun `日期字符串转换为 UTC 零点毫秒`() {
        val expected = LocalDate.parse("2026-08-20")
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expected, TimeUtils.convertDateToUtcMillis("2026-08-20"))
        assertEquals("2026-08-20", TimeUtils.convertUtcMillisToDate(expected))
    }

    @Test
    fun `非法日期字符串回退为今天`() {
        val expected = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expected, TimeUtils.convertDateToUtcMillis("not-a-date"))
    }

    @Test
    fun `近N天起点是本地午夜`() {
        val start = TimeUtils.getStartTimeForLastDays(7)
        val expectedDate = LocalDate.now().minusDays(6)
        val actualDate = java.time.Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(expectedDate, actualDate)
        // 起点必须是那天的 0 点，避免把前一天晚上的记录算进范围
        assertEquals(0, java.time.Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay())
    }

    @Test
    fun `近N月起点是整月前`() {
        val start = TimeUtils.getStartTimeForLastMonths(3)
        val expected = LocalDate.now().minusMonths(3)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, start)
    }
}
