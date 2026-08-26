package com.example.weight.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartViewportTest {

    @Test
    fun `近7天和近14天节点完整适配一屏`() {
        assertTrue(shouldFitAllChartPoints(7))
        assertTrue(shouldFitAllChartPoints(14))
    }

    @Test
    fun `超过14个节点保留横向浏览`() {
        assertFalse(shouldFitAllChartPoints(15))
    }
}
