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
        // 日头条：极淡青灰底 + 墨色日期 + 次要色收支 + 深青竖条（RA 轮 D045）
        add("日头条 onSurface / 底", OnSurfaceLight, DayHeaderLight)
        add("日头条 onSurfaceVariant / 底", OnSurfaceVariantLight, DayHeaderLight)
        add("日头条 竖条 primaryInk / 底", PrimaryInkLight, DayHeaderLight)
        add("expense / surface", ExpenseLight, SurfaceLight)
        add("expense / background", ExpenseLight, BackgroundLight)
        add("income / surface", IncomeLight, SurfaceLight)
        add("error / surface", ErrorLight, SurfaceLight)
        add("onErrorContainer / errorContainer", OnErrorContainerLight, ErrorContainerLight)
        // 提示条走反向槽位：深底浅字 + inversePrimary 动作键
        add("inverseOnSurface / inverseSurface（提示条）", BackgroundLight, OnSurfaceLight)
        add("inversePrimary / inverseSurface（撤销键）", InversePrimaryLight, OnSurfaceLight)
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
        add("日头条 onSurface / 底（深）", OnSurfaceDark, DayHeaderDark)
        add("日头条 onSurfaceVariant / 底（深）", OnSurfaceVariantDark, DayHeaderDark)
        add("日头条 竖条 primaryInk / 底（深）", PrimaryInkDark, DayHeaderDark)
        add("expense / surface（深）", ExpenseDark, SurfaceDark)
        add("expense / background（深）", ExpenseDark, BackgroundDark)
        add("income / surface（深）", IncomeDark, SurfaceDark)
        add("error / surface（深）", ErrorDark, SurfaceDark)
        add("onErrorContainer / errorContainer（深）", OnErrorContainerDark, ErrorContainerDark)
        add("inverseOnSurface / inverseSurface（提示条·深）", SurfaceDark, OnSurfaceDark)
        add("inversePrimary / inverseSurface（撤销键·深）", InversePrimaryDark, OnSurfaceDark)
        // —— 深色：非文字 ——
        add("iconMuted / surface（深）", IconMutedDark, SurfaceDark, nonText)
        add("进度填充 primary / surface（深）", PrimaryDark, SurfaceDark, nonText)

        // —— 分类颜料 v3：三档（亮档块面 / 中档细件 / 深档字与选中块，D046）——
        // 块面是**装饰性**的（分类身份由字形 + 旁边的名称承担），所以块面本身不设明度下限——
        // 它靠色相/彩度与卡片分开（参考的课表格子同款做法，人家只有 1.4~1.6:1）。
        // 真正要守的是下面三条：字形读得清、细件在白底与轨道上看得见、深色下细件换成亮档也看得见。
        PennedPigment.fill.forEachIndexed { i, block ->
            val mark = PennedPigment.mark[i]
            val glyph = PennedPigment.ink[i]
            add("颜料$i 深档字 / 自家亮档块面", glyph, block)
            add("白字 / 颜料$i 深档块（选中态）", PennedPigment.onFillWhite, glyph)
            add("颜料$i 中档细件 / surface（浅）", mark, SurfaceLight, nonText)
            add("颜料$i 中档细件 / 占比条轨道（浅）", mark, SurfaceContainerHighLight, nonText)
            add("颜料$i 亮档细件 / surface（深）", block, SurfaceDark, nonText)
            add("颜料$i 亮档细件 / 占比条轨道（深）", block, SurfaceContainerHighDark, nonText)
        }
        add("白字 / 中性块面", PennedPigment.onFillWhite, PennedPigment.neutralFill)
        add("中性块面 / surface（浅）", PennedPigment.neutralFill, SurfaceLight, nonText)
        add("中性块面 / surface（深）", PennedPigment.neutralFill, SurfaceDark, nonText)
        add("中性细件 / surface（浅）", PennedPigment.neutralInkLight, SurfaceLight, nonText)
        add("中性细件 / 占比条轨道（浅）", PennedPigment.neutralInkLight, SurfaceContainerHighLight, nonText)
        add("中性细件 / surface（深）", PennedPigment.neutralInkDark, SurfaceDark, nonText)
        add("中性细件 / 占比条轨道（深）", PennedPigment.neutralInkDark, SurfaceContainerHighDark, nonText)
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
            hex(DayHeaderLight) to "#EDF3F1",
            hex(DayHeaderDark) to "#1C2523",
            // 分类颜料盘 v3（第四节八槽 × 三档 + 中性三档）
            hex(PennedPigment.fill[0]) to "#EB7A9E",
            hex(PennedPigment.fill[1]) to "#EB9A7A",
            hex(PennedPigment.fill[2]) to "#7AEBC0",
            hex(PennedPigment.fill[3]) to "#7AD3EB",
            hex(PennedPigment.fill[4]) to "#AD7AEB",
            hex(PennedPigment.fill[5]) to "#7A87EB",
            hex(PennedPigment.fill[6]) to "#E27AEB",
            hex(PennedPigment.fill[7]) to "#EBC57A",
            hex(PennedPigment.ink[0]) to "#690D2A",
            hex(PennedPigment.ink[1]) to "#712A0E",
            hex(PennedPigment.ink[2]) to "#0D6946",
            hex(PennedPigment.ink[3]) to "#0E596E",
            hex(PennedPigment.ink[4]) to "#390E6F",
            hex(PennedPigment.ink[5]) to "#0E1A72",
            hex(PennedPigment.ink[6]) to "#620D69",
            hex(PennedPigment.ink[7]) to "#6F4E0E",
            hex(PennedPigment.mark[0]) to "#EC3E75",
            hex(PennedPigment.mark[1]) to "#E55117",
            hex(PennedPigment.mark[2]) to "#0F9562",
            hex(PennedPigment.mark[3]) to "#118EB0",
            hex(PennedPigment.mark[4]) to "#9145ED",
            hex(PennedPigment.mark[5]) to "#4559ED",
            hex(PennedPigment.mark[6]) to "#DA2BEA",
            hex(PennedPigment.mark[7]) to "#AD7911",
            hex(PennedPigment.neutralFill) to "#6B7280",
            hex(PennedPigment.onFillWhite) to "#FFFFFF",
            hex(PennedPigment.neutralInkLight) to "#4B5563",
            hex(PennedPigment.neutralInkDark) to "#A8AEBA",
        )
        val mismatched = documented.filter { (actual, expected) -> actual != expected }
        assertTrue(
            "以下 token 与界面规范文档里的值不一致：\n" +
                mismatched.joinToString("\n") { (actual, expected) -> "  代码 $actual ≠ 文档 $expected" },
            mismatched.isEmpty(),
        )
    }
}
