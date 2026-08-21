package com.example.weight.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 *@description: 昼夜成对的颜色值机制类型。各业务域的语义色板(饮食红绿灯/额度环/宏量、
 *               体重涨跌等)用它承载浅色/深色两套值;组合内用无参 [resolve](),
 *               非组合环境(单测/小部件)用带 isDark 的 [resolve]。
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
@Immutable
data class DayNightColor(val light: Color, val dark: Color)

fun DayNightColor.resolve(isDark: Boolean): Color = if (isDark) dark else light

/** 组合期解析:读主题中心生效的深浅模式(含强制浅色/深色覆写),勿直接用 isSystemInDarkTheme */
@Composable
fun DayNightColor.resolve(): Color = resolve(LocalIsDarkTheme.current)
