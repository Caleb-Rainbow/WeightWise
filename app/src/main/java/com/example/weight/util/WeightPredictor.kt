package com.example.weight.util

import com.example.weight.data.record.DailyMinWeight
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.pow

/**
 * 目标体重预测算法：对近期"每日最低体重"做指数加权线性回归，拟合出趋势斜率（kg/天），
 * 再用 剩余距离 ÷ 朝目标的有效速率 估算达成目标还需多少天。
 *
 * 相比旧的"全历史平均速率"（总减重 ÷ 总天数）方案，它更贴近近期真实状态：
 * - 指数权重（半衰期 21 天）让近期数据主导结果，早期的水分掉秤或很久前的快速减重不会永久拉高预测；
 * - 回归拟合天然平滑单日波动（水分、进食造成的噪声）；
 * - 双层趋势检查：整体斜率外还对最近 30 天单独回归复核，平台一两个月不会被较早的快速期拉出乐观天数；
 * - 同时支持减重与增重目标；趋势停滞或正在远离目标时返回 null，不给误导性数字。
 */
object WeightPredictor {

    /** 参与回归的最大时间窗口（天），取数方应按此窗口请求数据 */
    const val ANALYSIS_WINDOW_DAYS = 90L

    /** 指数权重半衰期（天）：比最新记录早 21 天的数据权重减半 */
    private const val WEIGHT_HALF_LIFE_DAYS = 21.0

    /** 参与回归的最少数据点数，样本太少趋势不可信 */
    private const val MIN_DATA_POINTS = 5

    /** 数据的最小时间跨度（天），只有几天的连续记录无法代表趋势 */
    private const val MIN_SPAN_DAYS = 7.0

    /** 近期停滞检查窗口（天）：整体斜率混合了最长 90 天的历史，需对最近这段单独回归复核 */
    private const val RECENT_CHECK_DAYS = 30.0

    /** 近期窗口内参与局部回归的最少点数，证据不足时跳过停滞检查而非否决预测 */
    private const val MIN_RECENT_POINTS = 3

    /** 近期窗口的最小时间跨度（天），与整体 [MIN_SPAN_DAYS] 同理 */
    private const val MIN_RECENT_SPAN_DAYS = 7.0

    /** 预测天数上限，超过说明当前趋势下目标遥不可及，不如不显示 */
    private const val MAX_PREDICTABLE_DAYS = 730L

    private const val MILLIS_PER_DAY = 24.0 * 60 * 60 * 1000

    /**
     * 预测距离目标体重还需多少天。
     *
     * @param dailyWeights 每日最低体重列表（按时间升序），早于最新记录 [ANALYSIS_WINDOW_DAYS] 天的数据会被自动忽略
     * @param currentWeight 当前体重，剩余距离按它计算，保证与界面展示口径一致
     * @param targetWeight 目标体重，须大于 0
     * @return 预计剩余天数（向上取整）；数据不足、趋势停滞或反向、超出可信范围时返回 null
     */
    fun estimateDaysToTarget(
        dailyWeights: List<DailyMinWeight>,
        currentWeight: Double,
        targetWeight: Double,
    ): Long? {
        if (dailyWeights.size < MIN_DATA_POINTS || targetWeight <= 0.0) return null

        // 只保留最新记录往前 ANALYSIS_WINDOW_DAYS 天内的数据
        val latestTs = dailyWeights.maxOf { it.timestamp }
        val windowStartTs = latestTs - ANALYSIS_WINDOW_DAYS * MILLIS_PER_DAY
        val xs = ArrayList<Double>(dailyWeights.size) // x=距窗口首日天数
        val ys = ArrayList<Double>(dailyWeights.size)
        for (record in dailyWeights) {
            if (record.timestamp >= windowStartTs) {
                xs.add((record.timestamp - windowStartTs) / MILLIS_PER_DAY)
                ys.add(record.minWeight)
            }
        }
        val pointCount = xs.size
        if (pointCount < MIN_DATA_POINTS) return null
        // 实际数据点的时间跨度必须足够长，连续几天的记录代表不了趋势
        if ((xs.max() - xs.min()) < MIN_SPAN_DAYS) return null

        // 指数权重：以最新记录为基准（而非当前时刻），权重只由数据间相对距离决定
        // weight = 0.5 ^ (数据年龄 / 半衰期)，即 0.5.pow((spanDays - x) / halfLife)
        val spanDays = (latestTs - windowStartTs) / MILLIS_PER_DAY

        // 权重只算一遍存数组复用（原先两遍循环各算一次 pow）
        val weights = DoubleArray(pointCount) { i -> 0.5.pow((spanDays - xs[i]) / WEIGHT_HALF_LIFE_DAYS) }

        // 加权最小二乘：先算加权均值，再累计加权协方差
        var sumW = 0.0
        var sumWX = 0.0
        var sumWY = 0.0
        for (i in 0 until pointCount) {
            val w = weights[i]
            sumW += w
            sumWX += w * xs[i]
            sumWY += w * ys[i]
        }
        val meanX = sumWX / sumW
        val meanY = sumWY / sumW

        var sxx = 0.0
        var sxy = 0.0
        for (i in 0 until pointCount) {
            val dx = xs[i] - meanX
            sxx += weights[i] * dx * dx
            sxy += weights[i] * dx * (ys[i] - meanY)
        }
        if (sxx <= 0.0) return null
        val slope = sxy / sxx // kg/天，负值表示下降

        val losingWeight = currentWeight > targetWeight
        // 按目标方向取"朝目标的有效速率"：减重看下降速率，增重看上升速率
        val towardTargetRate = if (losingWeight) -slope else slope
        val remaining = (currentWeight - targetWeight).absoluteValue
        if (remaining <= 0.0) return null
        // 速率门槛自调谐：低于 remaining / MAX_PREDICTABLE_DAYS 即按当前速率
        // 在可信范围内到不了目标，平台期（速率≈0）与方向相反（负速率）都被这道门拦下
        val minRate = remaining / MAX_PREDICTABLE_DAYS
        if (towardTargetRate < minRate) return null

        // 整体斜率混合了最长 90 天的历史，平台一两个月仍可能被较早的快速期拉出乐观天数，
        // 再对最近 RECENT_CHECK_DAYS 天单独回归：局部速率同样不达标视为停滞，不给预测
        val recentTowardRate = recentTowardRate(xs, ys, spanDays, losingWeight)
        if (recentTowardRate != null && recentTowardRate < minRate) return null

        return ceil(remaining / towardTargetRate).toLong()
    }

    /**
     * 对最近 [RECENT_CHECK_DAYS] 天的数据做普通最小二乘，返回按目标方向的有效速率。
     * 近期点数或跨度不足、无法可靠判断局部趋势时返回 null，调用方应跳过停滞检查。
     */
    private fun recentTowardRate(
        xs: List<Double>,
        ys: List<Double>,
        spanDays: Double,
        losingWeight: Boolean,
    ): Double? {
        val cutoff = spanDays - RECENT_CHECK_DAYS
        val recentXs = ArrayList<Double>(xs.size)
        val recentYs = ArrayList<Double>(ys.size)
        for (i in xs.indices) {
            if (xs[i] >= cutoff) {
                recentXs.add(xs[i])
                recentYs.add(ys[i])
            }
        }
        if (recentXs.size < MIN_RECENT_POINTS) return null
        if (recentXs.max() - recentXs.min() < MIN_RECENT_SPAN_DAYS) return null

        var meanX = 0.0
        var meanY = 0.0
        for (i in recentXs.indices) {
            meanX += recentXs[i]
            meanY += recentYs[i]
        }
        meanX /= recentXs.size
        meanY /= recentYs.size

        var sxx = 0.0
        var sxy = 0.0
        for (i in recentXs.indices) {
            val dx = recentXs[i] - meanX
            sxx += dx * dx
            sxy += dx * (recentYs[i] - meanY)
        }
        if (sxx <= 0.0) return null
        val slope = sxy / sxx
        return if (losingWeight) -slope else slope
    }
}
