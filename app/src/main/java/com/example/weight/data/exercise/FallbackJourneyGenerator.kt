package com.example.weight.data.exercise

import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

object FallbackJourneyGenerator {

    fun generate(targetDays: Int, startWeight: Double, targetWeight: Double): List<AiPhaseDefinition> {
        val totalWeightLoss = startWeight - targetWeight

        val phase1End = (targetDays * 0.14).roundToInt().coerceAtLeast(7)
        val phase3Start = (targetDays * 0.80).roundToInt().coerceAtMost(targetDays - 6)

        return listOf(
            AiPhaseDefinition(
                name = "适应期",
                description = "建立运动习惯，以轻度日常活动为主，让身体逐步适应规律运动。",
                startDay = 1,
                endDay = phase1End,
                targetWeightLoss = (totalWeightLoss * 0.15).roundTo1Decimal(),
                focusAreas = listOf("日常活动", "轻度有氧", "拉伸"),
                difficultyLevel = 1,
                dailyCalorieDeficit = 200,
                dailyExerciseDuration = 20,
            ),
            AiPhaseDefinition(
                name = "燃脂期",
                description = "提升运动强度，以有氧运动为主，加速脂肪消耗。",
                startDay = phase1End + 1,
                endDay = phase3Start,
                targetWeightLoss = (totalWeightLoss * 0.60).roundTo1Decimal(),
                focusAreas = listOf("有氧", "徒手训练", "高强度间歇"),
                difficultyLevel = 2,
                dailyCalorieDeficit = 350,
                dailyExerciseDuration = 35,
            ),
            AiPhaseDefinition(
                name = "塑形期",
                description = "巩固成果，加入力量训练元素，提升基础代谢率。",
                startDay = phase3Start + 1,
                endDay = targetDays,
                targetWeightLoss = (totalWeightLoss * 0.25).roundTo1Decimal(),
                focusAreas = listOf("徒手训练", "有氧", "拉伸"),
                difficultyLevel = 3,
                dailyCalorieDeficit = 300,
                dailyExerciseDuration = 40,
            ),
        )
    }

    fun generatePhasesJson(phases: List<AiPhaseDefinition>): String {
        return Json.encodeToString(
            kotlinx.serialization.serializer<List<AiPhaseDefinition>>(),
            phases,
        )
    }

    private fun Double.roundTo1Decimal(): Double = (this * 10).roundToInt() / 10.0
}
