package com.example.weight.data.diet

import kotlinx.serialization.json.Json

/** 单日宏量合计结果 */
data class DailyMacros(
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    /** 任一食物存在宏量数据（字段非 null）即认为该日有宏量数据 */
    val hasMacroData: Boolean,
    /** 部分食物无宏量数据（字段为 null）被跳过，合计是部分和；UI 应提示而非当完整值展示 */
    val partialMacroData: Boolean,
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

    fun aggregate(records: List<DietRecord>, json: Json): DailyMacros = aggregate(records) { text ->
        runCatching { json.decodeFromString<List<RecognizedFoodItem>>(text) }.getOrNull()
    }

    /**
     * 解码器注入版：调用方可传带缓存的解码器。Room 表级失效会让任一记录写入
     * 重发全量列表，未变化记录的 JSON 逐条重新解码是纯浪费。
     * 解码器返回 null 视为该条解析失败（跳过并计数）。
     */
    fun aggregate(records: List<DietRecord>, decode: (String) -> List<RecognizedFoodItem>?): DailyMacros {
        var protein = 0
        var carbs = 0
        var fat = 0
        var hasMacroData = false
        var partial = false
        var skipped = 0
        for (record in records) {
            val foods = decode(record.recognizedFoodJson)
            if (foods == null) {
                skipped++
                continue
            }
            for (food in foods) {
                val anyNonNull = food.protein != null || food.carbs != null || food.fat != null
                if (anyNonNull) hasMacroData = true else partial = true
                food.protein?.let { protein += it }
                food.carbs?.let { carbs += it }
                food.fat?.let { fat += it }
            }
        }
        return DailyMacros(protein, carbs, fat, hasMacroData, partial && hasMacroData, skipped)
    }

    /**
     * 宏量堆叠条权重(D4/Q3A):按热量占比归一(kcal = g×4/4/9,营养学惯例),
     * 图例仍显示克数。全零宏量返回 null,UI 不渲染堆叠条
     */
    fun macroCalorieWeights(proteinG: Int, carbsG: Int, fatG: Int): Triple<Float, Float, Float>? {
        val p = proteinG * 4f
        val c = carbsG * 4f
        val f = fatG * 9f
        val total = p + c + f
        if (total <= 0f) return null
        return Triple(p / total, c / total, f / total)
    }
}
