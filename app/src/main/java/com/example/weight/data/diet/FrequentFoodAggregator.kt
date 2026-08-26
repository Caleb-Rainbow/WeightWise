package com.example.weight.data.diet

import kotlinx.serialization.json.Json

/**
 *@description: 常用食物聚合：从近 N 天记录的 recognizedFoodJson 提取高频食物，
 *               供快速添加 chips 一键复用（离线、零 AI 依赖）。
 *               输入是轻量投影查询（getFoodJsonBetween）的 JSON 字符串列表
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
object FrequentFoodAggregator {

    /** 聚合窗口（含今天在内的日历天数）；窗口起点 = today - (WINDOW_DAYS - 1) */
    const val WINDOW_DAYS = 60L

    /**
     * @param foodJsonList 每个元素是一条记录的 recognizedFoodJson
     * @param limit 返回的高频食物上限（chips 展示 N=8-10）
     *
     * 聚合规则（评审定稿）：
     * - 名字 trim 后精确分组（「米饭/白米饭」暂视为不同食物，自用可容忍）
     * - 频次=出现过的不同餐数：单条记录内同名项只计一次（重复点 chip、AI 重复项
     *   不应抬高排名），样本取该餐首个同名项
     * - 克数中位数只统计 >0 样本（旧记录/手填记录允许留空 0，混入会污染默认值）
     * - 热量/宏量中位数只统计 >0 样本；全 0/负值时给 0（曾有的「全 0 回退全样本」
     *   对全 0 输入结果仍是 0，等价死代码已删）
     * - 单条 JSON 解析失败仅跳过该条，不影响其余
     */
    fun topFoods(
        foodJsonList: List<String>,
        json: Json,
        limit: Int = 10,
    ): List<RecognizedFoodItem> {
        data class Sample(
            val grams: Int,
            val calories: Int,
            val protein: Int?,
            val carbs: Int?,
            val fat: Int?,
            val category: String,
            val isHealthy: Boolean,
        )

        val groups = LinkedHashMap<String, MutableList<Sample>>()
        for (foodJson in foodJsonList) {
            val foods = runCatching {
                json.decodeFromString<List<RecognizedFoodItem>>(foodJson)
            }.getOrNull() ?: continue
            for (food in foods.distinctBy { it.name.trim() }) {
                val key = food.name.trim()
                if (key.isEmpty()) continue
                groups.getOrPut(key) { mutableListOf() }.add(
                    Sample(
                        grams = food.estimatedGrams,
                        calories = food.estimatedCalories,
                        protein = food.protein,
                        carbs = food.carbs,
                        fat = food.fat,
                        category = food.category,
                        isHealthy = food.isHealthy,
                    )
                )
            }
        }

        return groups.entries
            .sortedWith(compareByDescending<MutableMap.MutableEntry<String, MutableList<Sample>>> { it.value.size }.thenBy { it.key })
            .take(limit)
            .map { (name, samples) ->
                RecognizedFoodItem(
                    name = name,
                    estimatedCalories = medianOfPositive(samples.map { it.calories }) ?: 0,
                    estimatedGrams = medianOfPositive(samples.map { it.grams }) ?: 0,
                    category = samples.groupingBy { it.category }.eachCount()
                        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                        ?.key.orEmpty(),
                    isHealthy = samples.count { it.isHealthy } * 2 >= samples.size,
                    // 宏量样本只取非 null 值；全无样本返回 null（无数据），不再伪 0
                    protein = medianOfPositive(samples.mapNotNull { it.protein }),
                    carbs = medianOfPositive(samples.mapNotNull { it.carbs }),
                    fat = medianOfPositive(samples.mapNotNull { it.fat }),
                )
            }
    }

    /** 偶数个样本取中间两数均值（向下取整）；空列表返回 null */
    private fun medianOf(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** 只统计 >0 样本的中位数；无正值样本返回 null（调用方决定回退策略） */
    private fun medianOfPositive(values: List<Int>): Int? =
        medianOf(values.filter { it > 0 })
}
