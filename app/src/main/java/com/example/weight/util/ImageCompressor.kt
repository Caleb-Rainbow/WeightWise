package com.example.weight.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 *@description: 图片压缩 + 缓存持久化工具
 *@author: 杨帅林
 *@create: 2026/4/11
 **/
object ImageCompressor {

    private const val DIR_NAME = "diet_images"
    private const val DEFAULT_MAX_LONG_EDGE = 1024
    private const val DEFAULT_QUALITY = 80

    data class CompressionResult(
        val base64: String,
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val sizeBytes: Long,
    )

    /**
     * 从 URI 读取图片，压缩并编码为 Base64
     */
    suspend fun compressAndEncode(
        context: Context,
        uri: Uri,
        maxLongEdge: Int = DEFAULT_MAX_LONG_EDGE,
        quality: Int = DEFAULT_QUALITY,
    ): CompressionResult = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI: $uri")

        // Step 1: Decode bounds only
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, boundsOptions)
        inputStream.close()

        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight

        // Step 2: Calculate target size and sample size
        val (targetW, targetH) = calculateTargetSize(origW, origH, maxLongEdge)
        val sampleSize = calculateSampleSize(origW, origH, targetW, targetH)

        // Step 3: Decode with sample size
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val secondStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot reopen URI: $uri")
        val roughBitmap = BitmapFactory.decodeStream(secondStream, null, decodeOptions)
        secondStream.close()

        if (roughBitmap == null) {
            throw IllegalArgumentException("Failed to decode image from URI: $uri")
        }

        // Step 4: Scale to exact target size
        val scaledBitmap = scaleBitmap(roughBitmap, targetW, targetH)
        if (roughBitmap != scaledBitmap) roughBitmap.recycle()

        // Step 5: Compress to JPEG and Base64 encode
        val (base64, sizeBytes) = bitmapToBase64(scaledBitmap, quality)

        CompressionResult(
            base64 = base64,
            bitmap = scaledBitmap,
            width = scaledBitmap.width,
            height = scaledBitmap.height,
            sizeBytes = sizeBytes,
        )
    }

    /**
     * 对已获得的 Bitmap 直接压缩并编码为 Base64（用于 TakePicturePreview 返回的 Bitmap）
     */
    suspend fun compressBitmap(
        bitmap: Bitmap,
        maxLongEdge: Int = DEFAULT_MAX_LONG_EDGE,
        quality: Int = DEFAULT_QUALITY,
    ): CompressionResult = withContext(Dispatchers.IO) {
        val (targetW, targetH) = calculateTargetSize(bitmap.width, bitmap.height, maxLongEdge)
        val scaledBitmap = scaleBitmap(bitmap, targetW, targetH)
        if (bitmap != scaledBitmap) {
            // Don't recycle the original since caller may still need it
        }

        val (base64, sizeBytes) = bitmapToBase64(scaledBitmap, quality)

        CompressionResult(
            base64 = base64,
            bitmap = scaledBitmap,
            width = scaledBitmap.width,
            height = scaledBitmap.height,
            sizeBytes = sizeBytes,
        )
    }

    /**
     * 将 Bitmap 保存到应用内部缓存目录，返回 file:// URI 字符串
     */
    suspend fun saveToCache(
        context: Context,
        bitmap: Bitmap,
        quality: Int = DEFAULT_QUALITY,
    ): String = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()

        val fileName = "${System.currentTimeMillis()}.jpg"
        val file = File(dir, fileName)

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            fos.flush()
        }

        file.absolutePath
    }

    /**
     * 删除缓存中的图片文件
     */
    fun deleteCacheImage(imagePath: String) {
        if (imagePath.isBlank()) return
        val file = File(imagePath)
        if (file.exists() && file.absolutePath.contains(DIR_NAME)) {
            file.delete()
        }
    }

    private fun calculateTargetSize(origW: Int, origH: Int, maxLongEdge: Int): Pair<Int, Int> {
        if (origW <= 0 || origH <= 0) return maxLongEdge to maxLongEdge
        val longEdge = maxOf(origW, origH)
        if (longEdge <= maxLongEdge) return origW to origH
        val ratio = maxLongEdge.toFloat() / longEdge
        return (origW * ratio).toInt() to (origH * ratio).toInt()
    }

    private fun calculateSampleSize(origW: Int, origH: Int, targetW: Int, targetH: Int): Int {
        var inSampleSize = 1
        if (origH > targetH || origW > targetW) {
            val halfH = origH / 2
            val halfW = origW / 2
            while (halfH / inSampleSize >= targetH && halfW / inSampleSize >= targetW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleBitmap(bitmap: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (bitmap.width == targetW && bitmap.height == targetH) return bitmap
        val matrix = Matrix()
        val scaleX = targetW.toFloat() / bitmap.width
        val scaleY = targetH.toFloat() / bitmap.height
        matrix.setScale(scaleX, scaleY)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap, quality: Int): Pair<String, Long> {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val bytes = baos.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        baos.close()
        return base64 to bytes.size.toLong()
    }
}
