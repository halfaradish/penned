package dev.penned.app.data.llm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import android.util.Base64
import androidx.core.graphics.scale

/**
 * 截图进模型前的**本地准备**（M4-7）：解码 → 缩到长边上限 → 压成 JPEG → base64。
 *
 * 口径（规格 §三 M4-7 / R9）：**长边 1280 / JPEG 85**；原图**只落 `cacheDir` 临时文件、
 * 识别完即丢**（不留存）。零新增依赖：`BitmapFactory` + `Bitmap.compress`。
 *
 * 纯 Android 图形 API，没有隐藏状态——`ImagePrepTest` 用 Robolectric（NATIVE 图形）钉住口径。
 */
object ImagePrep {

    /** 长边上限（超出就等比缩，小了不动——放大只会更慢更糊）。 */
    const val MAX_LONG_SIDE = 1280

    /** JPEG 质量（与 M3-16 分享卡片的位图口径同族）。 */
    const val JPEG_QUALITY = 85

    /** 解码 + 缩放；解不出（不是图片/损坏）返回 null。 */
    fun decodeAndScale(bytes: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        val scale = scaleFactor(options.outWidth, options.outHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(scale)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null
        return scaleToLongSide(decoded, MAX_LONG_SIDE)
    }

    /** 缩放后 → JPEG base64（不带 data 前缀；`LlmClient.extractImage` 会自己拼）。 */
    fun toJpegBase64(bitmap: Bitmap, quality: Int = JPEG_QUALITY): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** 一步到位：字节 → 模型要的 base64（解不出返回 null）。 */
    fun prepare(bytes: ByteArray): PreparedImage? {
        val bitmap = decodeAndScale(bytes) ?: return null
        val base64 = toJpegBase64(bitmap)
        return PreparedImage(base64 = base64, width = bitmap.width, height = bitmap.height)
    }

    /** 需要的整体缩放比例（≤1）。 */
    fun scaleFactor(width: Int, height: Int, maxLongSide: Int = MAX_LONG_SIDE): Float {
        val longSide = max(width, height)
        return if (longSide <= maxLongSide) 1f else maxLongSide.toFloat() / longSide
    }

    /** `inSampleSize` 只能是 2 的幂：先把最靠近的比例取整，最后再精确缩一次。 */
    private fun sampleSizeFor(scale: Float): Int {
        if (scale >= 1f) return 1
        var sample = 1
        while (sample * 2 <= 1f / scale) sample *= 2
        return sample
    }

    /** 精确缩到长边 ≤ [maxLongSide]（比例已满足就原样返回）。 */
    private fun scaleToLongSide(bitmap: Bitmap, maxLongSide: Int): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= maxLongSide) return bitmap
        val ratio = maxLongSide.toFloat() / longSide
        val width = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = bitmap.scale(width, height)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}

/** 准备结果：base64 + 缩放后的尺寸（页面显示"这张图将发送给…"时顺带报一下）。 */
data class PreparedImage(val base64: String, val width: Int, val height: Int)
