package com.example.weight.ui.diet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.weight.util.IntakeStatus

/**
 *@description: 饮食域设计令牌单一出处。三套颜色编码互不借用(D3/OV9):
 *               TrafficLightColors=食物质量;IntakeRingColors=额度状态(数值与红绿灯相同,
 *               但独立常量对象——语义隔离靠 import 边界而非注释,调食物红绿灯不会连带改额度环);
 *               DietMacroColors=宏量营养类别
 *@author: 杨帅林
 *@create: 2026/8/21
 **/

/** 食物质量红绿灯:行色条、结果卡 chip、历史日头圆点 */
object TrafficLightColors {
    val Green = Color(0xFF4CAF50)
    val Amber = Color(0xFFFFC107)
    val Red = Color(0xFFF44336)
    /** 评级缺失(脏 JSON 等)的灰点 */
    val Unknown = Color(0xFFB6C2CC)
}

/** 额度状态圆环:hero 环是全屏唯一允许使用此三色的位置 */
object IntakeRingColors {
    val Enough = Color(0xFF4CAF50)
    val NearLimit = Color(0xFFFFC107)
    val Over = Color(0xFFF44336)
    val Track = Color(0xFFE3EAF0)
    val Disabled = Color(0xFFE3EAF0)
}

fun IntakeStatus.ringColor(): Color = when (this) {
    IntakeStatus.ENOUGH -> IntakeRingColors.Enough
    IntakeStatus.NEAR_LIMIT -> IntakeRingColors.NearLimit
    IntakeStatus.OVER -> IntakeRingColors.Over
}

/** 宏量营养类别:蛋白/碳水/脂肪,堆叠条与图例共用(D4/Q3A) */
object DietMacroColors {
    val Protein = Color(0xFF2B638B)
    val Carbs = Color(0xFF26A69A)
    val Fat = Color(0xFF8D6E63)
}

/**
 * lightchip 三态色板:容器底/圆点/文字。文字一律深色保 ≥4.5:1 对比度
 * (琥珀 #FFC107 在白底仅 1.6:1,禁止直接作文字色)。
 */
@Immutable
data class LightChipColors(
    val container: Color,
    val dot: Color,
    val text: Color,
)

fun lightChipColors(light: String): LightChipColors = when (light) {
    "GREEN" -> LightChipColors(Color(0xFFE8F5E9), TrafficLightColors.Green, Color(0xFF2E7D32))
    "YELLOW" -> LightChipColors(Color(0xFFFFF8E1), TrafficLightColors.Amber, Color(0xFFB8860B))
    "RED" -> LightChipColors(Color(0xFFFFEBEE), TrafficLightColors.Red, Color(0xFFC62828))
    else -> LightChipColors(Color(0xFFEEF2F6), TrafficLightColors.Unknown, Color(0xFF5C7080))
}

internal fun trafficLightColor(light: String): Color = when (light) {
    "GREEN" -> TrafficLightColors.Green
    "YELLOW" -> TrafficLightColors.Amber
    "RED" -> TrafficLightColors.Red
    else -> TrafficLightColors.Unknown
}

internal fun trafficLightLabel(light: String): String = when (light) {
    "GREEN" -> "健康饮食"
    "YELLOW" -> "尚可"
    "RED" -> "放纵一下"
    else -> "未知"
}
