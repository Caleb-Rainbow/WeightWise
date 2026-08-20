package com.example.weight.data.diet

import android.content.Context
import com.example.weight.util.ImageCompressor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 一次性迁移：历史版本的饮食图片存于 cacheDir/diet_images，系统或用户清缓存后
 * 图片丢失而饮食记录仍指向旧路径。启动时把旧目录里的文件挪到 filesDir/diet_images
 * 并回写数据库中的绝对路径。旧目录不存在或为空时是幂等空操作。
 */
object DietImageMigrator {

    suspend fun migrate(context: Context, dietRecordDao: DietRecordDao) = withContext(Dispatchers.IO) {
        val legacyDir = File(context.cacheDir, ImageCompressor.DIET_IMAGE_DIR)
        if (!legacyDir.isDirectory) return@withContext
        val legacyFiles = legacyDir.listFiles()
        if (legacyFiles.isNullOrEmpty()) {
            legacyDir.delete()
            return@withContext
        }

        val targetDir = File(context.filesDir, ImageCompressor.DIET_IMAGE_DIR).apply { mkdirs() }
        val updatedRecords = dietRecordDao.getRecordsWithImage().mapNotNull { record ->
            val oldFile = File(record.imageUri)
            if (oldFile.parentFile?.absolutePath != legacyDir.absolutePath) return@mapNotNull null
            val newFile = File(targetDir, oldFile.name)
            when {
                // 正常情况：文件还在，挪过去
                oldFile.exists() -> if (oldFile.renameTo(newFile)) record.copy(imageUri = newFile.absolutePath) else null
                // 文件已被清理但迁移副本已存在（重复启动）：仅回写新路径
                newFile.exists() -> record.copy(imageUri = newFile.absolutePath)
                // 图片已彻底丢失，保留原路径也无意义，跳过
                else -> null
            }
        }
        if (updatedRecords.isNotEmpty()) {
            dietRecordDao.updateAll(updatedRecords)
        }
        // 剩下没有对应记录的孤儿文件一并清掉
        legacyDir.deleteRecursively()
    }
}
