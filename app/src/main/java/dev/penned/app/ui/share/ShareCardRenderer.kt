package dev.penned.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import dev.penned.app.domain.ShareCardLayout
import dev.penned.app.ui.theme.BrandColors
import dev.penned.app.ui.theme.BrandPreview
import dev.penned.app.ui.theme.PennedBrands
import dev.penned.app.ui.theme.ThemeAccent
import java.io.File
import java.io.FileOutputStream

/**
 * 数据小卡片的渲染与分享（M3-16）。
 *
 * 自绘 Canvas → Bitmap → `cacheDir/cards/` 下的 PNG → FileProvider → `ACTION_SEND`。
 * **不引图片库、不写相册**（只走系统分享面板）；颜色全部来自**当前主题 token**
 * （`ShareCardLayout.CardRole` → `BrandPreview` 的映射在 [paletteOf]），换主题卡片跟着变。
 */
object ShareCardRenderer {

    /** 卡片落在 cacheDir 的子目录（FileProvider 的 `cards/` 路径）。 */
    const val DIR = "cards"

    /** 文字基线偏移：`ShareCardLayout` 给的是行顶 y，画字要基线。 */
    private const val BASELINE_FACTOR = 0.82f

    /** 语义角色 → 实际颜色（当前主题；深色主题时卡片也画深色底）。 */
    fun paletteOf(accent: ThemeAccent, darkTheme: Boolean): BrandPreview = PennedBrands.preview(accent, darkTheme)

    /** 画一张卡片（纯绘制，不碰文件；测试直接断言 Bitmap 尺寸与像素）。 */
    fun render(plan: ShareCardLayout.Plan, preview: BrandPreview): Bitmap {
        val bitmap = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val brand: BrandColors = preview.brand

        // 底：卡片底（浅色白 / 深色卡片色）
        canvas.drawColor(preview.surface.toArgb())
        // 顶部品牌条：一条 12px 的品牌色（换主题跟着变，也是"这是本 App 出的图"的标识）
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = brand.primary.toArgb()
        canvas.drawRect(0f, 0f, plan.width.toFloat(), 12f, paint)

        // 色块（进度条轨道 / 填充）
        plan.bars.forEach { bar ->
            paint.color = colorOf(bar.role, preview).toArgb()
            canvas.drawRect(bar.x.toFloat(), bar.y.toFloat(), (bar.x + bar.width).toFloat(), (bar.y + bar.height).toFloat(), paint)
        }

        // 占比环：圆心在左上区，半径 160px，描边 36px
        if (plan.segments.isNotEmpty()) {
            val cx = ShareCardLayout.DONUT_CX
            val cy = ShareCardLayout.DONUT_CY
            val radius = ShareCardLayout.DONUT_RADIUS
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 40f
            plan.segments.forEach { segment ->
                paint.color = pigmentColor(segment.slot, preview.dark).toArgb()
                canvas.drawArc(
                    cx - radius,
                    cy - radius,
                    cx + radius,
                    cy + radius,
                    segment.startFraction * 360f - 90f,
                    (segment.fraction * 360f).coerceAtLeast(0.5f),
                    false,
                    paint,
                )
            }
            paint.style = Paint.Style.FILL
        }

        // 文字
        plan.texts.forEach { text ->
            paint.color = colorOf(text.role, preview).toArgb()
            paint.textSize = text.sizePx.toFloat()
            paint.typeface = if (text.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(text.text, text.x.toFloat(), text.y + text.sizePx * BASELINE_FACTOR, paint)
        }
        return bitmap
    }

    /** 渲染并落盘到 `cacheDir/cards/<name>.png`，返回文件（覆盖同名旧文件）。 */
    fun writeToCache(context: Context, plan: ShareCardLayout.Plan, preview: BrandPreview, name: String): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { out ->
            render(plan, preview).compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    /**
     * 分享 Intent：`ACTION_SEND` + `image/png` + FileProvider URI + 读权限。
     * **只有这一处**拼 Intent（测试直接断言它的组成）。
     *
     * 拆成两层是因为 **Robolectric 读不到 FileProvider 的 `paths` 元数据**
     * （`getUriForFile` 会抛 "Failed to find configured root"，M2 就记过这条）：
     * 纯拼装那层（[shareIntentFor]）在单测里验，真 URI 的解析在模拟机走查里验。
     */
    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return shareIntentFor(uri)
    }

    /** 纯拼装（不碰 FileProvider）：测试断言 action / type / URI / 读权限。 */
    fun shareIntentFor(uri: android.net.Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** 拉起系统分享面板。 */
    fun share(context: Context, file: File) {
        val chooser = Intent.createChooser(shareIntent(context, file), "分享小卡片")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** 语义角色 → 颜色（按当前主题）。 */
    private fun colorOf(role: ShareCardLayout.CardRole, preview: BrandPreview): Color = when (role) {
        ShareCardLayout.CardRole.BRAND -> preview.brand.primary
        ShareCardLayout.CardRole.BRAND_INK -> preview.brand.primaryInk
        ShareCardLayout.CardRole.ON_SURFACE -> preview.onSurface
        ShareCardLayout.CardRole.ON_SURFACE_VARIANT -> preview.onSurfaceVariant
        ShareCardLayout.CardRole.EXPENSE -> preview.expense
        ShareCardLayout.CardRole.INCOME -> preview.brand.primaryInk
        ShareCardLayout.CardRole.SURFACE -> preview.onSurfaceVariant.copy(alpha = 0.15f)
    }

    /**
     * 占比环的槽位色：走分类颜料盘（与统计页环同一个口径：`PennedPigment.assignSlots` 的槽位 + 细件档 alpha）。
     * 渲染器不在 composition 里，所以直接用 `PennedPigment` 的原始色，不走 `pigmentMarkAt`（那是 @Composable）。
     */
    private fun pigmentColor(slot: Int, dark: Boolean): Color {
        val pigment = dev.penned.app.ui.theme.PennedPigment
        val base = if (dark) pigment.hueOnDark[slot % pigment.hueOnDark.size] else pigment.hue[slot % pigment.hue.size]
        val alpha = if (dark) pigment.MARK_ALPHA_DARK else pigment.MARK_ALPHA_LIGHT
        return base.copy(alpha = alpha)
    }

    /** 文件名的安全字符（卡片名是固定的英文串，这里再兜一层）。 */
    fun safeName(name: String): String = name.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
}
