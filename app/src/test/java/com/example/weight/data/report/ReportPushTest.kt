package com.example.weight.data.report

import com.example.weight.data.record.DailyMinWeight
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportPushSchedulerTest {

    @Test
    fun `解析推送时间字符串`() {
        assertEquals(LocalTime.of(8, 0), ReportPushScheduler.parsePushTime("08:00"))
        assertEquals(LocalTime.of(21, 5), ReportPushScheduler.parsePushTime("21:05"))
    }

    @Test
    fun `非法时间回退默认时刻`() {
        assertEquals(LocalTime.of(8, 0), ReportPushScheduler.parsePushTime("bad"))
        assertEquals(LocalTime.of(8, 0), ReportPushScheduler.parsePushTime(""))
    }

    @Test
    fun `周一未到推送时刻算到今天`() {
        // 2026-08-17 是周一
        val now = LocalDateTime.of(2026, 8, 17, 6, 0)
        assertEquals(Duration.ofHours(2), ReportPushScheduler.delayUntilNextMonday(now, LocalTime.of(8, 0)))
    }

    @Test
    fun `周一已过推送时刻顺延到下周一`() {
        val now = LocalDateTime.of(2026, 8, 17, 9, 0)
        assertEquals(Duration.ofDays(7).minusHours(1), ReportPushScheduler.delayUntilNextMonday(now, LocalTime.of(8, 0)))
    }

    @Test
    fun `恰好等于推送时刻顺延到下周一`() {
        val now = LocalDateTime.of(2026, 8, 17, 8, 0)
        assertEquals(Duration.ofDays(7), ReportPushScheduler.delayUntilNextMonday(now, LocalTime.of(8, 0)))
    }

    @Test
    fun `周中非周一顺延到下周一`() {
        // 2026-08-19 是周三，下周一为 08-24
        val now = LocalDateTime.of(2026, 8, 19, 12, 0)
        assertEquals(Duration.ofDays(5), ReportPushScheduler.delayUntilNextMonday(now, LocalTime.of(12, 0)))
    }

    @Test
    fun `周日顺延到下周一`() {
        // 2026-08-23 是周日，下周一为 08-24
        val now = LocalDateTime.of(2026, 8, 23, 8, 0)
        assertEquals(Duration.ofDays(1), ReportPushScheduler.delayUntilNextMonday(now, LocalTime.of(8, 0)))
    }
}

class WeeklyReportTextBuilderTest {

    private fun day(date: String, weight: Double) = DailyMinWeight(minWeight = weight, recordDay = date, timestamp = 0L)

    @Test
    fun `上周无打卡返回null`() {
        assertNull(WeeklyReportTextBuilder.build(emptyList(), emptyList()))
    }

    @Test
    fun `基本文案包含打卡天数与净变化`() {
        val text = WeeklyReportTextBuilder.build(
            listOf(day("2026-08-10", 80.0), day("2026-08-16", 79.4)),
            emptyList(),
        )!!
        assertEquals("上周打卡 2 天，体重 -0.6kg", text)
    }

    @Test
    fun `比前一周多降`() {
        val text = WeeklyReportTextBuilder.build(
            listOf(day("2026-08-10", 80.0), day("2026-08-16", 79.0)),
            listOf(day("2026-08-03", 81.0), day("2026-08-09", 80.5)),
        )!!
        assertEquals("上周打卡 2 天，体重 -1kg，比前一周多降 0.5kg", text)
    }

    @Test
    fun `比前一周少降`() {
        val text = WeeklyReportTextBuilder.build(
            listOf(day("2026-08-10", 80.0), day("2026-08-16", 79.8)),
            listOf(day("2026-08-03", 81.0), day("2026-08-09", 79.8)),
        )!!
        assertEquals("上周打卡 2 天，体重 -0.2kg，比前一周少降 1kg", text)
    }

    @Test
    fun `与前一周持平不附加对比`() {
        val text = WeeklyReportTextBuilder.build(
            listOf(day("2026-08-10", 80.0), day("2026-08-16", 79.5)),
            listOf(day("2026-08-03", 81.0), day("2026-08-09", 80.5)),
        )!!
        assertEquals("上周打卡 2 天，体重 -0.5kg", text)
    }

    @Test
    fun `前一周无数据不附加对比`() {
        val text = WeeklyReportTextBuilder.build(
            listOf(day("2026-08-10", 80.0), day("2026-08-16", 79.4)),
            emptyList(),
        )!!
        assertEquals("上周打卡 2 天，体重 -0.6kg", text)
    }
}
