package com.example.weight.data.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * APK 安装器：通过 FileProvider + Intent 调起系统安装程序。
 *
 * 需在 AndroidManifest 声明 `REQUEST_INSTALL_PACKAGES` 权限与 FileProvider；
 * FileProvider 只暴露 external-cache/update/ 子目录（见 file_paths.xml 收窄决策）。
 */
object ApkInstaller {

    private const val TAG = "WW-Update"
    private const val UPDATE_DIR = "update"
    private const val APK_NAME = "weight_wise_update.apk"

    /** 下载目标文件（app 外部缓存 update/ 子目录）。 */
    fun apkFile(context: Context): File =
        File(File(context.externalCacheDir, UPDATE_DIR).apply { mkdirs() }, APK_NAME)

    /**
     * 清理缓存的安装包：App 启动时调用（安装已完成/上次下载残留），
     * 以及每次重新下载前调用，保证缓存目录不留旧包。
     */
    fun cleanCache(context: Context) {
        val dir = File(context.externalCacheDir, UPDATE_DIR)
        if (dir.exists()) {
            dir.deleteRecursively()
            Log.d(TAG, "清理安装包缓存：${dir.absolutePath}")
        }
    }

    /**
     * 调起系统安装程序安装指定 APK 文件。
     * @return true 表示 Intent 已发出
     */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists()) {
            Log.w(TAG, "APK 文件不存在：${apk.absolutePath}")
            return false
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apk)
        Log.d(TAG, "安装 APK：${apk.absolutePath}  uri=$uri")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "启动安装程序失败：${e.message}", e)
            false
        }
    }
}
