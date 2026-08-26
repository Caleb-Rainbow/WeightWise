package com.example.weight.data.backup

import kotlinx.serialization.Serializable

/**
 * 全量备份文件结构（JSON）。
 * 饮食图片文件不入包，仅保留文件名；恢复后图片缺失时界面按无图展示。
 * v1.1 起 settings 纳入提醒/周报/AI 模型/主题外观；旧 JSON 缺字段走默认值。
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val app: String = "WeightWise",
    val exportedAt: Long,
    val records: List<RecordBackup>,
    val dietRecords: List<DietRecordBackup>,
    val settings: SettingsBackup,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class RecordBackup(
    val weight: Double,
    val log: String = "",
    val timestamp: Long,
    /** 体脂秤成分 JSON（[com.example.weight.data.record.BodyComposition]）；旧备份缺字段时为空串 */
    val bodyComposition: String = "",
    /** 高频三率冗余列（迁移 11 起）；旧备份缺字段取 0.0，导入时可从 JSON 回填 */
    val fatRatio: Double = 0.0,
    val muscleRatio: Double = 0.0,
    val waterRatio: Double = 0.0,
)

@Serializable
data class DietRecordBackup(
    val date: String,
    val timestamp: Long,
    val mealType: String,
    val imageFileName: String = "",
    val userInput: String = "",
    val recognizedFoodJson: String = "",
    val estimatedCalories: Int = 0,
    val trafficLight: String = "",
)

@Serializable
data class SettingsBackup(
    val height: Double,
    val targetWeight: Double,
    /** 手动设置的目标起始体重；0.0 表示未设置（跟随第一条记录），旧备份缺字段时取默认值 */
    val startWeight: Double = 0.0,
    /** 个人档案（热量建议用）；0 / 空串表示未设置，旧备份缺字段时取默认值 */
    val age: Int = 0,
    val gender: String = "",
    val activityLevel: String = "",
    /*--------以下设置 v1.1 起入包；旧备份缺字段时保持默认，导入侧据此跳过覆盖--------*/
    /** 每日提醒开关；null 表示旧备份未含（Boolean 无"未设置"态，用可空区分 false 与缺失） */
    val reminderEnabled: Boolean? = null,
    /** 提醒时间 HH:mm；空串表示未设置 */
    val reminderTime: String = "",
    /** 周报推送开关；null 表示旧备份未含 */
    val weeklyReportPushEnabled: Boolean? = null,
    /** 周报推送时间（每周一）HH:mm；空串表示未设置 */
    val weeklyReportPushTime: String = "",
    /** AI 模型 ID；空串表示未设置（不覆盖本机选择） */
    val doubaoModelId: String = "",
    /** 主题预设 [com.example.weight.ui.theme.ThemePreset].name；空串/非法值不覆盖 */
    val themeId: String = "",
    /** 深浅模式 [com.example.weight.ui.theme.AppearanceMode].name；空串/非法值不覆盖 */
    val appearanceMode: String = "",
    /** 每日统计口径 [com.example.weight.data.record.DailyStatMode].name；空串/非法值不覆盖 */
    val dailyStatMode: String = "",
)
