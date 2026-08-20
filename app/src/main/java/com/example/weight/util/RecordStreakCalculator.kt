package com.example.weight.util

import java.time.LocalDate

data class StreakInfo(
    /** 当前连续打卡天数；今天还没打卡时从昨天起算（不算断），再往前断档则为 0 */
    val currentStreak: Int,
    /** 历史最长连续打卡天数 */
    val longestStreak: Int,
    /** 今天是否已打卡 */
    val checkedToday: Boolean,
)

/**
 * 连续打卡计算器。输入为打卡日期字符串列表（yyyy-MM-dd，北京时间口径，可重复、无序）。
 * 规则：今天或昨天有打卡则当前 streak 有效——用户今天还没称不应当场清零；
 * 断档超过一天才归零。纯函数，today 显式传入便于单测。
 */
object RecordStreakCalculator {

    fun calculate(recordDays: List<String>, today: LocalDate): StreakInfo {
        val days = recordDays
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSortedSet()
        if (days.isEmpty()) return StreakInfo(currentStreak = 0, longestStreak = 0, checkedToday = false)

        var longest = 1
        var run = 1
        val iterator = days.iterator()
        var prev = iterator.next()
        while (iterator.hasNext()) {
            val current = iterator.next()
            run = if (current == prev.plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
            prev = current
        }

        val checkedToday = today in days
        val anchor = when {
            checkedToday -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return StreakInfo(currentStreak = 0, longestStreak = longest, checkedToday = false)
        }
        var streak = 0
        var day = anchor
        while (day in days) {
            streak++
            day = day.minusDays(1)
        }
        return StreakInfo(
            currentStreak = streak,
            longestStreak = maxOf(longest, streak),
            checkedToday = checkedToday,
        )
    }
}
