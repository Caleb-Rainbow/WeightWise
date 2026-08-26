package com.example.weight.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SettingsBackup 序列化兼容：v1.0 备份（仅 6 个基础字段）必须能无损解码，
 * 新设置字段取「未设置」默认值；新格式 round-trip 不丢字段。
 * Json 配置对齐 KoinModule.provideJson（ignoreUnknownKeys + explicitNulls=false）。
 */
class SettingsBackupCompatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `旧版备份缺新字段解码为未设置态`() {
        val v10 = """{"height":170.0,"targetWeight":65.0,"startWeight":80.0,
            "age":30,"gender":"MALE","activityLevel":"LIGHT"}""".trimIndent()

        val settings = json.decodeFromString(SettingsBackup.serializer(), v10)

        assertEquals(170.0, settings.height, 1e-9)
        assertNull(settings.reminderEnabled)
        assertNull(settings.weeklyReportPushEnabled)
        assertEquals("", settings.reminderTime)
        assertEquals("", settings.weeklyReportPushTime)
        assertEquals("", settings.doubaoModelId)
        assertEquals("", settings.themeId)
        assertEquals("", settings.appearanceMode)
        assertNull(settings.weeklyTargetChangeKg)
        assertNull(settings.stageGoalStepKg)
        assertNull(settings.currentWaistCm)
        assertNull(settings.targetWaistCm)
        assertNull(settings.targetBodyFatPercent)
    }

    @Test
    fun `新格式roundTrip不丢字段`() {
        val original = SettingsBackup(
            height = 170.0, targetWeight = 65.0,
            reminderEnabled = true, reminderTime = "08:30",
            weeklyReportPushEnabled = false, weeklyReportPushTime = "09:00",
            doubaoModelId = "doubao-seed-2-0-lite-260215",
            themeId = "FOREST_GREEN", appearanceMode = "DARK",
            weeklyTargetChangeKg = 0.4, stageGoalStepKg = 2.5,
            currentWaistCm = 82.0, targetWaistCm = 76.0, targetBodyFatPercent = 18.0,
        )

        val decoded = json.decodeFromString(
            SettingsBackup.serializer(),
            json.encodeToString(SettingsBackup.serializer(), original),
        )

        assertEquals(original, decoded)
        // false 是合法值不能被序列化丢弃（区分「关」与「旧备份未含」依赖它存活）
        assertFalse(decoded.weeklyReportPushEnabled!!)
        assertTrue(decoded.reminderEnabled!!)
    }
}
