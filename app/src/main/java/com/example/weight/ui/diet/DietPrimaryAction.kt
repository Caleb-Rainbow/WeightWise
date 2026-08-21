package com.example.weight.ui.diet

/**
 *@description: 添加页情境化主按钮状态机(D11/OV2A)。四输入穷举成纯函数才可单测;
 *               isAnalyzing/isSaving 不进此函数——分析中按钮被「取消识别」覆盖,
 *               属于调用方的更高优先级渲染分支
 *@author: 杨帅林
 *@create: 2026/8/21
 **/

enum class DietPrimaryAction {
    /** 结果卡在场:情境按钮隐藏,保存入口在结果卡内 */
    HIDDEN,

    /** 纯快速路径:已选食物非空且无图 → 离线零 AI 直接保存(备注非空也走这里,备注照存) */
    SAVE_THIS_MEAL,

    /** 有图,或仅备注无食物 → AI 识别 */
    ANALYZE,

    /** 无任何输入 → 禁用并给原因文案 */
    DISABLED,
}

fun dietPrimaryAction(
    hasImage: Boolean,
    hasNote: Boolean,
    hasFoods: Boolean,
    hasResult: Boolean,
): DietPrimaryAction = when {
    hasResult -> DietPrimaryAction.HIDDEN
    hasFoods && !hasImage -> DietPrimaryAction.SAVE_THIS_MEAL
    hasImage -> DietPrimaryAction.ANALYZE
    hasNote -> DietPrimaryAction.ANALYZE
    else -> DietPrimaryAction.DISABLED
}
