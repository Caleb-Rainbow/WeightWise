package com.example.weight.util

import kotlin.math.abs
import kotlin.math.ceil

data class StageGoal(
    val index: Int,
    val total: Int,
    val targetWeight: Double,
    val reached: Boolean,
)

/** 目标拆阶段与计划时间计算；支持减重和增重方向。 */
object GoalPlanCalculator {

    fun stages(
        startWeight: Double,
        currentWeight: Double,
        targetWeight: Double,
        stageStepKg: Double,
    ): List<StageGoal> {
        if (startWeight <= 0 || targetWeight <= 0 || stageStepKg <= 0 || abs(startWeight - targetWeight) < 1e-9) {
            return emptyList()
        }
        val losing = targetWeight < startWeight
        val distance = abs(startWeight - targetWeight)
        val count = ceil(distance / stageStepKg).toInt()
        return (1..count).map { index ->
            val stepped = if (losing) startWeight - stageStepKg * index else startWeight + stageStepKg * index
            val stageTarget = if (index == count) targetWeight else stepped
            val reached = if (losing) currentWeight <= stageTarget else currentWeight >= stageTarget
            StageGoal(index, count, stageTarget, reached)
        }
    }

    fun nextStage(
        startWeight: Double,
        currentWeight: Double,
        targetWeight: Double,
        stageStepKg: Double,
    ): StageGoal? = stages(startWeight, currentWeight, targetWeight, stageStepKg)
        .firstOrNull { !it.reached }

    /** 按用户设定的每周变化速度估算计划天数；已达成或配置非法返回 null。 */
    fun plannedDays(currentWeight: Double, targetWeight: Double, weeklyChangeKg: Double): Long? {
        if (currentWeight <= 0 || targetWeight <= 0 || weeklyChangeKg <= 0) return null
        val distance = abs(currentWeight - targetWeight)
        if (distance < 0.05) return null
        return ceil(distance / weeklyChangeKg * 7).toLong()
    }
}
