package com.example.weight.ui.record

import androidx.compose.ui.graphics.Color
import com.example.weight.ui.theme.DayNightColor

/**
 *@description: 体重涨跌语义令牌:降为利好绿、升为提醒红。昼夜两套,tonal 口径同
 *               饮食域红绿灯(tools/generate_themes.py 推导)。独立对象——体重域不 import
 *               饮食域常量,靠包边界隔离语义(DESIGN.md 颜色规则)。
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
object WeightTrendColors {
    /** 体重下降(利好) */
    val Decrease = DayNightColor(Color(0xFF378646), Color(0xFF87D98F))

    /** 体重上涨(提醒) */
    val Increase = DayNightColor(Color(0xFFD44439), Color(0xFFFFB4AA))
}
