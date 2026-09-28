package com.example.weight.util

import java.time.Clock
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
 * 数据归日、录入和查询窗口统一使用固定 +8；记录时间展示保留设备本地时区。
 */
object TimeUtils {
    private val displayZone: ZoneId get() = ZoneId.systemDefault()

    /**
     * 体重数据归日的固定北京时区（与 data.record.BEIJING_OFFSET 同源同口径）。
     * 打卡 streak、周报/月报窗口等要和 +8 归日的 recordDay 比对或切窗的「今天」
     * 必须用 [beijingToday] 取值：直接 LocalDate.now() 在非 +8 时区设备上会错位一天
     * （如 UTC 设备在北京 0-8 点间：称重已归"明天"，系统今天还是"昨天"→ 刚称完重打卡却显示 0 天）。
     * 展示类格式化仍走系统时区（用户本地墙钟时间）。
     */
    val BEIJING_ZONE: ZoneOffset = ZoneOffset.ofHours(8)

    fun beijingToday(clock: Clock = Clock.systemUTC()): LocalDate =
        LocalDate.now(clock.withZone(BEIJING_ZONE))

    /** 时间戳所属的北京日；供饮食查询边界和数据关联使用。 */
    fun beijingDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(BEIJING_ZONE).toLocalDate()

    private val format1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val format2 = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
    private val format3 = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val format4 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val formatUtcDate = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA).withZone(ZoneOffset.UTC)
    private val formatDay = DateTimeFormatter.ofPattern("dd", Locale.CHINA)
    private val formatMonth = DateTimeFormatter.ofPattern("MM月", Locale.CHINA)

    private fun zdt(millis: Long) = Instant.ofEpochMilli(millis).atZone(displayZone)

    // 本地墙钟格式化仅用于展示、导出文件名，不用于数据归日。
    fun getCurrentTime(): String = format1.format(LocalDateTime.now())
    /** 设备本地日期，仅用于导出文件名；数据日期使用 [beijingToday]。 */
    fun getCurrentDate(): String = format2.format(LocalDate.now())

    /** 解析录入的北京时间 "yyyy-MM-dd HH:mm:ss"；非法输入返回 0。 */
    fun convertTimeToMillis(time: String): Long = runCatching {
        LocalDateTime.parse(time, format1).atZone(BEIJING_ZONE).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    /** 解析北京日 "yyyy-MM-dd"（北京时间零点）；非法输入返回 0。 */
    fun convertDateToMillis(date: String): Long = runCatching {
        LocalDate.parse(date, format2).atStartOfDay(BEIJING_ZONE).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    fun convertMillisToDate(millis: Long): String = format2.format(zdt(millis))
    fun convertMillisToTime(millis: Long): String = format4.format(zdt(millis))
    fun convertMillisToHM(millis: Long): String = format3.format(zdt(millis))
    /** 记录编辑器的时间输入，与北京日和 [convertTimeToMillis] 配套。 */
    fun convertMillisToBeijingHM(millis: Long): String =
        format3.format(Instant.ofEpochMilli(millis).atZone(BEIJING_ZONE))
    fun convertMillisToDay(millis: Long): String = formatDay.format(zdt(millis))
    fun convertMillisToMonth(millis: Long): String = formatMonth.format(zdt(millis))

    /**
     * DatePicker 的 selectedDateMillis 以 UTC 毫秒解释，
     * 不能直接传 System.currentTimeMillis()，否则东八区 0:00-8:00 之间会被截断成昨天
     */
    fun getTodayUtcMillis(clock: Clock = Clock.systemUTC()): Long =
        beijingToday(clock).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** 将 DatePicker 返回的 UTC 毫秒（所选日期的 UTC 零点）格式化为日期字符串 */
    fun convertUtcMillisToDate(millis: Long): String = formatUtcDate.format(Instant.ofEpochMilli(millis))

    /** 将 "yyyy-MM-dd" 日期字符串转换为 DatePicker 需要的 UTC 零点毫秒值 */
    fun convertDateToUtcMillis(date: String, clock: Clock = Clock.systemUTC()): Long = try {
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (e: Exception) {
        getTodayUtcMillis(clock)
    }
    /** 新增记录的默认北京时间，与日期选择器的北京日配套。 */
    fun getCurrentTimeFormat3(clock: Clock = Clock.systemUTC()): String =
        format3.format(clock.instant().atZone(BEIJING_ZONE))

    /**
     * 计算“近 N 天”的起始时间戳（包含今天和之前的 N-1 天）
     * 返回该范围第一天的北京时间零点。
     */
    fun getStartTimeForLastDays(days: Int, clock: Clock = Clock.systemUTC()): Long {
        val startDate = beijingToday(clock).minusDays((days - 1).toLong())
        return startDate.atStartOfDay(BEIJING_ZONE).toInstant().toEpochMilli()
    }

    /**
     * 计算“近 N 个月”的起始时间戳。
     * 返回 N 个月前同一日期的北京时间零点（月末按 java.time 规则回退）。
     */
    fun getStartTimeForLastMonths(months: Int, clock: Clock = Clock.systemUTC()): Long {
        val startDate = beijingToday(clock).minusMonths(months.toLong())
        return startDate.atStartOfDay(BEIJING_ZONE).toInstant().toEpochMilli()
    }

    /**
     * 计算“近 N 年”的起始时间戳。
     * 返回 N 年前同一日期的北京时间零点（闰日按 java.time 规则回退）。
     */
    fun getStartTimeForLastYears(years: Int, clock: Clock = Clock.systemUTC()): Long {
        val startDate = beijingToday(clock).minusYears(years.toLong())
        return startDate.atStartOfDay(BEIJING_ZONE).toInstant().toEpochMilli()
    }

    fun getDaysSince(startDateStr: String, clock: Clock = Clock.systemUTC()): Int {
        val start = LocalDate.parse(startDateStr)
        val today = beijingToday(clock)
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
