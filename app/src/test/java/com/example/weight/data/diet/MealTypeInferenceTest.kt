package com.example.weight.data.diet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class MealTypeInferenceTest {

    private fun infer(hour: Int, minute: Int) = MealTypeInference.infer(LocalTime.of(hour, minute))

    @Test
    fun `五个边界时刻落在正确餐次`() {
        assertEquals(MealType.BREAKFAST, infer(5, 0))    // 早餐开
        assertEquals(MealType.LUNCH, infer(10, 0))       // 午餐开
        assertEquals(MealType.SNACK, infer(14, 30))      // 加餐开
        assertEquals(MealType.DINNER, infer(17, 0))      // 晚餐开
        assertEquals(MealType.SNACK, infer(22, 0))       // 晚餐后加餐
    }

    @Test
    fun `边界前一分钟归上一餐次`() {
        assertEquals(MealType.SNACK, infer(4, 59))       // 凌晨归加餐
        assertEquals(MealType.BREAKFAST, infer(9, 59))
        assertEquals(MealType.LUNCH, infer(14, 29))
        assertEquals(MealType.SNACK, infer(16, 59))
        assertEquals(MealType.DINNER, infer(21, 59))
    }

    @Test
    fun `正餐时段不误判为加餐`() {
        // 评审修正的回归点：18:30 是晚餐，不是加餐
        assertEquals(MealType.DINNER, infer(18, 30))
        assertEquals(MealType.DINNER, infer(19, 30))
        assertEquals(MealType.LUNCH, infer(12, 0))
    }

    @Test
    fun `深夜与凌晨归加餐`() {
        assertEquals(MealType.SNACK, infer(23, 30))
        assertEquals(MealType.SNACK, infer(0, 30))
    }
}
