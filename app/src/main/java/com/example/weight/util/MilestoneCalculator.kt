package com.example.weight.util

import kotlin.math.floor

/**
 * 减重里程碑计算器：以起始体重为基准，每累计减重 [STEP_KG] kg 达成一档。
 * 增重方向不设里程碑。纯函数，便于单测与首页/小组件共用。
 */
object MilestoneCalculator {

    const val STEP_KG = 2.0

    /**
     * 当前已达成的里程碑档数（向下取整）。
     * 例：起始 80kg，当前 74.1kg → 减重 5.9kg → 达成 2 档（4kg、6kg 未到）。
     */
    fun calculateMilestoneCount(startWeight: Double, currentWeight: Double): Int {
        val loss = startWeight - currentWeight
        if (loss < STEP_KG) return 0
        return floor(loss / STEP_KG).toInt()
    }

    /** 第 [count] 档对应的累计减重公斤数，用于庆祝文案 */
    fun lossKgOfMilestone(count: Int): Double = count * STEP_KG
}
