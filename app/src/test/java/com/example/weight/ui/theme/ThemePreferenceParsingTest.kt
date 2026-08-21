package com.example.weight.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** 主题中心偏好解析:MMKV 存的是枚举 name 字符串,未知值必须兜底而不是崩 */
class ThemePreferenceParsingTest {

    @Test
    fun `fromId 命中全部预设`() {
        for (preset in ThemePreset.entries) {
            assertEquals(preset, ThemePreset.fromId(preset.name))
        }
    }

    @Test
    fun `fromId 未知或空值兜底钢蓝`() {
        assertEquals(ThemePreset.STEEL_BLUE, ThemePreset.fromId("NO_SUCH_THEME"))
        assertEquals(ThemePreset.STEEL_BLUE, ThemePreset.fromId(""))
    }

    @Test
    fun `外观模式 fromId 命中与兜底`() {
        for (mode in AppearanceMode.entries) {
            assertEquals(mode, AppearanceMode.fromId(mode.name))
        }
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromId("AUTO"))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromId(""))
    }

    @Test
    fun `五套预设明暗 scheme 主色互不相同且非占位`() {
        // 换主题必须肉眼可辨:各预设浅色 primary 两两不同
        val lights = ThemePreset.entries.map { it.light.primary }
        assertEquals(lights.size, lights.toSet().size)
        val darks = ThemePreset.entries.map { it.dark.primary }
        assertEquals(darks.size, darks.toSet().size)
    }
}
