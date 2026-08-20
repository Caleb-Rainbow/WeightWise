package com.example.weight.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordStreakCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 20)

    private fun days(vararg offsetsFromToday: Long): List<String> =
        offsetsFromToday.map { today.minusDays(it).toString() }

    @Test
    fun `今天已打卡计入当前连续`() {
        val info = RecordStreakCalculator.calculate(days(0, 1, 2), today)
        assertEquals(3, info.currentStreak)
        assertTrue(info.checkedToday)
        assertEquals(3, info.longestStreak)
    }

    @Test
    fun `今天未打卡从昨天起算不算断`() {
        val info = RecordStreakCalculator.calculate(days(1, 2), today)
        assertEquals(2, info.currentStreak)
        assertFalse(info.checkedToday)
    }

    @Test
    fun `断档超过一天当前连续清零`() {
        val info = RecordStreakCalculator.calculate(days(2, 3), today)
        assertEquals(0, info.currentStreak)
        assertEquals(2, info.longestStreak)
    }

    @Test
    fun `最长连续取历史最大值`() {
        // 早期连打 5 天，近期只连打 2 天
        val info = RecordStreakCalculator.calculate(days(0, 1, 10, 11, 12, 13, 14), today)
        assertEquals(2, info.currentStreak)
        assertEquals(5, info.longestStreak)
    }

    @Test
    fun `重复与乱序输入按去重升序处理`() {
        val info = RecordStreakCalculator.calculate(listOf("2026-08-19", "2026-08-20", "2026-08-20", "2026-08-18"), today)
        assertEquals(3, info.currentStreak)
        assertEquals(3, info.longestStreak)
    }

    @Test
    fun `无记录返回全零`() {
        val info = RecordStreakCalculator.calculate(emptyList(), today)
        assertEquals(0, info.currentStreak)
        assertEquals(0, info.longestStreak)
        assertFalse(info.checkedToday)
    }

    @Test
    fun `非法日期行被忽略`() {
        val info = RecordStreakCalculator.calculate(listOf("not-a-date", today.toString(), today.minusDays(1).toString()), today)
        assertEquals(2, info.currentStreak)
    }
}
