package com.example.weight.util

import kotlin.math.roundToInt

enum class Gender(val displayName: String) {
    MALE("男"), FEMALE("女")
}

enum class ActivityLevel(val displayName: String, val factor: Double) {
    SEDENTARY("久坐（几乎不运动）", 1.2),
    LIGHT("轻度活动（每周运动1-3次）", 1.375),
    MODERATE("中度活动（每周运动3-5次）", 1.55),
    HIGH("高度活动（每周运动6-7次）", 1.725),
    EXTREME("极高强度（体力劳动或每天训练）", 1.9),
}

/** 今日摄入相对建议摄入的状态 */
enum class IntakeStatus(val label: String) {
    /** 已摄入 ≤ 建议的 90%，今日还有充足余量 */
    ENOUGH("还有余量"),

    /** 已摄入为建议的 90%~100%，额度快用完 */
    NEAR_LIMIT("接近建议"),

    /** 已摄入超过建议摄入 */
    OVER("超出建议"),
}

/**
 * 热量计算：Mifflin-St Jeor 公式估算基础代谢（BMR），乘活动系数得每日总消耗（TDEE），
 * 再结合目标体重给出建议摄入。纯函数便于单测。
 */
object CalorieCalculator {

    /** 默认减重建议每日热量缺口（kcal），约每周减 0.5kg */
    const val CUT_CALORIES = 500

    /** 增重建议每日热量盈余（kcal） */
    const val BULK_CALORIES = 300

    /** 建议摄入的安全下限（kcal），长期低于该值容易营养不良 */
    private const val FEMALE_MIN_INTAKE = 1200
    private const val MALE_MIN_INTAKE = 1500

    private const val KCAL_PER_KG = 7000.0

    /**
     * Mifflin-St Jeor 基础代谢。任一档案缺失（性别未知/年龄未设置）或数据非法返回 null。
     */
    fun bmr(gender: Gender?, weightKg: Double, heightCm: Double, age: Int): Double? {
        if (gender == null || age <= 0 || weightKg <= 0 || heightCm <= 0) return null
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return when (gender) {
            Gender.MALE -> base + 5
            Gender.FEMALE -> base - 161
        }
    }

    /**
     * 每日建议摄入（kcal）：BMR × 活动系数得 TDEE，再按目标方向调整——
     * 减重（目标低于当前 0.5kg 以上）减 [CUT_CALORIES]，增重（目标高于当前 0.5kg 以上）加
     * [BULK_CALORIES]，维持或未设目标即 TDEE；最后做安全下限保护。
     * 档案不全或无体重记录返回 null，界面据此展示降级形态。
     */
    fun recommendedIntake(
        gender: Gender?,
        weightKg: Double,
        heightCm: Double,
        age: Int,
        activityLevel: ActivityLevel?,
        targetWeightKg: Double,
        weeklyTargetChangeKg: Double = 0.5,
    ): Int? {
        val bmr = bmr(gender, weightKg, heightCm, age) ?: return null
        if (activityLevel == null) return null
        val tdee = bmr * activityLevel.factor
        val dailyDeficit = (weeklyTargetChangeKg.coerceIn(0.1, 1.0) * KCAL_PER_KG / 7.0).roundToInt()
        val adjusted = when {
            targetWeightKg > 0 && targetWeightKg < weightKg - 0.5 -> tdee - dailyDeficit
            targetWeightKg > 0 && targetWeightKg > weightKg + 0.5 -> tdee + BULK_CALORIES
            else -> tdee
        }
        val minIntake = if (gender == Gender.MALE) MALE_MIN_INTAKE else FEMALE_MIN_INTAKE
        return maxOf(adjusted, minIntake.toDouble()).roundToInt()
    }

    /**
     * 今日摄入状态：≤建议的 90% 为 [IntakeStatus.ENOUGH]，90%~100% 为
     * [IntakeStatus.NEAR_LIMIT]，超过 100% 为 [IntakeStatus.OVER]。
     */
    fun intakeStatus(intake: Int, recommended: Int): IntakeStatus {
        if (recommended <= 0) return IntakeStatus.ENOUGH
        val ratio = intake.toDouble() / recommended
        return when {
            ratio > 1.0 -> IntakeStatus.OVER
            ratio > 0.9 -> IntakeStatus.NEAR_LIMIT
            else -> IntakeStatus.ENOUGH
        }
    }
}
