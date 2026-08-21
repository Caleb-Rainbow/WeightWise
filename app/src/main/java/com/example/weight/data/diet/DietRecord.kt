package com.example.weight.data.diet

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 *@description: 饮食记录 Room 实体 + AI 响应 DTO
 *@author: 杨帅林
 *@create: 2026/4/11
 **/

// timestamp 索引供分页排序；mealType 无任何查询使用，不给它付写入维护成本
@Entity(tableName = "DietRecord", indices = [Index("date"), Index("timestamp")])
data class DietRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,
    val timestamp: Long,
    val mealType: String,
    @ColumnInfo(defaultValue = "")
    val imageUri: String = "",
    @ColumnInfo(defaultValue = "")
    val userInput: String = "",
    val recognizedFoodJson: String,
    @ColumnInfo(defaultValue = "0")
    val estimatedCalories: Int = 0,
    @ColumnInfo(defaultValue = "")
    val trafficLight: String = "",
)

// 以下 DTO 均为只读数据类，标记 @Immutable 让 Compose 恢复对其参数的跳过能力
// 宏量三字段为可选新增（v1.5）：旧记录 JSON 无这些键时反序列化为 0；
// 「真 0 值」与「无数据」按天聚合时以「存在任一非零宏量」近似区分（DailyMacroAggregator）
@Immutable
@Serializable
data class RecognizedFoodItem(
    val name: String,
    val estimatedCalories: Int,
    val estimatedGrams: Int = 0,
    val category: String = "",
    val isHealthy: Boolean = true,
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0,
    /** E1A:常用食物 chip/手动添加为 true;mergeFoods 据此让手动项跨 AI 分析存活 */
    val isManuallyAdded: Boolean = false,
)

@Immutable
@Serializable
data class AiDietResponse(
    val foods: List<RecognizedFoodItem>,
    val totalCalories: Int,
    val macros: Macros = Macros(),
    val trafficLight: String,
    val advice: String,
    val adjustedDescription: String = "",
)

@Immutable
@Serializable
data class Macros(
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0,
)
