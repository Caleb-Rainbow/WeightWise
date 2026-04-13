package com.example.weight.data.diet

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

@Entity(tableName = "DietRecord", indices = [Index("date"), Index("mealType")])
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

@Serializable
data class RecognizedFoodItem(
    val name: String,
    val estimatedCalories: Int,
    val estimatedGrams: Int = 0,
    val category: String = "",
    val isHealthy: Boolean = true,
)

@Serializable
data class AiDietResponse(
    val foods: List<RecognizedFoodItem>,
    val totalCalories: Int,
    val macros: Macros = Macros(),
    val trafficLight: String,
    val advice: String,
    val adjustedDescription: String = "",
)

@Serializable
data class Macros(
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0,
)
