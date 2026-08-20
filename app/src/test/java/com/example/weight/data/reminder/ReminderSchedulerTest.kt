package com.example.weight.data.reminder

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSchedulerTest {

    @Test
    fun `解析提醒时间字符串`() {
        assertEquals(LocalTime.of(7, 30), ReminderScheduler.parseReminderTime("07:30"))
        assertEquals(LocalTime.of(23, 5), ReminderScheduler.parseReminderTime("23:05"))
    }

    @Test
    fun `非法时间回退默认时刻`() {
        assertEquals(LocalTime.of(7, 30), ReminderScheduler.parseReminderTime("bad"))
        assertEquals(LocalTime.of(7, 30), ReminderScheduler.parseReminderTime(""))
    }

    @Test
    fun `未到提醒时刻算到今天`() {
        val now = LocalDateTime.of(2026, 8, 20, 6, 0)
        val delay = ReminderScheduler.delayUntilNext(now, LocalTime.of(7, 30))
        assertEquals(Duration.ofMinutes(90), delay)
    }

    @Test
    fun `已过提醒时刻顺延到明天`() {
        val now = LocalDateTime.of(2026, 8, 20, 8, 0)
        val delay = ReminderScheduler.delayUntilNext(now, LocalTime.of(7, 30))
        assertEquals(Duration.ofHours(23) + Duration.ofMinutes(30), delay)
    }

    @Test
    fun `恰好等于提醒时刻顺延到明天`() {
        val now = LocalDateTime.of(2026, 8, 20, 7, 30)
        val delay = ReminderScheduler.delayUntilNext(now, LocalTime.of(7, 30))
        assertEquals(Duration.ofHours(24), delay)
    }
}
