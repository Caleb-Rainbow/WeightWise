package com.example.weight.util

import com.example.weight.data.record.DailyWeight
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

enum class TrendConfidence(val label: String) {
    LOW("可信度较低"),
    MEDIUM("可信度一般"),
    HIGH("可信度较高"),
}

enum class WeightTrendDirection(val label: String) {
    LOSING("下降"),
    STABLE("平稳"),
    GAINING("上升"),
}

enum class FluctuationDirection {
    ABOVE_TREND,
    BELOW_TREND,
}

data class IndexedTrendPoint(
    /** 与传入 DailyWeight 列表一致的横轴索引 */
    val index: Int,
    val value: Double,
)

data class ShortTermFluctuation(
    val direction: FluctuationDirection,
    /** 最新原始值 - 最新平滑趋势；正值表示高于趋势 */
    val deltaKg: Double,
)

/** 一段体重数据的可解释趋势结果；所有判断都是统计信号，不替代医学结论。 */
data class WeightTrendInsight(
    val sevenDayAverage: List<IndexedTrendPoint>,
    val smoothedTrend: List<IndexedTrendPoint>,
    val confidence: TrendConfidence,
    val confidenceReason: String,
    /** 平滑趋势的近期线性变化率，kg/周；null 表示跨度不足 */
    val weeklyRateKg: Double?,
    val direction: WeightTrendDirection,
    /** 仍有目标距离且最近至少两周近乎横盘时为 true */
    val isPlateau: Boolean,
    val fluctuation: ShortTermFluctuation?,
)

/**
 * 趋势可信度分析：将原始每日代表值、7 个自然日均值与时间感知 EWMA 分开，避免把单次水分波动当趋势。
 */
object WeightTrendAnalyzer {

    private const val DAY_MS = 24.0 * 60 * 60 * 1000
    private const val SEVEN_DAY_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    private const val FULL_WINDOW_AGE_MS = 6L * 24 * 60 * 60 * 1000
    private const val TREND_HALF_LIFE_DAYS = 3.5
    private const val RECENT_TREND_DAYS = 30.0
    private const val PLATEAU_WINDOW_DAYS = 21.0
    private const val PLATEAU_MIN_SPAN_DAYS = 14.0
    private const val PLATEAU_MAX_RATE_KG_PER_WEEK = 0.1
    private const val PLATEAU_MAX_RANGE_KG = 0.6
    private const val DIRECTION_THRESHOLD_KG_PER_WEEK = 0.1

    fun analyze(
        dailyWeights: List<DailyWeight>,
        totalDays: Int,
        targetWeight: Double = 0.0,
    ): WeightTrendInsight {
        if (dailyWeights.isEmpty()) {
            return WeightTrendInsight(
                sevenDayAverage = emptyList(),
                smoothedTrend = emptyList(),
                confidence = TrendConfidence.LOW,
                confidenceReason = "当前周期没有体重记录",
                weeklyRateKg = null,
                direction = WeightTrendDirection.STABLE,
                isPlateau = false,
                fluctuation = null,
            )
        }

        // DAO 正常返回升序；这里仍保留原始索引排序，确保纯函数可安全接收乱序测试/调用。
        val ordered = dailyWeights.withIndex().sortedBy { it.value.timestamp }
        val smoothed = exponentiallySmoothed(ordered)
        val averages = sevenCalendarDayAverage(ordered)
        val confidenceResult = confidenceOf(ordered, totalDays)
        val weeklyRate = recentWeeklyRate(ordered, smoothed)
        val direction = when {
            weeklyRate == null || abs(weeklyRate) < DIRECTION_THRESHOLD_KG_PER_WEEK -> WeightTrendDirection.STABLE
            weeklyRate < 0 -> WeightTrendDirection.LOSING
            else -> WeightTrendDirection.GAINING
        }
        val plateau = isPlateau(ordered, targetWeight, weeklyRate, confidenceResult.first)
        val fluctuation = shortTermFluctuation(ordered, smoothed)

        return WeightTrendInsight(
            sevenDayAverage = averages,
            smoothedTrend = smoothed,
            confidence = confidenceResult.first,
            confidenceReason = confidenceResult.second,
            weeklyRateKg = weeklyRate,
            direction = direction,
            isPlateau = plateau,
            fluctuation = fluctuation,
        )
    }

    /** 以前一条趋势为基线，按实际间隔调整 alpha；隔得越久，旧值影响越小。 */
    private fun exponentiallySmoothed(
        ordered: List<IndexedValue<DailyWeight>>,
    ): List<IndexedTrendPoint> {
        var trend = ordered.first().value.value
        var previousTime = ordered.first().value.timestamp
        return ordered.mapIndexed { position, indexed ->
            if (position > 0) {
                val gapDays = ((indexed.value.timestamp - previousTime) / DAY_MS).coerceAtLeast(0.25)
                val alpha = 1.0 - 0.5.pow(gapDays / TREND_HALF_LIFE_DAYS)
                trend += alpha * (indexed.value.value - trend)
                previousTime = indexed.value.timestamp
            }
            IndexedTrendPoint(indexed.index, trend)
        }
    }

    /** 只在已经覆盖完整 7 日窗口后出点；窗口内至少 3 个记录日才画，避免两点均值冒充趋势。 */
    private fun sevenCalendarDayAverage(
        ordered: List<IndexedValue<DailyWeight>>,
    ): List<IndexedTrendPoint> {
        if (ordered.size < 3) return emptyList()
        val result = ArrayList<IndexedTrendPoint>()
        var left = 0
        var sum = 0.0
        val firstTime = ordered.first().value.timestamp
        ordered.forEachIndexed { right, indexed ->
            sum += indexed.value.value
            val windowStart = indexed.value.timestamp - SEVEN_DAY_WINDOW_MS
            while (left < right && ordered[left].value.timestamp <= windowStart) {
                sum -= ordered[left].value.value
                left++
            }
            val count = right - left + 1
            if (indexed.value.timestamp - firstTime >= FULL_WINDOW_AGE_MS && count >= 3) {
                result += IndexedTrendPoint(indexed.index, sum / count)
            }
        }
        return result
    }

    private fun confidenceOf(
        ordered: List<IndexedValue<DailyWeight>>,
        requestedTotalDays: Int,
    ): Pair<TrendConfidence, String> {
        val count = ordered.size
        if (count < 4) {
            return TrendConfidence.LOW to "仅记录 $count 天，至少 4 天后再判断趋势"
        }
        val spanDays = (ordered.last().value.timestamp - ordered.first().value.timestamp) / DAY_MS
        if (spanDays < 3.0) {
            return TrendConfidence.LOW to "记录集中在 ${ceil(spanDays + 1).toInt()} 天内，时间跨度不足"
        }
        val totalDays = max(requestedTotalDays, ceil(spanDays + 1).toInt()).coerceAtLeast(1)
        val coverage = count.toDouble() / totalDays
        val maxGapDays = ordered.zipWithNext { a, b ->
            (b.value.timestamp - a.value.timestamp) / DAY_MS
        }.maxOrNull() ?: 0.0
        val requiredCoverage = if (totalDays <= 31) 0.5 else 0.25
        return if (count >= 7 && coverage >= requiredCoverage && maxGapDays <= 7.0) {
            TrendConfidence.HIGH to "记录覆盖约 ${(coverage * 100).roundToInt()}%，时间分布较连续"
        } else {
            TrendConfidence.MEDIUM to "已能判断大致方向，但记录覆盖约 ${(coverage * 100).roundToInt()}%"
        }
    }

    private fun recentWeeklyRate(
        ordered: List<IndexedValue<DailyWeight>>,
        smoothed: List<IndexedTrendPoint>,
    ): Double? {
        if (ordered.size < 2) return null
        val latestTime = ordered.last().value.timestamp
        val cutoff = latestTime - RECENT_TREND_DAYS * DAY_MS
        val samples = ordered.indices
            .filter { ordered[it].value.timestamp >= cutoff }
            .map { ordered[it].value.timestamp to smoothed[it].value }
        return linearSlopePerDay(samples)?.times(7)
    }

    private fun isPlateau(
        ordered: List<IndexedValue<DailyWeight>>,
        targetWeight: Double,
        weeklyRate: Double?,
        confidence: TrendConfidence,
    ): Boolean {
        if (targetWeight <= 0 || weeklyRate == null || confidence == TrendConfidence.LOW) return false
        if (abs(ordered.last().value.value - targetWeight) <= 0.5) return false
        val latestTime = ordered.last().value.timestamp
        val recent = ordered.filter { latestTime - it.value.timestamp <= PLATEAU_WINDOW_DAYS * DAY_MS }
        if (recent.size < 8) return false
        val spanDays = (recent.last().value.timestamp - recent.first().value.timestamp) / DAY_MS
        if (spanDays < PLATEAU_MIN_SPAN_DAYS) return false
        val range = recent.maxOf { it.value.value } - recent.minOf { it.value.value }
        return abs(weeklyRate) < PLATEAU_MAX_RATE_KG_PER_WEEK && range <= PLATEAU_MAX_RANGE_KG
    }

    private fun shortTermFluctuation(
        ordered: List<IndexedValue<DailyWeight>>,
        smoothed: List<IndexedTrendPoint>,
    ): ShortTermFluctuation? {
        if (ordered.size < 4) return null
        val latestRaw = ordered.last().value.value
        val latestTrend = smoothed.last().value
        val delta = latestRaw - latestTrend
        val threshold = max(0.7, latestTrend * 0.008)
        if (abs(delta) < threshold) return null
        return ShortTermFluctuation(
            direction = if (delta > 0) FluctuationDirection.ABOVE_TREND else FluctuationDirection.BELOW_TREND,
            deltaKg = delta,
        )
    }

    internal fun linearSlopePerDay(samples: List<Pair<Long, Double>>): Double? {
        if (samples.size < 2) return null
        val firstTime = samples.first().first
        val xs = samples.map { (it.first - firstTime) / DAY_MS }
        if ((xs.maxOrNull() ?: 0.0) - (xs.minOrNull() ?: 0.0) < 2.0) return null
        val meanX = xs.average()
        val meanY = samples.map { it.second }.average()
        var sxx = 0.0
        var sxy = 0.0
        for (i in samples.indices) {
            val dx = xs[i] - meanX
            sxx += dx * dx
            sxy += dx * (samples[i].second - meanY)
        }
        return if (sxx <= 0.0) null else sxy / sxx
    }
}
