package com.example.weight.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.weight.data.AppDataBase
import com.example.weight.data.LocalStorageData
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.record.RecordDao
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import com.example.weight.util.ImageCompressor

/** 备份文件不可读或内容非法时抛出，message 可直接展示给用户 */
class BackupException(message: String) : Exception(message)

data class ExportResult(val recordCount: Int, val dietCount: Int)

/** 导入前的确认预览：去重后的新增数量与跳过数量 */
data class ImportPreview(
    val backup: BackupFile,
    val newRecordCount: Int,
    val skippedRecordCount: Int,
    val newDietCount: Int,
    val skippedDietCount: Int,
)

data class ImportResult(
    val insertedRecords: Int,
    val insertedDietRecords: Int,
    val settingsApplied: Boolean,
)

/**
 * 全量备份导出/导入仓库。
 * 文件格式见 [BackupFile]；图片文件不入包，仅保留文件名。
 */
@Single
class BackupRepository(
    private val recordDao: RecordDao,
    private val dietRecordDao: DietRecordDao,
    private val appDataBase: AppDataBase,
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

    /** 读取并解析备份文件，格式或版本不合法时抛 [BackupException] */
    suspend fun parseBackup(context: Context, uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().decodeToString()
        } ?: throw BackupException("无法读取所选文件")
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw BackupException("文件格式不正确，不是有效的 WeightWise 备份")
        }
        if (backup.schemaVersion > BackupFile.SCHEMA_VERSION) {
            throw BackupException("备份来自更新版本的应用（v${backup.schemaVersion}），请先升级 App 再导入")
        }
        backup
    }

    /** 与库内数据比对，计算去重后的导入预览，供用户确认 */
    suspend fun previewImport(backup: BackupFile): ImportPreview = withContext(Dispatchers.IO) {
        val recordDedup = BackupDeduplicator.filterNewRecords(recordDao.getAllOnce(), backup.records)
        val dietDedup = BackupDeduplicator.filterNewDietRecords(
            dietRecordDao.getAllOnce(), backup.dietRecords,
        ) { fileName -> "" } // 预览阶段不关心图片路径
        ImportPreview(
            backup = backup,
            newRecordCount = recordDedup.toInsert.size,
            skippedRecordCount = recordDedup.skippedCount,
            newDietCount = dietDedup.toInsert.size,
            skippedDietCount = dietDedup.skippedCount,
        )
    }

    /** 执行导入：去重后在 Room 事务中写入，中途失败整体回滚；随后应用设置项 */
    suspend fun importBackup(context: Context, backup: BackupFile): ImportResult = withContext(Dispatchers.IO) {
        val recordDedup = BackupDeduplicator.filterNewRecords(recordDao.getAllOnce(), backup.records)
        val dietDedup = BackupDeduplicator.filterNewDietRecords(
            dietRecordDao.getAllOnce(), backup.dietRecords,
        ) { fileName -> resolveImagePath(context, fileName) }

        appDataBase.withTransaction {
            if (recordDedup.toInsert.isNotEmpty()) recordDao.insertAll(recordDedup.toInsert)
            if (dietDedup.toInsert.isNotEmpty()) dietRecordDao.insertAll(dietDedup.toInsert)
        }

        val settings = backup.settings
        val settingsApplied = settings != null && (settings.height > 0.0 || settings.targetWeight > 0.0)
        if (settings != null) {
            if (settings.height > 0.0) LocalStorageData.height.update { settings.height }
            if (settings.targetWeight > 0.0) LocalStorageData.targetWeight.update { settings.targetWeight }
        }
        ImportResult(
            insertedRecords = recordDedup.toInsert.size,
            insertedDietRecords = dietDedup.toInsert.size,
            settingsApplied = settingsApplied,
        )
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
