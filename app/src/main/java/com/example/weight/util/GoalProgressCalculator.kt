package com.example.weight.util

import kotlin.math.abs

/**
 * 目标进度计算：起始体重 → 当前体重相对目标的完成比例。
 * 从首页 GoalProgressContent 抽出，供首页与桌面小组件共用同一口径。纯函数便于单测。
 */
object GoalProgressCalculator {

    /**
     * @return 0.0~1.0 的进度；起始与目标相同无法衡量进度时，
     *         已达标返回 1，否则 0（与界面进度条语义一致）
     */
    fun progress(startWeight: Double, currentWeight: Double, targetWeight: Double): Float {
        val totalRange = startWeight - targetWeight
        if (abs(totalRange) < 1e-9) {
            return if (currentWeight <= targetWeight) 1.0f else 0.0f
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
