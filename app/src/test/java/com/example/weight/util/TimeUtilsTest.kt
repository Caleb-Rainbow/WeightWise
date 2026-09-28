package com.example.weight.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TimeUtilsTest(private val deviceZone: String) {
    private lateinit var originalZone: TimeZone

    @Before
    fun setDeviceZone() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(deviceZone))
    }

    @After
    fun restoreDeviceZone() = TimeZone.setDefault(originalZone)

    private fun clock(instant: String = "2026-08-19T16:00:00Z"): Clock =
        Clock.fixed(Instant.parse(instant), ZoneId.systemDefault())

    private fun millis(instant: String) = Instant.parse(instant).toEpochMilli()

    @Test
    fun `北京午夜前后一毫秒归入相邻两天`() {
        val before = clock("2026-08-19T15:59:59.999Z")
        val after = clock()
        assertEquals(LocalDate.of(2026, 8, 19), TimeUtils.beijingToday(before))
        assertEquals(LocalDate.of(2026, 8, 20), TimeUtils.beijingToday(after))
        assertEquals(LocalDate.of(2026, 8, 20), TimeUtils.beijingDate(after.millis()))
    }

    @Test
    fun `设备已到明天时仍取北京今天`() {
        assertEquals(LocalDate.of(2026, 8, 20), TimeUtils.beijingToday(clock("2026-08-20T15:59:59.999Z")))
    }

    @Test
    fun `录入及编辑日期时间按北京日往返`() {
        val ts = TimeUtils.convertTimeToMillis("2026-08-20 00:30:00")
        assertEquals(millis("2026-08-19T16:30:00Z"), ts)
        assertEquals("2026-08-20", TimeUtils.beijingDate(ts).toString())
        assertEquals("00:30", TimeUtils.convertMillisToBeijingHM(ts))
        assertEquals("00:00", TimeUtils.getCurrentTimeFormat3(clock()))
        assertEquals(millis("2026-08-19T16:00:00Z"), TimeUtils.convertDateToMillis("2026-08-20"))
        assertEquals(0L, TimeUtils.convertTimeToMillis("invalid"))
        assertEquals(0L, TimeUtils.convertDateToMillis("invalid"))
    }

    @Test
    fun `日期选择器使用北京日期对应的UTC零点`() {
        val expected = millis("2026-08-20T00:00:00Z")
        assertEquals(expected, TimeUtils.getTodayUtcMillis(clock()))
        assertEquals(expected, TimeUtils.convertDateToUtcMillis("2026-08-20", clock()))
        assertEquals(expected, TimeUtils.convertDateToUtcMillis("not-a-date", clock()))
        assertEquals("2026-08-20", TimeUtils.convertUtcMillisToDate(expected))
    }

    @Test
    fun `近N天窗口从北京午夜开始且跨日滑动`() {
        assertEquals(millis("2026-08-13T16:00:00Z"), TimeUtils.getStartTimeForLastDays(7, clock()))
        assertEquals(millis("2026-08-19T16:00:00Z"), TimeUtils.getStartTimeForLastDays(1, clock()))
        assertEquals(
            millis("2026-08-12T16:00:00Z"),
            TimeUtils.getStartTimeForLastDays(7, clock("2026-08-19T15:59:59.999Z")),
        )
    }

    @Test
    fun `近N月和年从北京午夜开始并处理月末和闰年`() {
        assertEquals(millis("2026-05-19T16:00:00Z"), TimeUtils.getStartTimeForLastMonths(3, clock()))
        assertEquals(millis("2025-08-19T16:00:00Z"), TimeUtils.getStartTimeForLastYears(1, clock()))
        assertEquals(
            millis("2024-02-28T16:00:00Z"),
            TimeUtils.getStartTimeForLastMonths(1, clock("2024-03-30T16:00:00Z")),
        )
        assertEquals(
            millis("2023-02-27T16:00:00Z"),
            TimeUtils.getStartTimeForLastYears(1, clock("2024-02-28T16:00:00Z")),
        )
    }

    @Test
    fun `开始至今天的天数在北京午夜递增`() {
        assertEquals(1, TimeUtils.getDaysSince("2026-08-19", clock("2026-08-19T15:59:59.999Z")))
        assertEquals(2, TimeUtils.getDaysSince("2026-08-19", clock()))
    }

    @Test
    fun `周月年报告边界转换回北京日不会偏移`() {
        val today = TimeUtils.beijingToday(clock("2026-12-31T16:00:00Z"))
        for (type in ReportType.entries) {
            val anchor = type.anchorOf(today)
            val (start, end) = type.periodRange(anchor)
            assertEquals(anchor, TimeUtils.beijingDate(start))
            assertEquals(type.shift(anchor, 1), TimeUtils.beijingDate(end))
        }
    }

    @Test
    fun `记录时间展示仍使用设备本地时间`() {
        val ts = millis("2026-08-19T16:30:00Z")
        val expected = when (deviceZone) {
            "UTC" -> "2026-08-19 16:30"
            "America/Los_Angeles" -> "2026-08-19 09:30"
            "Pacific/Kiritimati" -> "2026-08-20 06:30"
            else -> "2026-08-20 00:30"
        }
        assertEquals(expected, TimeUtils.convertMillisToTime(ts))
        assertEquals(expected.take(10), TimeUtils.convertMillisToDate(ts))
        assertEquals(expected.takeLast(5), TimeUtils.convertMillisToHM(ts))
        assertEquals(expected.substring(8, 10), TimeUtils.convertMillisToDay(ts))
        assertEquals("08月", TimeUtils.convertMillisToMonth(ts))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "deviceZone={0}")
        fun deviceZones() = listOf("UTC", "America/Los_Angeles", "Pacific/Kiritimati", "Asia/Shanghai")
    }
}
