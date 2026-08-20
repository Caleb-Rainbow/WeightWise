package com.example.weight.util

import com.example.weight.data.diet.DailyCalories
import com.example.weight.data.diet.TrafficLightCount
import com.example.weight.data.record.DailyMinWeight
import kotlin.math.roundToInt

/** 周期内体重统计（口径：每日最低体重，与首页图表一致） */
data class ReportWeightStats(
    val startWeight: Double, // 期初体重（周期内第一个打卡日）
    val endWeight: Double, // 期末体重（周期内最后一个打卡日）
    val netChange: Double, // 期末 - 期初，负值表示下降
    val avgWeight: Double,
    val maxWeight: Double,
    val minWeight: Double,
    val recordedDays: Int, // 打卡天数
)

/** 周期内饮食热量统计 */
data class ReportCaloriesStats(
    val avgCalories: Int, // 有记录日的日均摄入
    val recordedDays: Int, // 有饮食记录的天数
    val daysOverTarget: Int, // 超过建议摄入的天数
    val hasTarget: Boolean, // 是否配置了建议摄入（决定超标天数是否可比）
    val greenCount: Int,
    val yellowCount: Int,
    val redCount: Int,
)

/**
 * 周期报告聚合器：把按日聚合的体重/热量数据汇总成报告统计。
 * 纯函数便于单测；输入须为同一周期内的数据（调用方负责按 periodRange 取数）。
 */
object ReportAggregator {

    /** 周期内无打卡记录返回 null，UI 据此显示空态 */
    fun weightStats(dailyWeights: List<DailyMinWeight>): ReportWeightStats? {
        if (dailyWeights.isEmpty()) return null
        val weights = dailyWeights.map { it.minWeight }
        return ReportWeightStats(
            startWeight = weights.first(),
            endWeight = weights.last(),
            netChange = weights.last() - weights.first(),
            avgWeight = weights.average(),
            maxWeight = weights.max(),
            minWeight = weights.min(),
            recordedDays = dailyWeights.size,
        )
    }

    /** 周期内「较上一期」的对比值：本期净变化 - 上期净变化；上期无数据返回 null */
    fun changeVsPrevPeriod(current: List<DailyMinWeight>, previous: List<DailyMinWeight>): Double? {
        if (current.isEmpty()) return null
        val prevNetChange = previous.takeIf { it.isNotEmpty() }
            ?.let { it.last().minWeight - it.first().minWeight }
            ?: return null
        val netChange = current.last().minWeight - current.first().minWeight
        return netChange - prevNetChange
    }

    /** 周期内无饮食记录返回 null；[recommendedIntake] 非正数视为未配置 */
    fun caloriesStats(
        dailyCalories: List<DailyCalories>,
        trafficLights: List<TrafficLightCount>,
        recommendedIntake: Int?,
    ): ReportCaloriesStats? {
        if (dailyCalories.isEmpty()) return null
        val target = recommendedIntake?.takeIf { it > 0 }
        val countOf = { name: String -> trafficLights.firstOrNull { it.trafficLight == name }?.count ?: 0 }
        return ReportCaloriesStats(
            avgCalories = dailyCalories.map { it.calories }.average().roundToInt(),
            recordedDays = dailyCalories.size,
            daysOverTarget = if (target != null) dailyCalories.count { it.calories > target } else 0,
            hasTarget = target != null,
            greenCount = countOf("GREEN"),
            yellowCount = countOf("YELLOW"),
            redCount = countOf("RED"),
        )
    }

    /** BMI = 体重 / 身高²；身高未设置（<=0）返回 null */
    fun bmi(weightKg: Double, heightCm: Double): Double? {
        if (heightCm <= 0.0) return null
        val heightM = heightCm / 100
        return weightKg / (heightM * heightM)
    }
}
