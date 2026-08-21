package com.example.weight.data.diet

import kotlinx.serialization.json.Json

/** 单日宏量合计结果 */
data class DailyMacros(
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    /** 近似标记：任一食物存在非零宏量即认为该日有宏量数据。真 0 宏量食物（无糖可乐）
     *  与旧版无宏量字段记录不可区分，按「有数据」近似；混合日为部分和，UI 需配合 skippedRecords 提示 */
    val hasMacroData: Boolean,
    /** 解析失败被跳过的记录条数（调用方负责 Log 观测） */
    val skippedRecords: Int,
)

/**
 *@description: 单日宏量营养素聚合。逐条 runCatching 解析 recognizedFoodJson：
 *               历史记录由不同版本 AI 产出，单条脏 JSON 只跳过该条，不允许灭掉整日聚合
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
object DailyMacroAggregator {

    fun aggregate(records: List<DietRecord>, json: Json): DailyMacros {
        var protein = 0
        var carbs = 0
        var fat = 0
        var hasMacroData = false
        var skipped = 0
        for (record in records) {
            val foods = runCatching {
                json.decodeFromString<List<RecognizedFoodItem>>(record.recognizedFoodJson)
            }.onFailure {
                skipped++
            }.getOrNull() ?: continue
            for (food in foods) {
                protein += food.protein
                carbs += food.carbs
                fat += food.fat
                if (food.protein > 0 || food.carbs > 0 || food.fat > 0) hasMacroData = true
            }
        }
        return DailyMacros(protein, carbs, fat, hasMacroData, skipped)
    }
}
