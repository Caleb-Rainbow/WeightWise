package com.example.weight.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 *@description: 主题中心预设。品牌配色层只管「界面气质」;语义色(红绿灯/额度环/宏量)
 *               不随主题变,见 ui/diet/DietTrafficLight.kt。新增主题先改
 *               tools/generate_themes.py 重跑,再在此登记。
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
enum class ThemePreset(
    val label: String,
    val light: ColorScheme,
    val dark: ColorScheme,
) {
    /** 默认钢蓝,视觉基准走 Color.kt 的 lightScheme/darkScheme */
    STEEL_BLUE("钢蓝", lightScheme, darkScheme),
    INDIGO("靛青", indigoLightScheme, indigoDarkScheme),
    WISTERIA("紫藤", wisteriaLightScheme, wisteriaDarkScheme),
    ROSE("蔷薇", roseLightScheme, roseDarkScheme),
    TERRACOTTA("陶土", terracottaLightScheme, terracottaDarkScheme);

    /** 选择器圆点主色(浅色 primary) */
    val swatch: Color get() = light.primary

    companion object {
        fun fromId(id: String): ThemePreset = entries.find { it.name == id } ?: STEEL_BLUE
    }
}

/** 深浅模式三态:跟随系统/强制浅色/强制深色 */
enum class AppearanceMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun fromId(id: String): AppearanceMode = entries.find { it.name == id } ?: SYSTEM
    }
}
