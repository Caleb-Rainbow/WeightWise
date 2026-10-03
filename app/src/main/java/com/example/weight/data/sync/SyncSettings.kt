package com.example.weight.data.sync

import com.example.weight.data.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/** All persistent user preferences; credentials and transient UI state are excluded. */
object SyncSettings {
    fun defaults(): JsonObject = buildJsonObject {
        put("height", DEFAULT_HEIGHT)
        put("age", DEFAULT_AGE)
        put("gender", "")
        put("activityLevel", "")
        put("targetWeight", DEFAULT_TARGET_WEIGHT)
        put("startWeight", DEFAULT_START_WEIGHT)
        put("weeklyTargetChangeKg", DEFAULT_WEEKLY_TARGET_CHANGE_KG)
        put("stageGoalStepKg", DEFAULT_STAGE_GOAL_STEP_KG)
        put("currentWaistCm", 0.0)
        put("currentWaistMeasuredAt", 0L)
        put("targetWaistCm", 0.0)
        put("targetBodyFatPercent", 0.0)
        put("reminderEnabled", false)
        put("reminderTime", "07:30")
        put("weeklyReportPushEnabled", false)
        put("weeklyReportPushTime", "08:00")
        put("doubaoModelId", "doubao-seed-2-0-lite-260215")
        put("themeId", "STEEL_BLUE")
        put("appearanceMode", "SYSTEM")
        put("dailyStatMode", "MIN")
    }
    fun snapshot(): JsonObject = buildJsonObject {
        put("height", LocalStorageData.height.value)
        put("age", LocalStorageData.age.value)
        put("gender", LocalStorageData.gender.value)
        put("activityLevel", LocalStorageData.activityLevel.value)
        put("targetWeight", LocalStorageData.targetWeight.value)
        put("startWeight", LocalStorageData.startWeight.value)
        put("weeklyTargetChangeKg", LocalStorageData.weeklyTargetChangeKg.value)
        put("stageGoalStepKg", LocalStorageData.stageGoalStepKg.value)
        put("currentWaistCm", LocalStorageData.currentWaistCm.value)
        put("currentWaistMeasuredAt", LocalStorageData.currentWaistMeasuredAt.value)
        put("targetWaistCm", LocalStorageData.targetWaistCm.value)
        put("targetBodyFatPercent", LocalStorageData.targetBodyFatPercent.value)
        put("reminderEnabled", LocalStorageData.reminderEnabled.value)
        put("reminderTime", LocalStorageData.reminderTime.value)
        put("weeklyReportPushEnabled", LocalStorageData.weeklyReportPushEnabled.value)
        put("weeklyReportPushTime", LocalStorageData.weeklyReportPushTime.value)
        put("doubaoModelId", LocalStorageData.doubaoModelId.value)
        put("themeId", LocalStorageData.themeId.value)
        put("appearanceMode", LocalStorageData.appearanceMode.value)
        put("dailyStatMode", LocalStorageData.dailyStatMode.value)
    }
    fun restore(value: JsonObject) {
        LocalStorageData.height.value = value["height"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_HEIGHT
        LocalStorageData.age.value = value["age"]?.jsonPrimitive?.intOrNull ?: DEFAULT_AGE
        LocalStorageData.gender.value = value["gender"]?.jsonPrimitive?.contentOrNull ?: ""
        LocalStorageData.activityLevel.value = value["activityLevel"]?.jsonPrimitive?.contentOrNull ?: ""
        LocalStorageData.targetWeight.value = value["targetWeight"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_TARGET_WEIGHT
        LocalStorageData.startWeight.value = value["startWeight"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_START_WEIGHT
        LocalStorageData.weeklyTargetChangeKg.value = value["weeklyTargetChangeKg"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_WEEKLY_TARGET_CHANGE_KG
        LocalStorageData.stageGoalStepKg.value = value["stageGoalStepKg"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_STAGE_GOAL_STEP_KG
        LocalStorageData.currentWaistMeasuredAt.value = 0L
        LocalStorageData.currentWaistCm.value = value["currentWaistCm"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        LocalStorageData.currentWaistMeasuredAt.value = value["currentWaistMeasuredAt"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: 0L
        LocalStorageData.targetWaistCm.value = value["targetWaistCm"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        LocalStorageData.targetBodyFatPercent.value = value["targetBodyFatPercent"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        LocalStorageData.reminderEnabled.value = value["reminderEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
        LocalStorageData.reminderTime.value = value["reminderTime"]?.jsonPrimitive?.contentOrNull ?: "07:30"
        LocalStorageData.weeklyReportPushEnabled.value = value["weeklyReportPushEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
        LocalStorageData.weeklyReportPushTime.value = value["weeklyReportPushTime"]?.jsonPrimitive?.contentOrNull ?: "08:00"
        LocalStorageData.doubaoModelId.value = value["doubaoModelId"]?.jsonPrimitive?.contentOrNull ?: "doubao-seed-2-0-lite-260215"
        LocalStorageData.themeId.value = value["themeId"]?.jsonPrimitive?.contentOrNull ?: "STEEL_BLUE"
        LocalStorageData.appearanceMode.value = value["appearanceMode"]?.jsonPrimitive?.contentOrNull ?: "SYSTEM"
        LocalStorageData.dailyStatMode.value = value["dailyStatMode"]?.jsonPrimitive?.contentOrNull ?: "MIN"
    }
    fun changes(): Flow<Unit> = merge(
        LocalStorageData.height.map { Unit },
        LocalStorageData.age.map { Unit },
        LocalStorageData.gender.map { Unit },
        LocalStorageData.activityLevel.map { Unit },
        LocalStorageData.targetWeight.map { Unit },
        LocalStorageData.startWeight.map { Unit },
        LocalStorageData.weeklyTargetChangeKg.map { Unit },
        LocalStorageData.stageGoalStepKg.map { Unit },
        LocalStorageData.currentWaistCm.map { Unit },
        LocalStorageData.currentWaistMeasuredAt.map { Unit },
        LocalStorageData.targetWaistCm.map { Unit },
        LocalStorageData.targetBodyFatPercent.map { Unit },
        LocalStorageData.reminderEnabled.map { Unit },
        LocalStorageData.reminderTime.map { Unit },
        LocalStorageData.weeklyReportPushEnabled.map { Unit },
        LocalStorageData.weeklyReportPushTime.map { Unit },
        LocalStorageData.doubaoModelId.map { Unit },
        LocalStorageData.themeId.map { Unit },
        LocalStorageData.appearanceMode.map { Unit },
        LocalStorageData.dailyStatMode.map { Unit }
    )
}
