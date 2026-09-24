package com.example.weight.util

import kotlin.math.abs

/**
 * 目标进度计算：起始体重 → 当前体重相对目标的完成比例。
 * 从首页 GoalProgressContent 抽出，供首页与桌面小组件共用同一口径。纯函数便于单测。
 */
object GoalProgressCalculator {

    /**
     * @return 0.0~1.0 的进度；起始与目标相同（维持体重）无法衡量进度时，
     *         落在目标 ±0.05kg（显示精度内）视为达标 1.0，否则 0.0——
     *         不能按"低于目标即 1.0"的减重口径：低于/高于维持目标都算偏离
     */
    fun progress(startWeight: Double, currentWeight: Double, targetWeight: Double): Float {
        val totalRange = startWeight - targetWeight
        if (abs(totalRange) < 1e-9) {
            return if (abs(currentWeight - targetWeight) <= 0.05) 1.0f else 0.0f
        }
        val traveled = startWeight - currentWeight
        return (traveled / totalRange).toFloat().coerceIn(0.0f, 1.0f)
    }

    /** 百分比展示值；目标未设置（targetWeight<=0）返回 null 表示不展示进度 */
    fun progressPercent(startWeight: Double, currentWeight: Double, targetWeight: Double): Int? {
        if (targetWeight <= 0.0) return null
        return (progress(startWeight, currentWeight, targetWeight) * 100).toInt()
    }

    /**
     * 生效的起始体重：手动设置（>0）优先，否则回落到第一条记录的体重。
     * 两者都没有（从未记过体重且未设置）返回 null。
     */
    fun effectiveStartWeight(configuredStartWeight: Double, firstRecordWeight: Double?): Double? =
        if (configuredStartWeight > 0.0) configuredStartWeight else firstRecordWeight
}
