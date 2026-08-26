package com.example.weight.data

import com.dylanc.mmkv.MMKVOwner
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_HEIGHT = 170.0
const val DEFAULT_TARGET_WEIGHT = 0.0
const val DEFAULT_START_WEIGHT = 0.0
const val DEFAULT_WEEKLY_TARGET_CHANGE_KG = 0.5
const val DEFAULT_STAGE_GOAL_STEP_KG = 2.0
const val DEFAULT_AGE = 0

object LocalStorageData : MMKVOwner(mmapID = "settings") {

    /*--------基础信息---------*/
    val height by mmkvDouble(default = DEFAULT_HEIGHT).asStateFlow()

    /*--------个人档案（热量建议用），空值表示未设置---------*/
    /** 年龄（岁）；0 表示未设置 */
    val age by mmkvInt(default = DEFAULT_AGE).asStateFlow()

    /** 性别，存 [com.example.weight.util.Gender].name；空串表示未设置 */
    val gender by mmkvString(default = "").asStateFlow()

    /** 活动水平，存 [com.example.weight.util.ActivityLevel].name；空串表示未设置 */
    val activityLevel by mmkvString(default = "").asStateFlow()

    /*--------目标相关---------*/
    val targetWeight by mmkvDouble(default = DEFAULT_TARGET_WEIGHT).asStateFlow()

    /** 目标起始体重；0.0 表示未手动设置，此时跟随第一条体重记录 */
    val startWeight by mmkvDouble(default = DEFAULT_START_WEIGHT).asStateFlow()

    /** 每周计划变化量（kg），同时用于目标日期规划与减重热量建议 */
    val weeklyTargetChangeKg by mmkvDouble(default = DEFAULT_WEEKLY_TARGET_CHANGE_KG).asStateFlow()

    /** 阶段目标间隔（kg） */
    val stageGoalStepKg by mmkvDouble(default = DEFAULT_STAGE_GOAL_STEP_KG).asStateFlow()

    /** 当前腰围（cm）；0.0 表示未设置 */
    val currentWaistCm by mmkvDouble(default = 0.0).asStateFlow()

    /** 目标腰围（cm）；0.0 表示未设置 */
    val targetWaistCm by mmkvDouble(default = 0.0).asStateFlow()

    /** 目标体脂率（%）；0.0 表示未设置 */
    val targetBodyFatPercent by mmkvDouble(default = 0.0).asStateFlow()
    /*--------其他---------*/
    var isFirst by mmkvBool(default = true)

    /*--------每日称重提醒---------*/
    val reminderEnabled by mmkvBool(default = false).asStateFlow()
    /** 提醒时间，HH:mm 24 小时制 */
    val reminderTime by mmkvString(default = "07:30").asStateFlow()

    /*--------周报推送---------*/
    val weeklyReportPushEnabled by mmkvBool(default = false).asStateFlow()
    /** 推送时间（每周一），HH:mm 24 小时制 */
    val weeklyReportPushTime by mmkvString(default = "08:00").asStateFlow()

    /*--------AI 提供商相关---------*/
    val doubaoModelId by mmkvString(default = "doubao-seed-2-0-lite-260215").asStateFlow()

    /*--------外观(主题中心)---------*/
    /** 主题预设,存 [com.example.weight.ui.theme.ThemePreset].name */
    val themeId by mmkvString(default = "STEEL_BLUE").asStateFlow()

    /** 深浅模式,存 [com.example.weight.ui.theme.AppearanceMode].name */
    val appearanceMode by mmkvString(default = "SYSTEM").asStateFlow()

    /*--------统计---------*/
    /** 每日体重统计口径，存 [com.example.weight.data.record.DailyStatMode].name；默认最低值保持历史行为 */
    val dailyStatMode by mmkvString(default = "MIN").asStateFlow()

    /*--------Health Connect---------*/
    /** 用户主动开启后才执行前台同步；权限被撤销时保留开关，便于重新授权后恢复 */
    val healthConnectEnabled by mmkvBool(default = false).asStateFlow()

    /** 最近一次完整同步成功时间（epoch millis）；0 表示从未同步 */
    val healthConnectLastSyncAt by mmkvLong(default = 0L).asStateFlow()
}
