package com.example.weight.data.diet

/**
 *@description: 手动添加与 AI 识别结果的合并及评级来源判定。
 *               合并只发生在 applyAiResponse 写回状态时(不在组合期),
 *               保证后续 updateFoodItem(index) 的 index 与展示列表一致(OV2)
 *@author: 杨帅林
 *@create: 2026/8/21
 **/

/**
 * E1A:手动项(isManuallyAdded)保留且排在最前,AI 识别项原序跟随。
 * 重新分析时旧 AI 项被新结果整体替换,手动项跨分析存活。
 */
fun mergeFoods(
    existing: List<RecognizedFoodItem>,
    aiFoods: List<RecognizedFoodItem>,
): List<RecognizedFoodItem> {
    if (aiFoods.isEmpty()) return existing
    val manual = existing.filter { it.isManuallyAdded }
    return manual + aiFoods
}

/**
 * OV1B:食物清单与 AI 评级时的快照一致才采信 AI 评级;用户增删改过则本地重算,
 * 杜绝「AI 评级盖在改动后的食物上」的脏数据污染报告页统计。
 * 无 AI 路径(纯快速添加)ratedFoods/aiTrafficLight 均为空,直接本地重算。
 * AI 值在此归一:非法值(小写/未知词)一律不采信,走本地重算兜底。
 */
fun resolveTrafficLight(
    foods: List<RecognizedFoodItem>,
    ratedFoods: List<RecognizedFoodItem>?,
    aiTrafficLight: String?,
): String {
    val normalized = aiTrafficLight?.trim()?.uppercase()
    val trusted = normalized?.takeIf { it == TrafficLightCalculator.GREEN ||
            it == TrafficLightCalculator.YELLOW || it == TrafficLightCalculator.RED }
    if (trusted != null && ratedFoods != null && foods == ratedFoods) return trusted
    return TrafficLightCalculator.compute(foods)
}
