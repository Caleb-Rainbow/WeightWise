package com.example.weight.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ZIP 备份容器清单（容器格式 v2）。与 [BackupFile.SCHEMA_VERSION]（data.json 内容结构）
 * 分开演进：容器升级（如加密）不动内容版本，反之亦然。
 */
@Serializable
data class BackupManifest(
    val schemaVersion: Int,
    val app: String = "WeightWise",
    val exportedAt: Long = 0L,
    val recordCount: Int = 0,
    val dietCount: Int = 0,
    val imageCount: Int = 0,
    /** entry 相对路径 → SHA-256 hex；导入时逐一核对，防传输/存储损坏静默混入 */
    val checksums: Map<String, String> = emptyMap(),
) {
    companion object {
        const val SCHEMA_VERSION = 2
    }
}

/** 解包结果：manifest 与 data.json 原始字节；图片已解包到 [imageDir]（无图备份为 null） */
class ParsedZipBackup(
    val manifest: BackupManifest,
    val dataJson: ByteArray,
    val imageDir: File?,
)

/**
 * ZIP 备份编解码：manifest.json + data.json + images 目录。
 * 纯 Stream/File 操作不依赖 Android 框架，便于 JVM 单测；
 * 校验与业务编排（版本检查、hash 核对、落库）在 [BackupRepository]。
 */
object BackupZipCodec {

    const val MANIFEST_ENTRY = "manifest.json"
    const val DATA_ENTRY = "data.json"
    const val IMAGE_DIR = "images"

    fun write(
        output: OutputStream,
        dataJson: ByteArray,
        images: Map<String, File>,
        manifest: BackupManifest,
        json: Json,
    ) {
        ZipOutputStream(output.buffered()).use { zip ->
            putEntry(zip, MANIFEST_ENTRY, json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            putEntry(zip, DATA_ENTRY, dataJson)
            images.forEach { (name, file) ->
                putEntry(zip, entryName(name), file.readBytes())
            }
        }
    }

    /**
     * 解包到内存（manifest/data.json）与 [imageOutputDir]（图片文件，目录存在则先清空）。
     * entry 名带路径分隔符的图片一律丢弃（Zip Slip 守卫：合法图片名是纯文件名）。
     */
    fun read(input: InputStream, json: Json, imageOutputDir: File?): ParsedZipBackup {
        if (imageOutputDir != null) {
            imageOutputDir.mkdirs()
            imageOutputDir.listFiles()?.forEach { it.delete() }
        }
        var manifest: BackupManifest? = null
        var dataJson: ByteArray? = null
        ZipInputStream(input.buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == MANIFEST_ENTRY ->
                        manifest = json.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString())

                    entry.name == DATA_ENTRY ->
                        dataJson = zip.readBytes()

                    imageOutputDir != null && entry.name.startsWith("$IMAGE_DIR/") -> {
                        val fileName = entry.name.removePrefix("$IMAGE_DIR/")
                        if (fileName.isNotEmpty() && !fileName.contains('/') && !fileName.contains('\\')) {
                            File(imageOutputDir, fileName).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                entry = zip.nextEntry
            }
        }
        val m = manifest ?: throw IllegalArgumentException("备份缺少 $MANIFEST_ENTRY")
        val d = dataJson ?: throw IllegalArgumentException("备份缺少 $DATA_ENTRY")
        return ParsedZipBackup(m, d, imageOutputDir?.takeIf { it.exists() })
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** entry 顺序固定 manifest → data.json → images：解包端先读到清单即可做版本与校验决策 */
    private fun putEntry(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content)
        zip.closeEntry()
    }

    private fun entryName(imageFileName: String): String = "$IMAGE_DIR/$imageFileName"
}
