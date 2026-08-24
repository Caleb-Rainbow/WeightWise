package com.example.weight.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/** WeightWise 2.0 五套品牌主题的核心文字对比度回归。 */
class ThemeColorContrastTest {

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val channel = value.toDouble()
            return if (channel <= 0.03928) channel / 12.92
            else Math.pow((channel + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrast(first: Color, second: Color): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (maxOf(firstLuminance, secondLuminance) + 0.05) /
            (minOf(firstLuminance, secondLuminance) + 0.05)
    }

    @Test
    fun `五套主题的核心文字组合均满足 WCAG AA`() {
        ThemePreset.entries.forEach { preset ->
            listOf("浅色" to preset.light, "深色" to preset.dark).forEach { (mode, scheme) ->
                val pairs = listOf(
                    "背景" to (scheme.onBackground to scheme.background),
                    "表面" to (scheme.onSurface to scheme.surface),
                    "主按钮" to (scheme.onPrimary to scheme.primary),
                    "主色容器" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                    "次色容器" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                )
                pairs.forEach { (role, colors) ->
                    val ratio = contrast(colors.first, colors.second)
                    assertTrue(
                        "${preset.label} $mode $role 对比度 ${"%.2f".format(ratio)} < 4.5",
                        ratio >= 4.5,
                    )
                }
            }
        }
    }
}
