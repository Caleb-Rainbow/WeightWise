package com.example.weight.util

import kotlin.math.abs
import kotlin.math.ceil

/** 按总目标与每周变化速度计算计划时间，支持减重和增重。 */
object GoalPlanCalculator {

    /** 按用户设定的每周变化速度估算计划天数；已达成或配置非法返回 null。 */
    fun plannedDays(currentWeight: Double, targetWeight: Double, weeklyChangeKg: Double): Long? {
        if (currentWeight <= 0 || targetWeight <= 0 || weeklyChangeKg <= 0) return null
        val distance = abs(currentWeight - targetWeight)
        if (distance < 0.05) return null
        return ceil(distance / weeklyChangeKg * 7).toLong()
    }
}
