package com.example.weight.data.backup

import android.content.Context
import android.net.Uri
import com.example.weight.data.LocalStorageData
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.record.RecordDao
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import com.example.weight.util.ImageCompressor

data class ExportResult(val recordCount: Int, val dietCount: Int)

/**
 * 全量备份导出/导入仓库。
 * 文件格式见 [BackupFile]；图片文件不入包，仅保留文件名。
 */
@Single
class BackupRepository(
    private val recordDao: RecordDao,
    private val dietRecordDao: DietRecordDao,
    private val json: Json,
) {

    suspend fun export(context: Context, uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        val records = recordDao.getAllOnce()
        val dietRecords = dietRecordDao.getAllOnce()
        val backup = BackupFile(
            exportedAt = System.currentTimeMillis(),
            records = records.map { RecordBackup(it.weight, it.log, it.timestamp) },
            dietRecords = dietRecords.map { record ->
                DietRecordBackup(
                    date = record.date,
                    timestamp = record.timestamp,
                    mealType = record.mealType,
                    imageFileName = File(record.imageUri).name,
                    userInput = record.userInput,
                    recognizedFoodJson = record.recognizedFoodJson,
                    estimatedCalories = record.estimatedCalories,
                    trafficLight = record.trafficLight,
                )
            },
            settings = SettingsBackup(
                height = LocalStorageData.height.value,
                targetWeight = LocalStorageData.targetWeight.value,
            ),
        )
        val text = json.encodeToString(BackupFile.serializer(), backup)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IllegalStateException("无法写入所选位置")
        ExportResult(records.size, dietRecords.size)
    }

    companion object {
        /** 导出文件的默认名，如 weightwise_backup_2026-08-20.json */
        fun defaultExportFileName(today: String): String = "weightwise_backup_$today.json"

        /** 备份中记录的图片文件名映射回本机持久路径（文件不存在时界面按无图展示） */
        fun resolveImagePath(context: Context, fileName: String): String {
            if (fileName.isBlank()) return ""
            return File(File(context.filesDir, ImageCompressor.DIET_IMAGE_DIR), fileName).absolutePath
        }
    }
}
