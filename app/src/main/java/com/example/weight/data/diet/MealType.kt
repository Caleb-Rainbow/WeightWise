package com.example.weight.data.diet

/**
 *@description: 餐次枚举。从 ui.diet 迁入 data 层：DietRecordWriter/餐次推断等数据侧
 *               纯函数与 UI 共用同一口径，避免双向依赖
 *@author: 杨帅林
 *@create: 2026/4/11
 **/
enum class MealType(val label: String, val displayName: String) {
    BREAKFAST("早餐", "🌅 早餐"),
    LUNCH("午餐", "☀️ 午餐"),
    DINNER("晚餐", "🌙 晚餐"),
    SNACK("加餐", "🍎 加餐"),
}
