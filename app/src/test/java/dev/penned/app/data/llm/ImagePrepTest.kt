package dev.penned.app.data.llm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * 截图进模型前的本地准备（M4-7 / R9）：**长边 1280 / JPEG 85** 的压缩口径、非图片给 null、
 * base64 可解回 JPEG。Robolectric 挂 NATIVE 图形（真位图运算）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagePrepTest {

    /** 有噪声的图：纯色图的 JPEG 不管质量多少都一样大，钉不住"按 85 压"这条口径。 */
    private fun noisy(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(42)
        val pixels = IntArray(width * height) { random.nextInt() or (0xFF shl 24) }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(200, 30, 30))
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    @Test
    fun `大图缩到长边 1280`() {
        val prepared = ImagePrep.prepare(png(3000, 2000))!!
        assertEquals(1280, maxOf(prepared.width, prepared.height))
        assertEquals(853, minOf(prepared.width, prepared.height)) // 等比（2000 × 1280/3000）
    }

    @Test
    fun `竖图同样按长边缩`() {
        val prepared = ImagePrep.prepare(png(1080, 2400))!!
        assertEquals(1280, maxOf(prepared.width, prepared.height))
    }

    @Test
    fun `小图不放大`() {
        val prepared = ImagePrep.prepare(png(400, 300))!!
        assertEquals(400, prepared.width)
        assertEquals(300, prepared.height)
    }

    @Test
    fun `长边正好 1280 时不缩`() {
        val prepared = ImagePrep.prepare(png(1280, 720))!!
        assertEquals(1280, prepared.width)
        assertEquals(720, prepared.height)
    }

    @Test
    fun `缩放比例纯函数`() {
        assertEquals(1f, ImagePrep.scaleFactor(1280, 720), 1e-6f)
        assertEquals(0.5f, ImagePrep.scaleFactor(2560, 1440), 1e-6f)
        assertEquals(1280f / 3000f, ImagePrep.scaleFactor(3000, 2000), 1e-6f)
    }

    @Test
    fun `base64 是 JPEG 且按 85 质量压`() {
        val prepared = ImagePrep.prepare(png(2000, 1500))!!
        val bytes = android.util.Base64.decode(prepared.base64, android.util.Base64.NO_WRAP)
        // JPEG 魔数 FF D8
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(decoded)
        assertEquals(1280, maxOf(decoded.width, decoded.height))
        // 口径就是质量 85：**有噪声的图**按 100 压出来一定更大
        // （别拿纯色图比：PNG 对纯色更小、JPEG 在任意质量下都一样大，两种比法都钉不住口径）
        val bitmap = ImagePrep.decodeAndScale(noisy(2000, 1500))!!
        val at85 = ImagePrep.toJpegBase64(bitmap, ImagePrep.JPEG_QUALITY)
        val at100 = ImagePrep.toJpegBase64(bitmap, 100)
        assertTrue("85 应比 100 小", at85.length < at100.length)
        assertEquals(85, ImagePrep.JPEG_QUALITY)
    }

    @Test
    fun `不是图片给 null`() {
        assertNull(ImagePrep.prepare("这不是图片".toByteArray()))
        assertNull(ImagePrep.prepare(ByteArray(0)))
    }
}
