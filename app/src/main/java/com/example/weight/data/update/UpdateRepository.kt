package com.example.weight.data.update

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * 更新仓库：检查更新 + 下载 APK。
 *
 * 检查更新委托给 [remoteDataSource]，携带设备 ID 供服务端定向更新；
 * 下载 APK 使用独立的 [downloadClient]（长超时），写入 [ApkInstaller.apkFile]
 * 指定的外部缓存路径，下载完成后按服务端下发的 MD5 做完整性校验。
 *
 * 注意：OSS 下载 URL 故意不带 .apk 后缀（阿里云禁止经 OSS endpoint 分发 APK），
 * Content-Type 为 octet-stream，客户端按字节流落盘为 .apk 文件。
 */
class UpdateRepository(
    private val context: Context,
    private val downloadClient: OkHttpClient,
    private val remoteDataSource: UpdateRemoteDataSource,
) {

    /** 检查更新，失败返回 null。 */
    suspend fun checkUpdate(versionCode: Int): CheckUpdateResponse? =
        remoteDataSource.checkUpdate(UpdateConfig.SERVER_URL, versionCode, DeviceIdProvider.get())

    /**
     * 下载 APK 到外部缓存目录（下载前清理旧缓存文件）。
     * @param release 目标版本（含下载地址与 MD5）
     * @param onProgress 下载进度回调
     * @return 下载成功且 MD5 校验通过的文件，失败返回 null（失败时清理残留文件）
     */
    suspend fun downloadApk(release: ReleaseInfo, onProgress: (DownloadProgress) -> Unit): File? {
        // 清理上一次的残留（旧版本安装包 / 未完成的下载）
        ApkInstaller.cleanCache(context)

        val request = Request.Builder().url(release.download_url).get().build()
        Log.d(TAG, "开始下载 APK：${release.download_url}")

        return try {
            downloadClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "下载失败：HTTP ${resp.code}")
                    return null
                }
                val total = resp.body.contentLength()
                val file = ApkInstaller.apkFile(context)
                resp.body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead = 0L
                        var lastSpeedSampleTime = System.currentTimeMillis()
                        var lastSpeedSampleBytes = 0L
                        var speedBytesPerSec = 0L

                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read

                            // 每 500ms 采样一次速度，避免频繁刷新抖动
                            val now = System.currentTimeMillis()
                            val elapsed = now - lastSpeedSampleTime
                            if (elapsed >= 500) {
                                val bytesDelta = bytesRead - lastSpeedSampleBytes
                                speedBytesPerSec = if (elapsed > 0) bytesDelta * 1000 / elapsed else 0L
                                lastSpeedSampleTime = now
                                lastSpeedSampleBytes = bytesRead
                            }

                            if (total > 0) {
                                val percent = (bytesRead * 100 / total).toInt().coerceIn(0, 100)
                                val remaining = if (speedBytesPerSec > 0) {
                                    (total - bytesRead) / speedBytesPerSec
                                } else -1L
                                onProgress(DownloadProgress(percent, bytesRead, total, speedBytesPerSec, remaining))
                            } else {
                                // Content-Length 未知，只传已下载量
                                onProgress(DownloadProgress(0, bytesRead, -1, speedBytesPerSec, -1))
                            }
                        }
                    }
                }
                Log.d(TAG, "下载完成：${file.absolutePath} (${file.length()} bytes)")

                // 完整性校验：服务端下发的 MD5（防下载损坏/被篡改）
                val expected = release.file_md5.trim()
                if (expected.isNotBlank()) {
                    val actual = fileMd5(file)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        Log.e(TAG, "APK MD5 校验失败：expected=$expected actual=$actual，删除损坏文件")
                        file.delete()
                        return null
                    }
                    Log.d(TAG, "APK MD5 校验通过：$actual")
                }
                file
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载 APK 异常：${e.message}", e)
            null
        }
    }

    /** 流式计算文件 MD5（hex 小写）。 */
    private fun fileMd5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "WW-Update"
    }
}
