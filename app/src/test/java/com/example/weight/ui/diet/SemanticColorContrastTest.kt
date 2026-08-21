package com.example.weight.ui.diet

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.weight.ui.theme.resolve
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 语义色板可读性回归:lightchip 文字对容器、语义 base 色对应用背景的 WCAG 对比度。
 * 色值由 tools/generate_themes.py tonal 推导,本测试锁住「文字 ≥4.5:1」的设计承诺,
 * 防止后续调色时把琥珀类低对比组合(旧 #FFC107 在白底仅 1.6:1)带回来。
 */
class SemanticColorContrastTest {

    /** WCAG 相对亮度:sRGB 通道线性化后按 0.2126/0.7152/0.0722 加权 */
    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** 应用昼夜背景(取自 ui/theme/Color.kt 的 backgroundLight/Dark),与变体一一配对 */
    private val dayNightBackgrounds = listOf(false to Color(0xFFF7F9FF), true to Color(0xFF101418))

    @Test
    fun `lightchip 文字对容器对比度昼夜均不低于4_5`() {
        for (light in listOf("GREEN", "YELLOW", "RED", "UNKNOWN")) {
            for (isDark in listOf(false, true)) {
                val chip = lightChipColors(light, isDark)
                val ratio = contrast(chip.text, chip.container)
                assertTrue(
                    "$light isDark=$isDark 文字(${chip.text.toArgb().toHexString()}) 对 " +
                        "容器(${chip.container.toArgb().toHexString()}) 对比度 ${"%.2f".format(ratio)} < 4.5",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `红绿灯与额度环 base 色对同模式应用背景不低于3_1`() {
        // Unknown 是刻意弱化的评级缺失占位灰(与旧版同值),不作硬性可见度要求
        val bases = TrafficLightColors.run {
            listOf(Green, Amber, Red)
        } + IntakeRingColors.run { listOf(Enough, NearLimit, Over) }
        for (base in bases) {
            for ((isDark, bg) in dayNightBackgrounds) {
                val resolved = base.resolve(isDark)
                val ratio = contrast(resolved, bg)
                assertTrue(
                    "$resolved(isDark=$isDark) 对背景 $bg 对比度 ${"%.2f".format(ratio)} < 3",
                    ratio >= 3.0,
                )
            }
        }
    }

    @Test
    fun `体重涨跌色对同模式应用背景不低于3_1`() {
        val bases = listOf(
            com.example.weight.ui.record.WeightTrendColors.Decrease,
            com.example.weight.ui.record.WeightTrendColors.Increase,
        )
        for (base in bases) {
            for ((isDark, bg) in dayNightBackgrounds) {
                val ratio = contrast(base.resolve(isDark), bg)
                assertTrue("涨跌色对比度 ${"%.2f".format(ratio)} < 3", ratio >= 3.0)
            }
        }
    }

    private fun Int.toHexString(): String = "%08X".format(this)
}
