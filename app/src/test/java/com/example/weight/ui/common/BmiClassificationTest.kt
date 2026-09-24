package com.example.weight.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BMI 分级必须按 18.5/24/28 连续分段：
 * 首页/小组件传入的是未取整的原始值（体重/身高²，如 27.905 显示为 27.9），
 * 缝隙值与边界值都不允许落空返回 null（曾导致评级显示"无"、指针停在起点）。
 */
class BmiClassificationTest {

    @Test
    fun `缝隙值27点905归入偏高`() {
        assertEquals(BMI.OVERWEIGHT, BMI.fromBMIValue(27.905))
    }

    @Test
    fun `各档缝隙值归入左侧档位`() {
        assertEquals(BMI.LOW, BMI.fromBMIValue(18.45))
        assertEquals(BMI.STANDARD, BMI.fromBMIValue(23.95))
        assertEquals(BMI.OVERWEIGHT, BMI.fromBMIValue(27.95))
    }

    @Test
    fun `边界值按中国成人标准归入较高档`() {
        assertEquals(BMI.STANDARD, BMI.fromBMIValue(18.5))
        assertEquals(BMI.OVERWEIGHT, BMI.fromBMIValue(24.0))
        assertEquals(BMI.OBESE, BMI.fromBMIValue(28.0))
    }

    @Test
    fun `档位内常规取值`() {
        assertEquals(BMI.LOW, BMI.fromBMIValue(15.0))
        assertEquals(BMI.STANDARD, BMI.fromBMIValue(21.0))
        assertEquals(BMI.OVERWEIGHT, BMI.fromBMIValue(27.9))
        assertEquals(BMI.OBESE, BMI.fromBMIValue(30.0))
    }

    @Test
    fun `超出刻度上限仍归入过高`() {
        assertEquals(BMI.OBESE, BMI.fromBMIValue(38.5))
    }

    @Test
    fun `无数据与低于刻度下限返回null`() {
        assertNull(BMI.fromBMIValue(0.0))
        assertNull(BMI.fromBMIValue(5.0))
    }
}
