package com.example.weight.data.diet

/**
 *@description: AI 不可用时的离线兜底分析器。
 *               红绿灯走 TrafficLightCalculator 本地推导（评审决策 #25）：
 *               兜底食物标记 isHealthy=false，旧实现写死 YELLOW 与之自相矛盾且污染报告统计
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
        val foods = listOf(
            RecognizedFoodItem(
                name = userInput.ifBlank { "未知食物" },
                estimatedCalories = defaultCalories,
                category = "其他",
                isHealthy = false,
            )
        )
        return AiDietResponse(
            foods = foods,
            totalCalories = defaultCalories,
            trafficLight = TrafficLightCalculator.compute(foods),
            advice = "AI 识别暂时不可用，热量为默认估算值，请手动修改。",
            adjustedDescription = "",
        )
    }
}
