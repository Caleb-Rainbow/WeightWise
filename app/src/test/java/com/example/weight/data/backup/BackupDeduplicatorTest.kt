package com.example.weight.data.backup

import com.example.weight.data.diet.DietRecordDedupKey
import com.example.weight.data.record.RecordDedupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupDeduplicatorTest {

    @Test
    fun `体重按时间戳与体重去重`() {
        val existing = listOf(RecordDedupKey(timestamp = 100L, weight = 75.0))
        val incoming = listOf(
            RecordBackup(weight = 75.0, log = "", timestamp = 100L), // 重复，跳过
            RecordBackup(weight = 74.0, log = "new", timestamp = 100L), // 同时间不同体重，保留
            RecordBackup(weight = 73.0, log = "", timestamp = 200L), // 新记录
        )
        val result = BackupDeduplicator.filterNewRecords(existing, incoming)
        assertEquals(2, result.toInsert.size)
        assertEquals(1, result.skippedCount)
        assertTrue(result.toInsert.none { it.weight == 75.0 })
    }

    @Test
    fun `非法体重或时间戳被丢弃`() {
        val incoming = listOf(
            RecordBackup(weight = 0.0, timestamp = 100L),
            RecordBackup(weight = -1.0, timestamp = 100L),
            RecordBackup(weight = 70.0, timestamp = 0L),
        )
        val result = BackupDeduplicator.filterNewRecords(emptyList(), incoming)
        assertTrue(result.toInsert.isEmpty())
        assertEquals(3, result.skippedCount)
    }

    @Test
    fun `饮食按日期时间戳餐型去重`() {
        val existing = listOf(
            DietRecordDedupKey(date = "2026-08-20", timestamp = 100L, mealType = "LUNCH")
        )
        val incoming = listOf(
            DietRecordBackup(date = "2026-08-20", timestamp = 100L, mealType = "LUNCH"), // 重复
            DietRecordBackup(date = "2026-08-20", timestamp = 100L, mealType = "DINNER"), // 同刻不同餐
            DietRecordBackup(date = "2026-08-21", timestamp = 300L, mealType = "LUNCH", imageFileName = "123.jpg"),
        )
        val result = BackupDeduplicator.filterNewDietRecords(existing, incoming) { name -> "/data/$name" }
        assertEquals(2, result.toInsert.size)
        assertEquals(1, result.skippedCount)
        // 图片文件名经回调映射成路径
        assertEquals("/data/123.jpg", result.toInsert.last { it.mealType == "LUNCH" }.imageUri)
    }

    @Test
    fun `备份序列化round-trip保字段`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val backup = BackupFile(
            exportedAt = 1_700_000_000_000L,
            records = listOf(RecordBackup(weight = 75.5, log = "晨重", timestamp = 100L)),
            dietRecords = listOf(
                DietRecordBackup(
                    date = "2026-08-20", timestamp = 100L, mealType = "LUNCH",
                    imageFileName = "123.jpg", userInput = "牛肉面",
                    recognizedFoodJson = "[{}]", estimatedCalories = 550, trafficLight = "YELLOW",
                )
            ),
            settings = SettingsBackup(height = 175.0, targetWeight = 70.0),
        )
        val decoded = json.decodeFromString(BackupFile.serializer(), json.encodeToString(BackupFile.serializer(), backup))
        assertEquals(backup, decoded)
        assertEquals(BackupFile.SCHEMA_VERSION, decoded.schemaVersion)
    }
}
