package com.example.weight.ui.diet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.weight.data.diet.MealType

/**
 *@description: 餐次图标映射。放 ui 层扩展属性而非 enum 字段:MealType 当初从 ui.diet
 *               迁入 data 层就是为了避免 data → compose.ui 的双向依赖(E2A),ImageVector
 *               是 UI 概念,不该进数据层
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
val MealType.icon: ImageVector
    get() = when (this) {
        MealType.BREAKFAST -> Icons.Filled.WbSunny
        MealType.LUNCH -> Icons.Filled.Restaurant
        MealType.DINNER -> Icons.Filled.NightsStay
        MealType.SNACK -> Icons.Filled.LocalCafe
    }
