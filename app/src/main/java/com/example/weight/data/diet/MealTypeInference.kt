package com.example.weight.data.diet

import java.time.LocalTime

/**
 *@description: 按当前时间推断餐次。边界（评审定稿）：[5:00,10:00) 早餐、[10:00,14:30) 午餐、
 *               [14:30,17:00) 加餐、[17:00,22:00) 晚餐、其余（22:00-5:00）加餐。
 *               只做预选，用户手动选择后以手动为准
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
object MealTypeInference {

    fun infer(time: LocalTime): MealType {
        val minutes = time.hour * 60 + time.minute
        return when (minutes) {
            in 5 * 60 until 10 * 60 -> MealType.BREAKFAST
            in 10 * 60 until 14 * 60 + 30 -> MealType.LUNCH
            in 17 * 60 until 22 * 60 -> MealType.DINNER
            else -> MealType.SNACK
        }
    }

    fun inferNow(): MealType = infer(LocalTime.now())
}
