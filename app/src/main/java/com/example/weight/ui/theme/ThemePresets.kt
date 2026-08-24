package com.example.weight.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 *@description: 主题中心预设。品牌配色层只管「界面气质」;语义色(红绿灯/额度环/宏量)
 *               不随主题变,见 ui/diet/DietTrafficLight.kt。新增主题先在
 *               WeightWiseSchemes.kt 定义昼夜配色,再在此登记。
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
enum class ThemePreset(
    val label: String,
    val light: ColorScheme,
    val dark: ColorScheme,
) {
    /** 枚举名称保留，兼容已存储的主题偏好；标签与色板升级为东方自然色。 */
    STEEL_BLUE("松石", jadeLightScheme, jadeDarkScheme),
    INDIGO("黛青", inkLightScheme, inkDarkScheme),
    WISTERIA("藕荷", lotusLightScheme, lotusDarkScheme),
    ROSE("胭脂", rougeLightScheme, rougeDarkScheme),
    TERRACOTTA("丹砂", cinnabarLightScheme, cinnabarDarkScheme);

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
