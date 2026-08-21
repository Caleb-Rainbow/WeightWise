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

    /** 有效速率下限（kg/天），低于它视为平台期，不做预测 */
    private const val MIN_EFFECTIVE_RATE = 0.002

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

        // 按目标方向取"朝目标的有效速率"：减重看下降速率，增重看上升速率
        val towardTargetRate = if (currentWeight > targetWeight) -slope else slope
        // 速率过低（平台期）或方向相反（正在远离目标）都不预测
        if (towardTargetRate < MIN_EFFECTIVE_RATE) return null

        val remaining = (currentWeight - targetWeight).absoluteValue
        val days = remaining / towardTargetRate
        if (days > MAX_PREDICTABLE_DAYS) return null
        return ceil(days).toLong()
    }
}
