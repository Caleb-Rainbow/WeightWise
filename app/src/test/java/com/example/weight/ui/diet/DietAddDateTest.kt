package com.example.weight.ui.diet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 添加页补记日期纯函数测试:跨午夜跟随规则与保存成功 SnackBar 文案分支
 */
class DietAddDateTest {

    private val today = LocalDate.of(2026, 8, 26)

    // ================================ advanceAddTabDate ================================

    @Test
    fun `未手动改过日期_跨午夜跟随到新今天`() {
        assertEquals(
            "2026-08-27",
            advanceAddTabDate(current = "2026-08-26", oldToday = "2026-08-26", newToday = "2026-08-27"),
        )
    }

    @Test
    fun `手动补记的日期_跨午夜保留不被覆盖`() {
        assertEquals(
            "2026-08-24",
            advanceAddTabDate(current = "2026-08-24", oldToday = "2026-08-26", newToday = "2026-08-27"),
        )
    }

    // ================================ savedMessage ================================

    @Test
    fun `记今天且有额度_给今日余量文案`() {
        assertEquals(
            "已记录,今日还可摄入 500 kcal",
            savedMessage("2026-08-26", today, remainingCalories = 500),
        )
    }

    @Test
    fun `记今天但无额度档案_降级为已记录`() {
        assertEquals("已记录", savedMessage("2026-08-26", today, remainingCalories = null))
    }

    @Test
    fun `补记昨天_给人性化补记文案`() {
        assertEquals(
            "已补记 昨天",
            savedMessage("2026-08-25", today, remainingCalories = null),
        )
    }

    @Test
    fun `补记更早日期_给月日周文案`() {
        // 2026-08-24 是周一
        assertEquals(
            "已补记 8月24日 周一",
            savedMessage("2026-08-24", today, remainingCalories = null),
        )
    }
}
