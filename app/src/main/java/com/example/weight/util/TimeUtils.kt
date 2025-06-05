package com.example.weight.util

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

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

    fun getCurrentTime(): String = format1.format(Date())
    fun getCurrentDate(): String = format2.format(Date())
    fun convertTimeToMillis(time: String): Long = format1.parse(time)?.time ?: 0
    fun convertDateToMillis(date: String): Long = format2.parse(date)?.time ?: 0
    fun convertMillisToDate(millis: Long): String = format2.format(Date(millis))
    fun convertMillisToTime(millis: Long): String = format4.format(Date(millis))
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
}