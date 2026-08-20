package com.example.weight.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MilestoneCalculatorTest {

    @Test
    fun `减重不足一档为0`() {
        assertEquals(0, MilestoneCalculator.calculateMilestoneCount(startWeight = 80.0, currentWeight = 78.1))
    }

    @Test
    fun `恰好一档算达成`() {
        assertEquals(1, MilestoneCalculator.calculateMilestoneCount(startWeight = 80.0, currentWeight = 78.0))
    }

    @Test
    fun `多档向下取整`() {
        // 减重 5.9kg → 2 档
        assertEquals(2, MilestoneCalculator.calculateMilestoneCount(startWeight = 80.0, currentWeight = 74.1))
        // 减重 6.0kg → 3 档
        assertEquals(3, MilestoneCalculator.calculateMilestoneCount(startWeight = 80.0, currentWeight = 74.0))
    }

    @Test
    fun `增重方向无里程碑`() {
        assertEquals(0, MilestoneCalculator.calculateMilestoneCount(startWeight = 70.0, currentWeight = 75.0))
    }

    @Test
    fun `档位换算公斤数`() {
        assertEquals(4.0, MilestoneCalculator.lossKgOfMilestone(2), 1e-9)
    }
}
