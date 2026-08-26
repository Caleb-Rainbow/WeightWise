package com.example.weight.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.weight.data.AppDataBase
import com.example.weight.data.LocalStorageData
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import com.example.weight.data.reminder.ReminderScheduler
import com.example.weight.data.report.ReportPushScheduler
import com.example.weight.data.widget.WidgetUpdater
import com.example.weight.ui.theme.AppearanceMode
import com.example.weight.ui.theme.ThemePreset
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.koin.core.annotation.Single
import com.example.weight.util.ActivityLevel
import com.example.weight.util.Gender
import com.example.weight.util.ImageCompressor
import com.example.weight.util.TimeUtils
import java.util.Locale

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
    private val widgetUpdater: WidgetUpdater,
    private val json: Json,
) {

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun export(context: Context, uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        val records = recordDao.getAllOnce()
        val dietRecords = dietRecordDao.getAllOnce()
        val backup = BackupFile(
            exportedAt = System.currentTimeMillis(),
            records = records.map {
                RecordBackup(
                    it.weight, it.log, it.timestamp, it.bodyComposition,
                    it.fatRatio, it.muscleRatio, it.waterRatio,
                )
            },
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
                startWeight = LocalStorageData.startWeight.value,
                age = LocalStorageData.age.value,
                gender = LocalStorageData.gender.value,
                activityLevel = LocalStorageData.activityLevel.value,
                reminderEnabled = LocalStorageData.reminderEnabled.value,
                reminderTime = LocalStorageData.reminderTime.value,
                weeklyReportPushEnabled = LocalStorageData.weeklyReportPushEnabled.value,
                weeklyReportPushTime = LocalStorageData.weeklyReportPushTime.value,
                doubaoModelId = LocalStorageData.doubaoModelId.value,
                themeId = LocalStorageData.themeId.value,
                appearanceMode = LocalStorageData.appearanceMode.value,
            ),
        )
        // encodeToStream 直接写输出流，避免在内存里再持有一份完整 JSON 字符串
        context.contentResolver.openOutputStream(uri)?.use { out ->
            json.encodeToStream(BackupFile.serializer(), backup, out)
            out.flush()
        } ?: throw IllegalStateException("无法写入所选位置")
        ExportResult(records.size, dietRecords.size)
    }

    /** 读取并解析备份文件，格式或版本不合法时抛 [BackupException] */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun parseBackup(context: Context, uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val backup = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                json.decodeFromStream(BackupFile.serializer(), input)
            } ?: throw BackupException("无法读取所选文件")
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("文件格式不正确，不是有效的 WeightWise 备份")
        }
        if (backup.schemaVersion > BackupFile.SCHEMA_VERSION) {
            throw BackupException("备份来自更新版本的应用（v${backup.schemaVersion}），请先升级 App 再导入")
        }
        backup
    }

    /** 与库内数据比对，计算去重后的导入预览，供用户确认；去重用轻量投影，不全量加载记录 */
    suspend fun previewImport(backup: BackupFile): ImportPreview = withContext(Dispatchers.IO) {
        val recordDedup = BackupDeduplicator.filterNewRecords(recordDao.getDedupKeys(), backup.records)
        val dietDedup = BackupDeduplicator.filterNewDietRecords(
            dietRecordDao.getDedupKeys(), backup.dietRecords,
        ) { fileName -> "" } // 预览阶段只数数量，不解析图片路径
        ImportPreview(
            backup = backup,
            newRecordCount = recordDedup.toInsert.size,
            skippedRecordCount = recordDedup.skippedCount,
            newDietCount = dietDedup.toInsert.size,
            skippedDietCount = dietDedup.skippedCount,
        )
    }

    /**
     * 执行导入：在 Room 事务内重取去重键、过滤并写入（预览到确认之间可能发生写入，
     * 事务保证快照一致且失败整体回滚）；随后应用设置项。
     */
    suspend fun importBackup(context: Context, preview: ImportPreview): ImportResult = withContext(Dispatchers.IO) {
        val backup = preview.backup
        val (recordDedup, dietDedup) = appDataBase.withTransaction {
            val newRecords = BackupDeduplicator.filterNewRecords(recordDao.getDedupKeys(), backup.records)
            val newDietRecords = BackupDeduplicator.filterNewDietRecords(
                dietRecordDao.getDedupKeys(), backup.dietRecords,
            ) { fileName -> resolveImagePath(context, fileName) }
            if (newRecords.toInsert.isNotEmpty()) recordDao.insertAll(newRecords.toInsert)
            if (newDietRecords.toInsert.isNotEmpty()) dietRecordDao.insertAll(newDietRecords.toInsert)
            newRecords to newDietRecords
        }

        val settings = backup.settings
        val settingsApplied = settings.height > 0.0 || settings.targetWeight > 0.0 ||
                settings.startWeight > 0.0 || settings.age > 0 ||
                settings.gender.isNotEmpty() || settings.activityLevel.isNotEmpty() ||
                settings.reminderEnabled != null || settings.weeklyReportPushEnabled != null ||
                settings.reminderTime.isNotEmpty() || settings.weeklyReportPushTime.isNotEmpty() ||
                settings.doubaoModelId.isNotEmpty() ||
                settings.themeId.isNotEmpty() || settings.appearanceMode.isNotEmpty()
        if (settings.height > 0.0) LocalStorageData.height.update { settings.height }
        if (settings.targetWeight > 0.0) LocalStorageData.targetWeight.update { settings.targetWeight }
        // 起始体重未设置（<=0）不覆盖本机已手动设置的值，与身高/目标体重口径一致
        if (settings.startWeight > 0.0) LocalStorageData.startWeight.update { settings.startWeight }
        // 档案字段同理：未设置不覆盖；枚举存 name，导入时校验合法性
        if (settings.age > 0) LocalStorageData.age.update { settings.age }
        if (Gender.entries.any { it.name == settings.gender }) {
            LocalStorageData.gender.update { settings.gender }
        }
        if (ActivityLevel.entries.any { it.name == settings.activityLevel }) {
            LocalStorageData.activityLevel.update { settings.activityLevel }
        }
        if (settings.doubaoModelId.isNotEmpty()) {
            LocalStorageData.doubaoModelId.update { settings.doubaoModelId }
        }
        // 提醒/周报开关 null=旧备份未含，整组跳过；恢复时刻须先落库再排程（schedule 默认读 MMKV 当前值）
        settings.reminderEnabled?.let { enabled ->
            if (settings.reminderTime.isNotEmpty()) {
                LocalStorageData.reminderTime.update { settings.reminderTime }
            }
            LocalStorageData.reminderEnabled.update { enabled }
            if (enabled) ReminderScheduler.schedule(context) else ReminderScheduler.cancel(context)
        }
        settings.weeklyReportPushEnabled?.let { enabled ->
            if (settings.weeklyReportPushTime.isNotEmpty()) {
                LocalStorageData.weeklyReportPushTime.update { settings.weeklyReportPushTime }
            }
            LocalStorageData.weeklyReportPushEnabled.update { enabled }
            if (enabled) ReportPushScheduler.schedule(context) else ReportPushScheduler.cancel(context)
        }
        // 主题外观：枚举校验合法才覆盖；变化后小组件需换色
        var themeTouched = false
        if (ThemePreset.entries.any { it.name == settings.themeId }) {
            LocalStorageData.themeId.update { settings.themeId }
            themeTouched = true
        }
        if (AppearanceMode.entries.any { it.name == settings.appearanceMode }) {
            LocalStorageData.appearanceMode.update { settings.appearanceMode }
            themeTouched = true
        }
        // 体重数据或主题变化，桌面小组件同步刷新
        if (recordDedup.toInsert.isNotEmpty() || themeTouched) {
            widgetUpdater.notifyDataChanged()
        }
        ImportResult(
            insertedRecords = recordDedup.toInsert.size,
            insertedDietRecords = dietDedup.toInsert.size,
            settingsApplied = settingsApplied,
        )
    }

    /**
     * 体重记录导出为 CSV（date,time,weight,log），带 UTF-8 BOM 防止 Excel 打开中文乱码。
     * 逐行流式写出，不在内存拼完整字符串。返回导出的记录条数。
     */
    suspend fun exportRecordsCsv(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val records = recordDao.getAllOnce()
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write("\uFEFF") // BOM
            writer.write("日期,时间,体重(kg),日志\n")
            for (record in records) {
                writer.write(TimeUtils.convertMillisToDate(record.timestamp))
                writer.write(",")
                writer.write(TimeUtils.convertMillisToHM(record.timestamp))
                writer.write(",")
                writer.write(String.format(Locale.CHINA, "%.1f", record.weight))
                writer.write(",")
                writer.write(escapeCsvField(record.log))
                writer.write("\n")
            }
            writer.flush()
        } ?: throw IllegalStateException("无法写入所选位置")
        records.size
    }

    /** 含逗号/引号/换行的字段用双引号包裹并把内部引号翻倍 */
    private fun escapeCsvField(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    companion object {
        /** 导出文件的默认名，如 weightwise_backup_2026-08-20.json */
        fun defaultExportFileName(today: String): String = "weightwise_backup_$today.json"

        /** CSV 导出默认文件名 */
        fun defaultCsvFileName(today: String): String = "weightwise_$today.csv"

        /** 备份中记录的图片文件名映射回本机持久路径（文件不存在时界面按无图展示） */
        fun resolveImagePath(context: Context, fileName: String): String {
            if (fileName.isBlank()) return ""
            return File(File(context.filesDir, ImageCompressor.DIET_IMAGE_DIR), fileName).absolutePath
        }
    }
}
