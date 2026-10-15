package dev.penned.app.ui.share

import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.domain.ShareCardLayout
import dev.penned.app.domain.StatsMath
import dev.penned.app.ui.theme.PennedBrands
import dev.penned.app.ui.theme.ThemeAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 数据小卡片（M3-16）：出图（尺寸/底/品牌条/文字像素）+ 落盘 + 分享 Intent 组成。
 * 出图只做"人工过目"的留档（Roborazzi），硬断言在尺寸/关键像素与 Intent 上。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardRenderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** 出图留档：写到 Roborazzi 的目录里（与其它基线同一个地方，人工过目）。 */
    private fun writePng(bitmap: android.graphics.Bitmap, name: String) {
        val file = File("build/test-results/roborazzi/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val summary = ShareCardLayout.monthSummary(
        monthLabel = "2026/10",
        incomeCents = 350_000L,
        expenseCents = 296_770L,
        budgetTotalCents = 200_000L,
        budgetSpentCents = 22_00L,
    )

    private val share = ShareCardLayout.categoryShare(
        periodLabel = "2026年9月",
        unit = "支出",
        totalCents = 296_770L,
        categories = listOf(
            StatsMath.CategoryTotal("食品餐饮", 180_000L, 110, 0.607f),
            StatsMath.CategoryTotal("购物消费", 80_000L, 15, 0.270f),
            StatsMath.CategoryTotal("其他", 35_570L, 4, 0.120f),
        ),
    )

    private val receipt = ShareCardLayout.receipt(
        categoryName = "食品餐饮 · 午餐",
        amountLabel = "-12.00",
        dateLabel = "2026年10月15日",
        note = "轻食",
        accountName = "微信",
        isExpense = true,
    )

    /** 三款卡片各出一张（浅色）；像素断言：画布尺寸 + 顶部品牌条 = 当前主题的 primary。 */
    @Test
    fun renderThreeCardsLight() {
        val preview = ShareCardRenderer.paletteOf(ThemeAccent.TEAL, darkTheme = false)
        listOf("m3_card_summary" to summary, "m3_card_share" to share, "m3_card_receipt" to receipt)
            .forEach { (name, plan) ->
                val bitmap = ShareCardRenderer.render(plan, preview)
                assertEquals(plan.width, bitmap.width)
                assertEquals(plan.height, bitmap.height)
                // 顶部品牌条（y=6 落在 12px 的条里）：颜色 = 品牌主色
                assertEquals(preview.brand.primary.toArgb(), bitmap.getPixel(plan.width / 2, 6))
                // 卡片底：右下角一处应是表面色（没有文字压在那里）
                assertEquals(preview.surface.toArgb(), bitmap.getPixel(plan.width - 20, plan.height - 20))
                // Roborazzi 没有 (path, Bitmap) 重载：自己写 PNG 到留档目录（人工过目用）
                writePng(bitmap, name)
            }
    }

    /** 换主题跟着变：赭石深色下底与品牌条都不是青绿浅色那套。 */
    @Test
    fun themeFollowsAccentAndDark() {
        val teal = ShareCardRenderer.render(summary, ShareCardRenderer.paletteOf(ThemeAccent.TEAL, darkTheme = false))
        val ochreDark = ShareCardRenderer.render(summary, ShareCardRenderer.paletteOf(ThemeAccent.OCHRE, darkTheme = true))
        assertTrue(teal.getPixel(summary.width - 20, summary.height - 20) != ochreDark.getPixel(summary.width - 20, summary.height - 20))
        assertEquals(
            PennedBrands.dark(ThemeAccent.OCHRE).primary.toArgb(),
            ochreDark.getPixel(summary.width / 2, 6),
        )
        writePng(ochreDark, "dark_card_summary")
    }

    /** 落盘：cacheDir/cards/<name>.png 存在且非空（FileProvider 的 cards/ 路径就指这里）。 */
    @Test
    fun writeToCacheLandsInCardsDir() {
        val file = ShareCardRenderer.writeToCache(
            context = context,
            plan = receipt,
            preview = ShareCardRenderer.paletteOf(ThemeAccent.TEAL, darkTheme = false),
            name = ShareCardRenderer.safeName("receipt"),
        )
        assertTrue(file.exists())
        assertTrue(file.length() > 1_000)
        assertEquals("cards", file.parentFile?.name)
        assertEquals(File(context.cacheDir, "cards"), file.parentFile)
        // 文件名过滤掉不安全字符
        assertEquals("receipt1", ShareCardRenderer.safeName("receipt/1"))
    }

    /**
     * 分享 Intent 组成：ACTION_SEND + image/png + 读权限 + EXTRA_STREAM 是那个 content URI。
     *
     * **不调 `FileProvider.getUriForFile`**：Robolectric 读不到 provider 的 `paths` 元数据
     * （实测 "Failed to find configured root that contains ..."），真 URI 的解析放到模拟机走查里验
     * （装包 → 首页「⋯」→ 分享 → 系统面板能拉起即证明 cards/ 配对了）。
     */
    @Test
    fun shareIntentComposition() {
        val uri = android.net.Uri.parse("content://dev.penned.app.fileprovider/cards/receipt.png")
        val intent = ShareCardRenderer.shareIntentFor(uri)
        assertEquals(android.content.Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val stream = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
        assertEquals(uri, stream)
        // FileProvider 的 authority 与路径段（与 file_paths.xml 的 cards/ 对齐）
        assertEquals("dev.penned.app.fileprovider", uri.authority)
        assertTrue(uri.path.orEmpty().contains("/cards/"))
    }
}
