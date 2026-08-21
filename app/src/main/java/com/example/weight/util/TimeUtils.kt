package com.example.weight.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 时间格式化工具。全部基于不可变的 java.time 类型：
 * 旧实现共享 SimpleDateFormat 单例，主线程与 IO 线程并发调用会输出错乱日期甚至抛异常。
 * DateTimeFormatter 无状态、线程安全，行为与原 SimpleDateFormat（系统默认时区 + Locale.CHINA）保持一致。
 */
object TimeUtils {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val format1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val format2 = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
    private val format3 = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val format4 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val formatUtcDate = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA).withZone(ZoneOffset.UTC)
    private val formatDay = DateTimeFormatter.ofPattern("dd", Locale.CHINA)
    private val formatMonth = DateTimeFormatter.ofPattern("MM月", Locale.CHINA)

    private fun zdt(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone)

    fun getCurrentTime(): String = format1.format(LocalDateTime.now())
    fun getCurrentDate(): String = format2.format(LocalDate.now())

    /** 解析 "yyyy-MM-dd HH:mm:ss"；非法输入返回 0（旧实现会抛异常，这里统一兜底） */
    fun convertTimeToMillis(time: String): Long = runCatching {
        LocalDateTime.parse(time, format1).atZone(zone).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    /** 解析 "yyyy-MM-dd"（当天零点）；非法输入返回 0 */
    fun convertDateToMillis(date: String): Long = runCatching {
        LocalDate.parse(date, format2).atStartOfDay(zone).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    fun convertMillisToDate(millis: Long): String = format2.format(zdt(millis))
    fun convertMillisToTime(millis: Long): String = format4.format(zdt(millis))
    fun convertMillisToHM(millis: Long): String = format3.format(zdt(millis))
    fun convertMillisToDay(millis: Long): String = formatDay.format(zdt(millis))
    fun convertMillisToMonth(millis: Long): String = formatMonth.format(zdt(millis))

    /**
     * DatePicker 的 selectedDateMillis 以 UTC 毫秒解释，
     * 不能直接传 System.currentTimeMillis()，否则东八区 0:00-8:00 之间会被截断成昨天
     */
    fun getTodayUtcMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** 将 DatePicker 返回的 UTC 毫秒（所选日期的 UTC 零点）格式化为日期字符串 */
    fun convertUtcMillisToDate(millis: Long): String = formatUtcDate.format(Instant.ofEpochMilli(millis))

    /** 将 "yyyy-MM-dd" 日期字符串转换为 DatePicker 需要的 UTC 零点毫秒值 */
    fun convertDateToUtcMillis(date: String): Long = try {
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (e: Exception) {
        getTodayUtcMillis()
    }
    fun getCurrentTimeFormat3() = format3.format(LocalDateTime.now())

    /**
     * 计算“近 N 天”的起始时间戳（包含今天和之前的 N-1 天）
     * 返回该范围第一天的开始（午夜）。
     */
    fun getStartTimeForLastDays(days: Int): Long {
        val startDate = LocalDate.now().minusDays((days - 1).toLong())
        return startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /**
     * 计算“近 N 个月”的起始时间戳。
     * 返回该范围第一个月的开始（当天的午夜）。
     */
    fun getStartTimeForLastMonths(months: Int): Long {
        val startDate = LocalDate.now().minusMonths(months.toLong())
        return startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /**
     * 计算“近 N 年”的起始时间戳。
     * 返回该范围第一年的开始（当天的午夜）。
     */
    fun getStartTimeForLastYears(years: Int): Long {
        val startDate = LocalDate.now().minusYears(years.toLong())
        return startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun getDaysSince(startDateStr: String): Int {
        val start = LocalDate.parse(startDateStr)
        val today = LocalDate.now(ZoneId.systemDefault())
        return ChronoUnit.DAYS.between(start, today).toInt() + 1
    }

    /**
     * 历史页日期头人性化(OV4B/E3A):今天/昨天说人话,更早给「M月d日 周X」。
     * today 由调用方传入(而非内部取 now)保持纯函数可单测;解析失败原样返回
     */
    fun humanizeDate(today: LocalDate, date: String): String {
        val d = runCatching { LocalDate.parse(date, format2) }.getOrNull() ?: return date
        return when (d) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> "${d.monthValue}月${d.dayOfMonth}日 ${chineseDayOfWeek(d.dayOfWeek.value)}"
        }
    }

    /** 近 N 天日期序列(OV4B 空档日枚举),含今天,升序;days ≤ 0 返回空 */
    fun lastNDates(today: LocalDate, days: Int): List<String> {
        if (days <= 0) return emptyList()
        return (days - 1 downTo 0).map { offset ->
            format2.format(today.minusDays(offset.toLong()))
        }
    }

    private fun chineseDayOfWeek(isoValue: Int): String = when (isoValue) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
}
