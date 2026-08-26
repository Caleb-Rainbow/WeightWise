package com.example.weight.data.diet

/**
 *@description: 红绿灯本地推导规则。与 AI 评级并存但分工明确：
 *               AI 路径保留 AI 评级；本地路径（快速添加/编辑重算/fallback）统一用本规则，
 *               保证同一来源口径一致，报告页统计不因记录来源漂移
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
object TrafficLightCalculator {

    const val GREEN = "GREEN"
    const val YELLOW = "YELLOW"
    const val RED = "RED"

    /**
     * 规则（T-6 三态推广）：INDULGENT 计 1 分、SOMETIMES 计 0.5 分、OFTEN 计 0 分。
     * 全 0 分 → GREEN；得分×3 ≥ 数量 → RED；其余 → YELLOW。
     * 全 SOMETIMES 的餐保持 RED（与旧「全不健康→RED」一致）；
     * 「1/3~半数不健康」由旧 RED 收窄为 YELLOW（旧数据 false→SOMETIMES 只算半分，粒度修正）。
     * 空列表不该走到这里（UI 层拦截「清空即删整条」），兜底返回 YELLOW 防止存入空串
     * （空串会让报告页 getTrafficLightBetween 的 COUNT 分组丢样本）。
     */
    fun compute(foods: List<RecognizedFoodItem>): String {
        if (foods.isEmpty()) return YELLOW
        val score = foods.sumOf {
            when (it.effectiveQuality) {
                FoodQuality.OFTEN -> 0.0
                FoodQuality.SOMETIMES -> 0.5
                FoodQuality.INDULGENT -> 1.0
            }
        }
        return when {
            score == 0.0 -> GREEN
            score * 3 >= foods.size -> RED
            else -> YELLOW
        }
    }
}
