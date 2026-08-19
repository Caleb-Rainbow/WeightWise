package com.example.weight.util

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeUtils {
    private val format1: SimpleDateFormat by lazy {
        SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.CHINA
        )
    }
    private val format2: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    }
    private val format3: SimpleDateFormat by lazy {
        SimpleDateFormat("HH:mm", Locale.CHINA)
    }
    private val format4: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    }
    private val formatUtcDate: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    private val formatDay: SimpleDateFormat by lazy {
        SimpleDateFormat("dd", Locale.CHINA)
    }
    private val formatMonth: SimpleDateFormat by lazy {
        SimpleDateFormat("MM月", Locale.CHINA)
    }

    fun getCurrentTime(): String = format1.format(Date())
    fun getCurrentDate(): String = format2.format(Date())
    fun convertTimeToMillis(time: String): Long = format1.parse(time)?.time ?: 0
    fun convertDateToMillis(date: String): Long = format2.parse(date)?.time ?: 0
    fun convertMillisToDate(millis: Long): String = format2.format(Date(millis))
    fun convertMillisToTime(millis: Long): String = format4.format(Date(millis))
    fun convertMillisToHM(millis: Long): String = format3.format(Date(millis))
    fun convertMillisToDay(millis: Long): String = formatDay.format(Date(millis))
    fun convertMillisToMonth(millis: Long): String = formatMonth.format(Date(millis))

    /**
     * DatePicker 的 selectedDateMillis 以 UTC 毫秒解释，
     * 不能直接传 System.currentTimeMillis()，否则东八区 0:00-8:00 之间会被截断成昨天
     */
    fun getTodayUtcMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** 将 DatePicker 返回的 UTC 毫秒（所选日期的 UTC 零点）格式化为日期字符串 */
    fun convertUtcMillisToDate(millis: Long): String = formatUtcDate.format(Date(millis))

    /** 将 "yyyy-MM-dd" 日期字符串转换为 DatePicker 需要的 UTC 零点毫秒值 */
    fun convertDateToUtcMillis(date: String): Long = try {
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (e: Exception) {
        getTodayUtcMillis()
    }
    fun getCurrentTimeFormat3() = format3.format(Date())

    /**
     * 计算“近 N 天”的起始时间戳（包含今天和之前的 N-1 天）
     * 返回该范围第一天的开始（午夜）。
     */
    fun getStartTimeForLastDays(days: Int): Long {
        val startDate = LocalDate.now().minusDays((days - 1).toLong())
        return startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * 计算“近 N 个月”的起始时间戳。
     * 返回该范围第一个月的开始（当天的午夜）。
     */
    fun getStartTimeForLastMonths(months: Int): Long {
        val startDate = LocalDate.now().minusMonths(months.toLong())
        return startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * 计算“近 N 年”的起始时间戳。
     * 返回该范围第一年的开始（当天的午夜）。
     */
    fun getStartTimeForLastYears(years: Int): Long {
        val startDate = LocalDate.now().minusYears(years.toLong())
        return startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun getDaysSince(startDateStr: String): Int {
        val start = LocalDate.parse(startDateStr)
        val today = LocalDate.now(ZoneId.systemDefault())
        return ChronoUnit.DAYS.between(start, today).toInt() + 1
    }
}