package com.example.weight.ui.diet

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.weight.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File

/**
 *@description: B 行缩略图缓存(D13/OV8B):remember 随组合销毁,180 天历史快速滚动
 *               会每行重复走 IO 解码+EXIF 读取;VM 层 LruCache(~24 条 80px,约 600KB)
 *               让跨页面返回与滚动复用解码结果。LruCache 自行淘汰,无需手动清理
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
@Single
class DietThumbnailCache(private val application: Application) {

    private val cache = object : LruCache<String, ImageBitmap>(24) {}

    suspend fun get(path: String): ImageBitmap? {
        if (path.isBlank()) return null
        cache.get(path)?.let { return it }
        val bitmap = runCatching {
            ImageCompressor.decodeScaled(
                application,
                Uri.fromFile(File(path)),
                maxLongEdge = THUMB_LONG_EDGE,
            )
        }.onFailure { android.util.Log.w("DietThumb", "缩略图解码失败 $path", it) }
            .getOrNull()
            ?: // decodeScaled 内部已降采样;极端坏图再退一次原始 BitmapFactory 兜底。
            // 包进 IO：get() 的调用方协程在主线程时，全尺寸解码不能落在主线程
            runCatching {
                withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
            }.getOrNull()
            ?: return null
        val imageBitmap = bitmap.asImageBitmap()
        cache.put(path, imageBitmap)
        return imageBitmap
    }

    private companion object {
        const val THUMB_LONG_EDGE = 80
    }
}
