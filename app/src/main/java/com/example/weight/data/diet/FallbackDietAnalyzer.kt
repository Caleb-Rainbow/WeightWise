package com.example.weight.data.diet

/**
 *@description: AI 不可用时的离线兜底分析器
 *@author: 杨帅林
 *@create: 2026/4/11
 **/
object FallbackDietAnalyzer {

    fun generateFallback(userInput: String, mealType: String): AiDietResponse {
        val defaultCalories = when (mealType) {
            "BREAKFAST" -> 400
            "LUNCH" -> 600
            "DINNER" -> 500
            "SNACK" -> 200
            else -> 400
        }
        return AiDietResponse(
            foods = listOf(
                RecognizedFoodItem(
                    name = userInput.ifBlank { "未知食物" },
                    estimatedCalories = defaultCalories,
                    category = "其他",
                    isHealthy = false,
                )
            ),
            totalCalories = defaultCalories,
            trafficLight = "YELLOW",
            advice = "AI 识别暂时不可用，热量为默认估算值，请手动修改。",
            adjustedDescription = "",
        )
    }
}
