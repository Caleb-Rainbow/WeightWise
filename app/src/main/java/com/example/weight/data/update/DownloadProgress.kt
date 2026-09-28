package com.example.weight.data.update

/**
 * 下载进度信息。
 */
data class DownloadProgress(
    val percent: Int,            // 0-100
    val downloadedBytes: Long,   // 已下载字节数
    val totalBytes: Long,        // 总字节数（-1 表示未知）
    val speedBytesPerSec: Long,  // 当前下载速度（字节/秒）
    val remainingSeconds: Long,  // 预计剩余秒数（-1 表示未知）
)

/** 将字节数格式化为人类可读字符串，如 "1.2 MB"。 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "$bytes B" else String.format("%.1f %s", value, units[unitIndex])
}

/** 将速度格式化为人类可读字符串，如 "1.2 MB/s"。 */
fun formatSpeed(bytesPerSec: Long): String = "${formatBytes(bytesPerSec)}/s"

/** 将秒数格式化为剩余时间字符串，如 "剩余 30秒"、"剩余 1分20秒"。 */
fun formatRemaining(seconds: Long): String {
    if (seconds < 0) return "计算中…"
    if (seconds < 60) return "剩余 ${seconds}秒"
    val min = seconds / 60
    val sec = seconds % 60
    return "剩余 ${min}分${sec}秒"
}
