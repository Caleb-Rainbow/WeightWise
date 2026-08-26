package com.example.weight.data.backup

import kotlinx.serialization.Serializable

/**
 * 全量备份文件结构（JSON）。
 * 饮食图片文件不入包，仅保留文件名；恢复后图片缺失时界面按无图展示。
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
    /** 手动设置的目标起始体重；0.0 表示未设置（跟随第一条记录），旧备份缺该字段时取默认值 */
    val startWeight: Double = 0.0,
    /** 个人档案（热量建议用）；0 / 空串表示未设置，旧备份缺字段时取默认值 */
    val age: Int = 0,
    val gender: String = "",
    val activityLevel: String = "",
)
