package dev.penned.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 对比度护栏（D043/D044，docs/界面规范.md 第三、四、十一节）：
 * 把界面规范里承诺过的每一对「前景 / 背景」都按 WCAG 2.1 的公式算一遍——
 * 正文与金额 ≥ 4.5:1、大字与承担识别职责的非文字件（图标/进度填充）≥ 3:1。
 *
 * 这一条是**改色值时会立刻变红**的那道门：颜色是观感的核心，但对比度是底线。
 * 另有 [tokens match the documented hexes] 把"代码里的值 = 规范文档里的值"钉住。
 */
class ContrastTest {

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.04045) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(fg: Color, bg: Color): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        val hi = maxOf(a, b)
        val lo = minOf(a, b)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** fg 以 alpha 叠在 bg 上（淡底、弱化文字用）。 */
    private fun over(fg: Color, bg: Color, alpha: Float): Color = Color(
        red = fg.red * alpha + bg.red * (1 - alpha),
        green = fg.green * alpha + bg.green * (1 - alpha),
        blue = fg.blue * alpha + bg.blue * (1 - alpha),
    )

    private data class ContrastPair(val label: String, val fg: Color, val bg: Color, val need: Double)

    private val body = 4.5
    private val nonText = 3.0

    private fun pairs(): List<ContrastPair> {
        val out = mutableListOf<ContrastPair>()
        fun add(label: String, fg: Color, bg: Color, need: Double = body) =
            out.add(ContrastPair(label, fg, bg, need))

        // —— 浅色：文字 ——
        add("onSurface / surface", OnSurfaceLight, SurfaceLight)
        add("onSurface / background", OnSurfaceLight, BackgroundLight)
        add("onSurface / surfaceContainer", OnSurfaceLight, SurfaceContainerLight)
        add("onSurfaceVariant / surface", OnSurfaceVariantLight, SurfaceLight)
        add("onSurfaceVariant / background", OnSurfaceVariantLight, BackgroundLight)
        add("onSurfaceVariant / surfaceContainer", OnSurfaceVariantLight, SurfaceContainerLight)
        add("白字 / primary（主按钮）", OnPrimaryLight, PrimaryLight)
        add("primaryInk / surface", PrimaryInkLight, SurfaceLight)
        add("primaryInk / background", PrimaryInkLight, BackgroundLight)
        add("primaryInk / primaryContainer", PrimaryInkLight, PrimaryContainerLight)
        add("onPrimaryContainer / primaryContainer", OnPrimaryContainerLight, PrimaryContainerLight)
        add(
            "日头条右侧 85% 收支 / primaryContainer",
            over(OnPrimaryContainerLight, PrimaryContainerLight, 0.85f),
            PrimaryContainerLight,
        )
        add("expense / surface", ExpenseLight, SurfaceLight)
        add("expense / background", ExpenseLight, BackgroundLight)
        add("income / surface", IncomeLight, SurfaceLight)
        add("error / surface", ErrorLight, SurfaceLight)
        add("onErrorContainer / errorContainer", OnErrorContainerLight, ErrorContainerLight)
        // —— 浅色：非文字（≥3:1）——
        add("iconMuted / surface", IconMutedLight, SurfaceLight, nonText)
        add("iconMuted / background", IconMutedLight, BackgroundLight, nonText)
        add("进度填充 primary / surface", PrimaryLight, SurfaceLight, nonText)
        add("进度填充 primary / 轨道 surfaceContainerHigh", PrimaryLight, SurfaceContainerHighLight, nonText)

        // —— 深色：文字 ——
        add("onSurface / surface（深）", OnSurfaceDark, SurfaceDark)
        add("onSurface / background（深）", OnSurfaceDark, BackgroundDark)
        add("onSurfaceVariant / surface（深）", OnSurfaceVariantDark, SurfaceDark)
        add("onSurfaceVariant / surfaceContainer（深）", OnSurfaceVariantDark, SurfaceContainerDark)
        add("onPrimary / primary（深）", OnPrimaryDark, PrimaryDark)
        add("primaryInk / surface（深）", PrimaryInkDark, SurfaceDark)
        add("primaryInk / background（深）", PrimaryInkDark, BackgroundDark)
        add("primaryInk / primaryContainer（深）", PrimaryInkDark, PrimaryContainerDark)
        add("onPrimaryContainer / primaryContainer（深）", OnPrimaryContainerDark, PrimaryContainerDark)
        add(
            "日头条右侧 85%（深）",
            over(OnPrimaryContainerDark, PrimaryContainerDark, 0.85f),
            PrimaryContainerDark,
        )
        add("expense / surface（深）", ExpenseDark, SurfaceDark)
        add("expense / background（深）", ExpenseDark, BackgroundDark)
        add("income / surface（深）", IncomeDark, SurfaceDark)
        add("error / surface（深）", ErrorDark, SurfaceDark)
        add("onErrorContainer / errorContainer（深）", OnErrorContainerDark, ErrorContainerDark)
        // —— 深色：非文字 ——
        add("iconMuted / surface（深）", IconMutedDark, SurfaceDark, nonText)
        add("进度填充 primary / surface（深）", PrimaryDark, SurfaceDark, nonText)

        // —— 分类颜料：ink 在本色淡底与两种页面底上都要成立（十二个槽各自核）——
        PennedPigment.light.forEachIndexed { i, ink ->
            add("颜料$i 浅 ink / surface", ink, SurfaceLight)
            add("颜料$i 浅 ink / background", ink, BackgroundLight)
            add(
                "颜料$i 浅 ink / 12% 淡底",
                ink,
                over(ink, SurfaceLight, PennedPigment.TINT_ALPHA_LIGHT),
            )
        }
        add("中性颜料 浅 / surface", PennedPigment.neutralLight, SurfaceLight)
        add("中性颜料 浅 / background", PennedPigment.neutralLight, BackgroundLight)
        add(
            "中性颜料 浅 / 12% 淡底",
            PennedPigment.neutralLight,
            over(PennedPigment.neutralLight, SurfaceLight, PennedPigment.TINT_ALPHA_LIGHT),
        )
        PennedPigment.dark.forEachIndexed { i, ink ->
            add("颜料$i 深 ink / surface", ink, SurfaceDark)
            add(
                "颜料$i 深 ink / 22% 淡底",
                ink,
                over(ink, SurfaceDark, PennedPigment.TINT_ALPHA_DARK),
            )
        }
        add("中性颜料 深 / surface", PennedPigment.neutralDark, SurfaceDark)
        add(
            "中性颜料 深 / 22% 淡底",
            PennedPigment.neutralDark,
            over(PennedPigment.neutralDark, SurfaceDark, PennedPigment.TINT_ALPHA_DARK),
        )
        return out
    }

    @Test
    fun `每一对前景背景都满足 WCAG 对比度下限`() {
        val failures = pairs().filter { contrast(it.fg, it.bg) < it.need }
            .joinToString("\n") {
                "  ${it.label}: %.2f（要 %.1f）".format(contrast(it.fg, it.bg), it.need)
            }
        assertTrue("以下配对的对比度不达标：\n$failures", failures.isEmpty())
    }

    private fun hex(c: Color): String = "#%02X%02X%02X".format(
        (c.red * 255).roundToInt(),
        (c.green * 255).roundToInt(),
        (c.blue * 255).roundToInt(),
    )

    @Test
    fun `tokens match the documented hexes`() {
        // docs/界面规范.md 第三、四节的表：改 token 就要同时改文档，反过来也一样
        val documented: List<Pair<String, String>> = listOf(
            hex(PrimaryLight) to "#0F766E",
            hex(PrimaryInkLight) to "#0B6A61",
            hex(PrimaryContainerLight) to "#D9F0EC",
            hex(OnPrimaryContainerLight) to "#08423B",
            hex(BackgroundLight) to "#F4F5F7",
            hex(SurfaceLight) to "#FFFFFF",
            hex(SurfaceContainerLight) to "#EFF1F4",
            hex(SurfaceContainerHighLight) to "#E7EAEF",
            hex(OutlineLight) to "#D5D8DE",
            hex(OutlineVariantLight) to "#EEEFF2",
            hex(IconMutedLight) to "#878E99",
            hex(OnSurfaceLight) to "#14171B",
            hex(OnSurfaceVariantLight) to "#5C636E",
            hex(ExpenseLight) to "#C43C33",
            hex(IncomeLight) to "#0B6A61",
            hex(ErrorLight) to "#B3261E",
            hex(PrimaryDark) to "#57CDBA",
            hex(PrimaryInkDark) to "#55C9B6",
            hex(PrimaryContainerDark) to "#134E48",
            hex(OnPrimaryContainerDark) to "#A8E6DA",
            hex(BackgroundDark) to "#0F1113",
            hex(SurfaceDark) to "#181A1D",
            hex(SurfaceContainerDark) to "#212429",
            hex(SurfaceContainerHighDark) to "#282C31",
            hex(OutlineDark) to "#4A4F57",
            hex(OutlineVariantDark) to "#262A2F",
            hex(IconMutedDark) to "#6B727C",
            hex(OnSurfaceDark) to "#E7E9EC",
            hex(OnSurfaceVariantDark) to "#9BA1AC",
            hex(ExpenseDark) to "#F28B82",
            hex(IncomeDark) to "#55C9B6",
            hex(ErrorDark) to "#F2B8B5",
            // 分类颜料盘（第四节八槽 + 中性两档）
            hex(PennedPigment.light[0]) to "#B0234F",
            hex(PennedPigment.light[1]) to "#B93C0B",
            hex(PennedPigment.light[2]) to "#0E7A50",
            hex(PennedPigment.light[3]) to "#0E7490",
            hex(PennedPigment.light[4]) to "#7B3FC4",
            hex(PennedPigment.light[5]) to "#3F4FC9",
            hex(PennedPigment.light[6]) to "#A21CAF",
            hex(PennedPigment.light[7]) to "#8F6205",
            hex(PennedPigment.dark[0]) to "#F2A0BE",
            hex(PennedPigment.dark[1]) to "#F5B183",
            hex(PennedPigment.dark[2]) to "#7EDCB0",
            hex(PennedPigment.dark[3]) to "#7FD4EC",
            hex(PennedPigment.dark[4]) to "#C9A8F0",
            hex(PennedPigment.dark[5]) to "#A3B0F5",
            hex(PennedPigment.dark[6]) to "#EFA0DC",
            hex(PennedPigment.dark[7]) to "#E5C878",
            hex(PennedPigment.neutralLight) to "#5F6672",
            hex(PennedPigment.neutralDark) to "#A8AEBA",
        )
        val mismatched = documented.filter { (actual, expected) -> actual != expected }
        assertTrue(
            "以下 token 与界面规范文档里的值不一致：\n" +
                mismatched.joinToString("\n") { (actual, expected) -> "  代码 $actual ≠ 文档 $expected" },
            mismatched.isEmpty(),
        )
    }
}
