package com.example.weight.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * BackupZipCodec 编解码回归：round-trip、空图片包、Zip Slip 守卫、sha256 稳定性。
 * 纯 JVM 无 Android 依赖；BackupRepository 层的版本/校验编排不在此覆盖。
 */
class BackupZipCodecTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `roundTrip保留manifest数据与图片`() {
        val imgFile = tmp.newFile("1724000000000.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val dataJson = """{"schemaVersion":1}""".toByteArray()
        val manifest = BackupManifest(
            schemaVersion = BackupManifest.SCHEMA_VERSION,
            exportedAt = 100L,
            recordCount = 3, dietCount = 2, imageCount = 1,
            checksums = mapOf(
                BackupZipCodec.DATA_ENTRY to BackupZipCodec.sha256(dataJson),
                "images/1724000000000.jpg" to BackupZipCodec.sha256(imgFile.readBytes()),
            ),
        )
        val out = ByteArrayOutputStream()

        BackupZipCodec.write(out, dataJson, mapOf("1724000000000.jpg" to imgFile), manifest, json)

        val imageDir = tmp.newFolder("unpack")
        val parsed = BackupZipCodec.read(ByteArrayInputStream(out.toByteArray()), json, imageDir)
        assertEquals(manifest, parsed.manifest)
        assertArrayEquals(dataJson, parsed.dataJson)
        val unpacked = imageDir.listFiles()?.singleOrNull()
        assertEquals("1724000000000.jpg", unpacked?.name)
        assertArrayEquals(imgFile.readBytes(), unpacked?.readBytes())
    }

    @Test
    fun `空图片包imageDir为空目录`() {
        val dataJson = "{}".toByteArray()
        val manifest = BackupManifest(2, exportedAt = 0L, recordCount = 0, dietCount = 0, imageCount = 0)
        val out = ByteArrayOutputStream()

        BackupZipCodec.write(out, dataJson, emptyMap(), manifest, json)

        val imageDir = tmp.newFolder("empty")
        val parsed = BackupZipCodec.read(ByteArrayInputStream(out.toByteArray()), json, imageDir)
        assertEquals(0, parsed.imageDir?.listFiles()?.size)
        assertEquals(manifest, parsed.manifest)
    }

    @Test
    fun `重读时清空旧图片目录`() {
        val imageDir = tmp.newFolder("stale")
        tmp.newFile("stale/old.jpg").writeBytes(byteArrayOf(9))

        val dataJson = "{}".toByteArray()
        val manifest = BackupManifest(2, exportedAt = 0L, recordCount = 0, dietCount = 0, imageCount = 0)
        val out = ByteArrayOutputStream()
        BackupZipCodec.write(out, dataJson, emptyMap(), manifest, json)

        BackupZipCodec.read(ByteArrayInputStream(out.toByteArray()), json, imageDir)

        assertNull(imageDir.listFiles()?.firstOrNull { it.name == "old.jpg" })
    }

    @Test
    fun `带路径分隔的entry被丢弃防ZipSlip`() {
        // 手工构造含 images/../../evil.txt 的包
        val bos = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry(BackupZipCodec.MANIFEST_ENTRY))
            zip.write("""{"schemaVersion":2}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry(BackupZipCodec.DATA_ENTRY))
            zip.write("{}".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("images/../../evil.txt"))
            zip.write("evil".toByteArray())
            zip.closeEntry()
        }

        val imageDir = tmp.newFolder("guard")
        val parentBefore = imageDir.parentFile
        BackupZipCodec.read(ByteArrayInputStream(bos.toByteArray()), json, imageDir)

        // images/ 内没有解出任何文件，更没有逃逸到上级目录
        assertEquals(0, imageDir.listFiles()?.size)
        val escaped = parentBefore?.listFiles()?.firstOrNull { it.name == "evil.txt" }
        assertFalse(escaped?.exists() ?: false)
    }

    @Test
    fun `sha256对同一内容稳定`() {
        val b = "weightwise".toByteArray()
        assertEquals(BackupZipCodec.sha256(b), BackupZipCodec.sha256(b.clone()))
        assertEquals(64, BackupZipCodec.sha256(b).length)
    }

    @Test
    fun `缺manifest或data的包抛出`() {
        val bos = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("other.txt"))
            zip.write("x".toByteArray())
            zip.closeEntry()
        }
        try {
            BackupZipCodec.read(ByteArrayInputStream(bos.toByteArray()), json, null)
            throw AssertionError("应当抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("manifest"))
        }
    }
}
