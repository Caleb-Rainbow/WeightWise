package com.example.weight.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 报告周期模型：周报（自然周，周一起）、月报（自然月）、年报（自然年）。
 * 周期用「锚点日期」（周期第一天的 LocalDate）表示，翻页即对锚点做周期平移，
 * 与首页「近7天」式滚动窗口形成差异化。纯函数便于单测；
 * 时区沿用 ZoneId.systemDefault()，与 TimeUtils/图表取数口径一致。
 */
enum class ReportType(val label: String) {
    WEEK("周报"), MONTH("月报"), YEAR("年报");

    /** [date] 所在周期的锚点（周期第一天） */
    fun anchorOf(date: LocalDate): LocalDate = when (this) {
        WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        MONTH -> date.withDayOfMonth(1)
        YEAR -> date.withDayOfYear(1)
    }

    /** 锚点平移 [periods] 个周期（负数往回翻历史） */
    fun shift(anchor: LocalDate, periods: Long): LocalDate = when (this) {
        WEEK -> anchor.plusWeeks(periods)
        MONTH -> anchor.plusMonths(periods)
        YEAR -> anchor.plusYears(periods)
    }

    /** 是否还能往未来翻：下一周期的第一天不晚于今天所在周期的第一天 */
    fun canGoNext(anchor: LocalDate, today: LocalDate): Boolean =
        shift(anchor, 1) <= anchorOf(today)

    /** 周期标题：周报「8月17日 - 8月23日」、月报「2026年8月」、年报「2026年」 */
    fun titleOf(anchor: LocalDate): String = when (this) {
        WEEK -> {
            val start = anchor
            val end = anchor.plusDays(6)
            "${start.monthValue}月${start.dayOfMonth}日 - ${end.monthValue}月${end.dayOfMonth}日"
        }
        MONTH -> "${anchor.year}年${anchor.monthValue}月"
        YEAR -> "${anchor.year}年"
    }

    /** 周期起止毫秒：起始含、结束排他（周期结束次日的零点），供 DAO between 查询 */
    fun periodRange(anchor: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val start = anchor.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = shift(anchor, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /**
     * 周期总天数（打卡率分母）：已结束的周期为完整长度，
     * 进行中的周期只算到今天，避免「本周刚过 1 天就显示打卡 1/7」的误导
     */
    fun daysOf(anchor: LocalDate, today: LocalDate): Int {
        val endDate = minOf(shift(anchor, 1).minusDays(1), today)
        return ChronoUnit.DAYS.between(anchor, endDate).toInt() + 1
    }
}
