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
@Entity(
    tableName = "DietRecord",
    indices = [Index("date"), Index("timestamp"), Index("healthConnectId")],
)
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
    /** 从 Health Connect 导入时保存其 NutritionRecord id；本地原生记录为空 */
    @ColumnInfo(defaultValue = "")
    val healthConnectId: String = "",
    /** Health Connect 数据来源包名；非空记录不会再次导出 */
    @ColumnInfo(defaultValue = "")
    val healthConnectOrigin: String = "",
)

/**
 * 单个食物的饮食质量三态（T-6）。
 * 序列化为名字字符串；旧 JSON 无此键时反序列化为 null，读取侧经 [RecognizedFoodItem.effectiveQuality] 回退。
 */
@Immutable
@Serializable
enum class FoodQuality {
    /** 适合经常吃 */
    OFTEN,

    /** 偶尔吃 */
    SOMETIMES,

    /** 放纵一下 */
    INDULGENT,
}

// 以下 DTO 均为只读数据类，标记 @Immutable 让 Compose 恢复对其参数的跳过能力
// 宏量三字段可空（T-6 配套）：null=该食物无宏量数据（v1.5 前旧记录缺键反序列化而来），
// 0=真 0 值（如零卡饮料）。日聚合由 DailyMacroAggregator 区分完整和/部分和
@Immutable
@Serializable
data class RecognizedFoodItem(
    val name: String,
    val estimatedCalories: Int,
    val estimatedGrams: Int = 0,
    val category: String = "",
    // isHealthy 已被 quality 取代，仅保留字段兼容旧 JSON 反序列化（encodeDefaults=false 下不再写出）；
    // 新代码禁止读写，统一走 quality / effectiveQuality
    val isHealthy: Boolean = true,
    val quality: FoodQuality? = null,
    val protein: Int? = null,
    val carbs: Int? = null,
    val fat: Int? = null,
    /** E1A:常用食物 chip/手动添加为 true;mergeFoods 据此让手动项跨 AI 分析存活 */
    val isManuallyAdded: Boolean = false,
) {
    /** 统一质量口径：quality 优先；旧数据按 isHealthy 映射（false 保守归 SOMETIMES，不升级惩罚） */
    val effectiveQuality: FoodQuality
        get() = quality ?: if (isHealthy) FoodQuality.OFTEN else FoodQuality.SOMETIMES
}

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
