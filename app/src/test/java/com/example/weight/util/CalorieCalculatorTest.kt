package com.example.weight.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalorieCalculatorTest {

    @Test
    fun `男性 BMR 按 Mifflin-St Jeor 计算`() {
        // 10*75 + 6.25*178 - 5*30 + 5 = 1717.5
        assertEquals(1717.5, CalorieCalculator.bmr(Gender.MALE, 75.0, 178.0, 30)!!, 1e-9)
    }

    @Test
    fun `女性 BMR 按 Mifflin-St Jeor 计算`() {
        // 10*60 + 6.25*165 - 5*28 - 161 = 1330.25
        assertEquals(1330.25, CalorieCalculator.bmr(Gender.FEMALE, 60.0, 165.0, 28)!!, 1e-9)
    }

    @Test
    fun `档案缺失时 BMR 返回 null`() {
        assertNull(CalorieCalculator.bmr(null, 75.0, 178.0, 30))
        assertNull(CalorieCalculator.bmr(Gender.MALE, 75.0, 178.0, 0))
        assertNull(CalorieCalculator.bmr(Gender.MALE, 0.0, 178.0, 30))
        assertNull(CalorieCalculator.bmr(Gender.MALE, 75.0, 0.0, 30))
    }

    @Test
    fun `建议摄入等于 TDEE 乘活动系数`() {
        // 1717.5 * 1.55 = 2662.125，未设目标不调整
        val intake = CalorieCalculator.recommendedIntake(
            Gender.MALE, 75.0, 178.0, 30, ActivityLevel.MODERATE, targetWeightKg = 0.0,
        )
        assertEquals(2662, intake)
    }

    @Test
    fun `减重目标减 500 大卡`() {
        val intake = CalorieCalculator.recommendedIntake(
            Gender.MALE, 75.0, 178.0, 30, ActivityLevel.MODERATE, targetWeightKg = 70.0,
        )
        // 2662.125 - 500 = 2162.125 → 2162
        assertEquals(2162, intake)
    }

    @Test
    fun `减重热量缺口随每周目标速度调整`() {
        val intake = CalorieCalculator.recommendedIntake(
            Gender.MALE, 75.0, 178.0, 30, ActivityLevel.MODERATE,
            targetWeightKg = 70.0,
            weeklyTargetChangeKg = 0.3,
        )
        // 0.3kg/周约等于 300kcal/日缺口
        assertEquals(2362, intake)
    }

    @Test
    fun `增重目标加 300 大卡`() {
        val intake = CalorieCalculator.recommendedIntake(
            Gender.MALE, 75.0, 178.0, 30, ActivityLevel.MODERATE, targetWeightKg = 80.0,
        )
        // 2662.125 + 300 = 2962.125 → 2962
        assertEquals(2962, intake)
    }

    @Test
    fun `维持目标不调整`() {
        // 目标与当前差距不足 0.5kg 视为维持
        val intake = CalorieCalculator.recommendedIntake(
            Gender.MALE, 75.0, 178.0, 30, ActivityLevel.MODERATE, targetWeightKg = 75.0,
        )
        assertEquals(2662, intake)
    }

    @Test
    fun `建议摄入不低于安全下限`() {
        // 女 BMR=826.5，久坐 TDEE=991.8，减重后 491.8 → 下限保护为 1200
        val intake = CalorieCalculator.recommendedIntake(
            Gender.FEMALE, 45.0, 150.0, 80, ActivityLevel.SEDENTARY, targetWeightKg = 40.0,
        )
        assertEquals(1200, intake)
    }

    @Test
    fun `活动水平缺失时建议摄入返回 null`() {
        val intake = CalorieCalculator.recommendedIntake(
            Gender.MALE, 75.0, 178.0, 30, null, targetWeightKg = 70.0,
        )
        assertNull(intake)
    }

    @Test
    fun `摄入状态三档边界`() {
        // 90% 恰好落在充足档，超过 90% 进入接近档，超过 100% 为超出
        assertEquals(IntakeStatus.ENOUGH, CalorieCalculator.intakeStatus(900, 1000))
        assertEquals(IntakeStatus.NEAR_LIMIT, CalorieCalculator.intakeStatus(901, 1000))
        assertEquals(IntakeStatus.NEAR_LIMIT, CalorieCalculator.intakeStatus(1000, 1000))
        assertEquals(IntakeStatus.OVER, CalorieCalculator.intakeStatus(1001, 1000))
        assertEquals(IntakeStatus.ENOUGH, CalorieCalculator.intakeStatus(0, 1000))
    }
}
