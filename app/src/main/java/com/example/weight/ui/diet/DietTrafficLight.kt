package com.example.weight.ui.diet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.weight.ui.theme.DayNightColor
import com.example.weight.ui.theme.LocalIsDarkTheme
import com.example.weight.ui.theme.resolve
import com.example.weight.util.IntakeStatus

/**
 *@description: 饮食域设计令牌单一出处。三套颜色编码互不借用(D3/OV9):
 *               TrafficLightColors=食物质量;IntakeRingColors=额度状态(数值与红绿灯相同,
 *               但独立常量对象——语义隔离靠 import 边界而非注释,调食物红绿灯不会连带改额度环);
 *               DietMacroColors=宏量营养类别。
 *               全部为昼夜成对 [DayNightColor]:浅色取 t50 系、深色取 t80 系,
 *               由 material-color-utilities tonal 推导(tools/generate_themes.py 可复算与校验)。
 *               语义色不随主题中心换主题而变,只在深浅模式间切换。
 *@author: 杨帅林
 *@create: 2026/8/21
 **/

/** 食物质量红绿灯:行色条、结果卡 chip、历史日头圆点 */
object TrafficLightColors {
    val Green = DayNightColor(Color(0xFF378646), Color(0xFF87D98F))
    val Amber = DayNightColor(Color(0xFF9B7000), Color(0xFFF8BD42))
    val Red = DayNightColor(Color(0xFFD44439), Color(0xFFFFB4AA))

    /** 评级缺失(脏 JSON 等)的灰点 */
    val Unknown = DayNightColor(Color(0xFFB6C2CC), Color(0xFF939FA9))
}

/** 额度状态圆环:hero 环是全屏唯一允许使用此三色的位置 */
object IntakeRingColors {
    val Enough = DayNightColor(Color(0xFF378646), Color(0xFF87D98F))
    val NearLimit = DayNightColor(Color(0xFF9B7000), Color(0xFFF8BD42))
    val Over = DayNightColor(Color(0xFFD44439), Color(0xFFFFB4AA))
    val Track = DayNightColor(Color(0xFFE3EAF0), Color(0xFF3A4048))
    val Disabled = Track
}

fun IntakeStatus.ringColor(isDark: Boolean): Color = when (this) {
    IntakeStatus.ENOUGH -> IntakeRingColors.Enough.resolve(isDark)
    IntakeStatus.NEAR_LIMIT -> IntakeRingColors.NearLimit.resolve(isDark)
    IntakeStatus.OVER -> IntakeRingColors.Over.resolve(isDark)
}

@Composable
fun IntakeStatus.ringColor(): Color = ringColor(LocalIsDarkTheme.current)

/** 宏量营养类别:蛋白/碳水/脂肪,堆叠条与图例共用(D4/Q3A)。固定色相,夜间整体提亮一档 */
object DietMacroColors {
    val Protein = DayNightColor(Color(0xFF2B638B), Color(0xFF7DB0DC))
    val Carbs = DayNightColor(Color(0xFF26A69A), Color(0xFF46BDB0))
    val Fat = DayNightColor(Color(0xFF8D6E63), Color(0xFFC7A497))
}

/**
 * lightchip 三态色板:容器底/圆点/文字。容器取 tonal t90(浅)/t30(深),
 * 文字取 t20(浅)/t90(深),文字对容器 ≥4.5:1(tools/generate_themes.py 校验约 10:1 与 5.5:1)。
 */
@Immutable
data class LightChipColors(
    val container: Color,
    val dot: Color,
    val text: Color,
)

// chip 容器/文字成对常量;圆点直接复用 TrafficLightColors 的 base
private val ChipGreenContainer = DayNightColor(Color(0xFFA3F6A9), Color(0xFF00531E))
private val ChipGreenText = DayNightColor(Color(0xFF003913), Color(0xFFA3F6A9))
private val ChipAmberContainer = DayNightColor(Color(0xFFFFDEA5), Color(0xFF5D4200))
private val ChipAmberText = DayNightColor(Color(0xFF412D00), Color(0xFFFFDEA5))
private val ChipRedContainer = DayNightColor(Color(0xFFFFDAD5), Color(0xFF8F0F0F))
private val ChipRedText = DayNightColor(Color(0xFF690004), Color(0xFFFFDAD5))
private val ChipNeutralContainer = DayNightColor(Color(0xFFEEF2F6), Color(0xFF3A4048))
private val ChipNeutralText = DayNightColor(Color(0xFF5C7080), Color(0xFFC3CBD3))

fun lightChipColors(light: String, isDark: Boolean): LightChipColors = when (light) {
    "GREEN" -> LightChipColors(
        ChipGreenContainer.resolve(isDark),
        TrafficLightColors.Green.resolve(isDark),
        ChipGreenText.resolve(isDark),
    )
    "YELLOW" -> LightChipColors(
        ChipAmberContainer.resolve(isDark),
        TrafficLightColors.Amber.resolve(isDark),
        ChipAmberText.resolve(isDark),
    )
    "RED" -> LightChipColors(
        ChipRedContainer.resolve(isDark),
        TrafficLightColors.Red.resolve(isDark),
        ChipRedText.resolve(isDark),
    )
    else -> LightChipColors(
        ChipNeutralContainer.resolve(isDark),
        TrafficLightColors.Unknown.resolve(isDark),
        ChipNeutralText.resolve(isDark),
    )
}

@Composable
fun lightChipColors(light: String): LightChipColors = lightChipColors(light, LocalIsDarkTheme.current)

internal fun trafficLightColor(light: String, isDark: Boolean): Color = when (light) {
    "GREEN" -> TrafficLightColors.Green.resolve(isDark)
    "YELLOW" -> TrafficLightColors.Amber.resolve(isDark)
    "RED" -> TrafficLightColors.Red.resolve(isDark)
    else -> TrafficLightColors.Unknown.resolve(isDark)
}

@Composable
internal fun trafficLightColor(light: String): Color = trafficLightColor(light, LocalIsDarkTheme.current)

internal fun trafficLightLabel(light: String): String = when (light) {
    "GREEN" -> "健康饮食"
    "YELLOW" -> "尚可"
    "RED" -> "放纵一下"
    else -> "未知"
}
