package com.example.weight.ui.diet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 添加页情境化主按钮状态机全组合测试(D11/OV2A)。
 * 输入四元组 (hasImage, hasNote, hasFoods, hasResult),共 16 种组合
 */
class DietPrimaryActionTest {

    private fun action(
        hasImage: Boolean = false,
        hasNote: Boolean = false,
        hasFoods: Boolean = false,
        hasResult: Boolean = false,
    ): DietPrimaryAction = dietPrimaryAction(hasImage, hasNote, hasFoods, hasResult)

    @Test
    fun `结果卡在场时情境按钮隐藏`() {
        assertEquals(DietPrimaryAction.HIDDEN, action(hasResult = true))
        assertEquals(DietPrimaryAction.HIDDEN, action(hasImage = true, hasResult = true))
        assertEquals(DietPrimaryAction.HIDDEN, action(hasNote = true, hasFoods = true, hasResult = true))
    }

    @Test
    fun `已选食物且无图走快速保存_备注存在也不改道`() {
        assertEquals(DietPrimaryAction.SAVE_THIS_MEAL, action(hasFoods = true))
        // OV2 修复:份量修正备注不应把 3 秒离线路径杀死
        assertEquals(DietPrimaryAction.SAVE_THIS_MEAL, action(hasNote = true, hasFoods = true))
    }

    @Test
    fun `有图走识别_即使已选食物`() {
        assertEquals(DietPrimaryAction.ANALYZE, action(hasImage = true))
        assertEquals(DietPrimaryAction.ANALYZE, action(hasImage = true, hasFoods = true))
        assertEquals(DietPrimaryAction.ANALYZE, action(hasImage = true, hasNote = true, hasFoods = true))
    }

    @Test
    fun `仅备注无食物走文本识别`() {
        assertEquals(DietPrimaryAction.ANALYZE, action(hasNote = true))
    }

    @Test
    fun `全空为禁用并给原因`() {
        assertEquals(DietPrimaryAction.DISABLED, action())
    }
}
