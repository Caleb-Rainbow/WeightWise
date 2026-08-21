package com.example.weight.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 *@description: 图片压缩 + 缓存持久化工具
 *@author: 杨帅林
 *@create: 2026/4/11
 **/
object ImageCompressor {

    /** 饮食图片目录名。当前位于 filesDir；旧版本曾存于 cacheDir，由 DietImageMigrator 负责迁移 */
    const val DIET_IMAGE_DIR = "diet_images"

    /**
     * 拍照临时目录（cacheDir 下）。与 [DIET_IMAGE_DIR] 完全独立：
     * DietImageMigrator 只迁移 diet_images，本目录由饮食页进入时整体清理（见 file_paths.xml 注释）
     */
    const val CAMERA_CAPTURE_DIR = "camera_capture"
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
     * 从 URI 解码 → EXIF 方向摆正 → inSampleSize 降采样 → 精确缩放，不做压缩和 Base64 编码。
     * 预览渲染与保存路径只需 Bitmap 时用这个，避免白白执行 JPEG 压缩 + 大字符串编码。
     * 无法打开或解码失败返回 null。
     *
     * EXIF：BitmapFactory 不读方向标记，相机全尺寸 JPEG（TakePicture）与部分相册图带
     * ROTATE/FLIP 标记，不解码摆正会横躺 90°。摆正后预览/AI 输入/落盘图三者同源一致。
     *
     * bounds 探测失败（部分格式如 HEIC 在 inJustDecodeBounds 下返回 null 或 0 尺寸，
     * 但全尺寸解码却正常）时降级为单次全尺寸解码，保证预览可用。
     */
    suspend fun decodeScaled(
        context: Context,
        uri: Uri,
        maxLongEdge: Int = DEFAULT_MAX_LONG_EDGE,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val exifTransform = readExifTransform(context, uri)
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val boundsDecoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        } != null
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight

        // bounds 探测失败或尺寸无效：降级全尺寸解码（不再二次打开流）
        if (!boundsDecoded || origW <= 0 || origH <= 0) {
            val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return@withContext null
            return@withContext applyExifTransform(raw, exifTransform)
        }

        val rotatedSize = when (exifTransform.degrees) {
            90, 270 -> origH to origW
            else -> origW to origH
        }
        val (targetW, targetH) = calculateTargetSize(rotatedSize.first, rotatedSize.second, maxLongEdge)
        val sampleSize = calculateSampleSize(rotatedSize.first, rotatedSize.second, targetW, targetH)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val roughBitmap = context.contentResolver.openInputStream(uri)?.use { secondStream ->
            BitmapFactory.decodeStream(secondStream, null, decodeOptions)
        } ?: return@withContext null

        val uprightBitmap = applyExifTransform(roughBitmap, exifTransform)
        val scaledBitmap = scaleBitmap(uprightBitmap, targetW, targetH)
        if (uprightBitmap != scaledBitmap) uprightBitmap.recycle()
        scaledBitmap
    }

    /**
     * 从 URI 读取图片，压缩并编码为 Base64
     */
    suspend fun compressAndEncode(
        context: Context,
        uri: Uri,
        maxLongEdge: Int = DEFAULT_MAX_LONG_EDGE,
        quality: Int = DEFAULT_QUALITY,
    ): CompressionResult = withContext(Dispatchers.IO) {
        val scaledBitmap = decodeScaled(context, uri, maxLongEdge)
            ?: throw IllegalArgumentException("Failed to decode image from URI: $uri")

        // 压缩到 JPEG 并 Base64 编码
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
        // 缩放产生新实例；调用方可能仍持有原 bitmap，这里不回收原件
        val scaledBitmap = scaleBitmap(bitmap, targetW, targetH)

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
     * 将 Bitmap 保存到应用内部持久目录（filesDir/diet_images），返回 file:// 绝对路径字符串。
     * 不能存 cacheDir：系统清缓存会删掉图片，而饮食记录仍指向该路径
     */
    suspend fun saveImage(
        context: Context,
        bitmap: Bitmap,
        quality: Int = DEFAULT_QUALITY,
    ): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIET_IMAGE_DIR)
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
     * 删除已保存的饮食图片文件
     */
    fun deleteImage(imagePath: String) {
        if (imagePath.isBlank()) return
        val file = File(imagePath)
        if (file.exists() && file.absolutePath.contains(DIET_IMAGE_DIR)) {
            file.delete()
        }
    }

    /** 创建拍照输出文件（TakePicture 契约用），位于 cacheDir/[CAMERA_CAPTURE_DIR]，FileProvider 已收窄授权该目录 */
    fun createCameraCaptureFile(context: Context): File =
        File(File(context.cacheDir, CAMERA_CAPTURE_DIR).apply { mkdirs() }, "capture_${System.currentTimeMillis()}.jpg")

    /**
     * 清理拍照临时文件；饮食页 ON_RESUME 时调用，覆盖「拍了照未保存/进程死亡」的残留路径。
     * [keep] 为当前会话刚拍摄的文件——从相机返回正好触发 ON_RESUME，
     * 不排除会把刚拍的照片删掉（真机踩过的坑）
     */
    fun clearCameraCaptures(context: Context, keep: File? = null) {
        File(context.cacheDir, CAMERA_CAPTURE_DIR).listFiles()?.forEach { file ->
            if (file != keep) file.delete()
        }
    }

    private data class ExifTransform(val degrees: Int, val flipHorizontal: Boolean) {
        val isIdentity: Boolean get() = degrees == 0 && !flipHorizontal
    }

    private fun readExifTransform(context: Context, uri: Uri): ExifTransform = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
            when (
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> ExifTransform(90, false)
                ExifInterface.ORIENTATION_ROTATE_180 -> ExifTransform(180, false)
                ExifInterface.ORIENTATION_ROTATE_270 -> ExifTransform(270, false)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifTransform(0, true)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifTransform(180, true)
                ExifInterface.ORIENTATION_TRANSPOSE -> ExifTransform(90, true)
                ExifInterface.ORIENTATION_TRANSVERSE -> ExifTransform(270, true)
                else -> ExifTransform(0, false)
            }
        } ?: ExifTransform(0, false)
    }.getOrDefault(ExifTransform(0, false))

    private fun applyExifTransform(bitmap: Bitmap, transform: ExifTransform): Bitmap {
        if (transform.isIdentity) return bitmap
        val matrix = Matrix()
        matrix.postRotate(transform.degrees.toFloat())
        if (transform.flipHorizontal) matrix.postScale(-1f, 1f)
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (upright != bitmap) bitmap.recycle()
        return upright
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
