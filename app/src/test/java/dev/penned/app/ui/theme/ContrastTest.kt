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

    /**
     * 图表细件（占比环/图例/占比条）的"看得见"下限：**1.40:1（浅）/ 2.30:1（深）**。
     *
     * 这两条**故意低于 3:1**——用户第二轮点名"支出占比的配色也需要改动"，环/条从全强度淡到 45%
     * 之后，与卡片 1.84~2.00:1、与浅轨道 1.52~1.72:1，物理上到不了 3:1。
     * 取舍写在 D048：环/条只是**辅助**视觉，占比数字与金额每行都写着；
     * 下限取"渲染比出来的那条线"——≥45% 时 4dp 占比条还看得见，≤25% 就真看不见了（`build/chart_pale.py`）。
     * 留着它是因为**改淡过头会立刻红**，而不是为了让数字好看。
     */
    private val markVisible = 1.40
    private val markVisibleDark = 2.30

    /**
     * 图标底（全 App 统一的淡灰绿圆）的"看得见"下限：**1.10:1**。
     *
     * 它和日头条底（1.12:1）是同一档的"纸"，**装饰性**——识别靠上面的墨色字形与旁边的名称
     * （理由写在规范 §4/§11.1），所以不按 3:1 守；留一条下限是为了"改淡过头立刻红"：
     * 淡到与卡片糊成一片（≤6%）就掉线。浅色实测 1.17:1、深色 1.75:1。
     */
    private val groundVisible = 1.10

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
        // 左滑动作区（UI-12）：编辑块 = 次级填充，删除块 = error 实心
        add("onSurface / surfaceContainerHigh（编辑块）", OnSurfaceLight, SurfaceContainerHighLight)
        add("onError / error（删除块）", OnErrorLight, ErrorLight)
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
        add("onSurface / surfaceContainerHigh（编辑块·深）", OnSurfaceDark, SurfaceContainerHighDark)
        add("onError / error（删除块·深）", OnErrorDark, ErrorDark)
        add("inverseOnSurface / inverseSurface（提示条·深）", SurfaceDark, OnSurfaceDark)
        add("inversePrimary / inverseSurface（撤销键·深）", InversePrimaryDark, OnSurfaceDark)
        // —— 深色：非文字 ——
        add("iconMuted / surface（深）", IconMutedDark, SurfaceDark, nonText)
        add("进度填充 primary / surface（深）", PrimaryDark, SurfaceDark, nonText)

        // —— 分类颜料 v6（AS 轮）：图标底统一淡灰绿，色相只留给图表细件与宫格小圆点 ——
        // 图标底**不再分槽**：全 App 一个色（`iconHue` 12%/22%），与白卡 1.17:1、跟日头条底 1.12:1
        // 同档，是"纸"不是"色块"——识别靠上面的墨色字形（浅 15.6:1 / 深 8.2:1）与旁边的名称。
        // 分类色只剩两处：①图表细件（45%/65%，够不上 3:1，按 markVisible 两条下限守）；
        // ②宫格"有子分类"9dp 小圆点（全强度，压在图标底上按 nonText 守——翠点压翠底不再是同色压同色）。
        val iconGroundLight = over(PennedPigment.iconHue, SurfaceLight, PennedPigment.TINT_ALPHA_LIGHT)
        val iconGroundDark = over(PennedPigment.iconHueOnDark, SurfaceDark, PennedPigment.TINT_ALPHA_DARK)
        add("图标底 墨色字 / 淡灰绿（浅）", OnSurfaceLight, iconGroundLight)
        add("图标底 墨色字 / 淡灰绿（深）", OnSurfaceDark, iconGroundDark)
        add("图标底 淡灰绿 / surface（浅）", iconGroundLight, SurfaceLight, groundVisible)
        add("图标底 淡灰绿 / surface（深）", iconGroundDark, SurfaceDark, groundVisible)
        // 宫格选中态：品牌青绿实心 + 反白（与日头条那根 3dp 竖条、下方选中的分类名同色）
        add("白字 / primaryInk（宫格选中态·浅）", OnPrimaryLight, PrimaryInkLight)
        add("深字 surface / primaryInk（宫格选中态·深）", SurfaceDark, PrimaryInkDark)
        PennedPigment.hue.forEachIndexed { i, full ->
            val onDark = PennedPigment.hueOnDark[i]
            val markLight = over(full, SurfaceLight, PennedPigment.MARK_ALPHA_LIGHT)
            val markDark = over(onDark, SurfaceDark, PennedPigment.MARK_ALPHA_DARK)
            add("颜料$i 细件 / surface（浅）", markLight, SurfaceLight, markVisible)
            add("颜料$i 细件 / 占比条轨道（浅）", markLight, SurfaceContainerHighLight, markVisible)
            add("颜料$i 细件 / surface（深）", markDark, SurfaceDark, markVisibleDark)
            add("颜料$i 小圆点 / 图标底（浅）", full, iconGroundLight, nonText)
            add("颜料$i 小圆点 / 图标底（深）", onDark, iconGroundDark, nonText)
        }
        val neutralMarkLight = over(PennedPigment.neutralHue, SurfaceLight, PennedPigment.MARK_ALPHA_LIGHT)
        val neutralMarkDark = over(PennedPigment.neutralHueOnDark, SurfaceDark, PennedPigment.MARK_ALPHA_DARK)
        add("中性 细件 / surface（浅）", neutralMarkLight, SurfaceLight, markVisible)
        add("中性 细件 / 占比条轨道（浅）", neutralMarkLight, SurfaceContainerHighLight, markVisible)
        add("中性 细件 / surface（深）", neutralMarkDark, SurfaceDark, markVisibleDark)
        add("中性 小圆点 / 图标底（浅）", PennedPigment.neutralHue, iconGroundLight, nonText)
        add("中性 小圆点 / 图标底（深）", PennedPigment.neutralHueOnDark, iconGroundDark, nonText)
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
            // 分类颜料盘 v4（第四节八槽色相 × 两种强度 + 中性）
            hex(PennedPigment.hue[0]) to "#E31657",
            hex(PennedPigment.hue[1]) to "#CD4814",
            hex(PennedPigment.hue[2]) to "#0D8557",
            hex(PennedPigment.hue[3]) to "#107F9D",
            hex(PennedPigment.hue[4]) to "#9145ED",
            hex(PennedPigment.hue[5]) to "#4559ED",
            hex(PennedPigment.hue[6]) to "#C815D8",
            hex(PennedPigment.hue[7]) to "#9B6C0F",
            hex(PennedPigment.hueOnDark[0]) to "#EB7A9E",
            hex(PennedPigment.hueOnDark[1]) to "#EB9A7A",
            hex(PennedPigment.hueOnDark[2]) to "#7AEBC0",
            hex(PennedPigment.hueOnDark[3]) to "#7AD3EB",
            hex(PennedPigment.hueOnDark[4]) to "#AD7AEB",
            hex(PennedPigment.hueOnDark[5]) to "#7A87EB",
            hex(PennedPigment.hueOnDark[6]) to "#E27AEB",
            hex(PennedPigment.hueOnDark[7]) to "#EBC57A",
            hex(PennedPigment.neutralHue) to "#4B5563",
            hex(PennedPigment.neutralHueOnDark) to "#A8AEBA",
            // AS 轮：全 App 统一的图标底（淡灰绿，与 2 号翠槽同值但独立成 token）
            hex(PennedPigment.iconHue) to "#0D8557",
            hex(PennedPigment.iconHueOnDark) to "#7AEBC0",
        )
        val mismatched = documented.filter { (actual, expected) -> actual != expected }
        assertTrue(
            "以下 token 与界面规范文档里的值不一致：\n" +
                mismatched.joinToString("\n") { (actual, expected) -> "  代码 $actual ≠ 文档 $expected" },
            mismatched.isEmpty(),
        )
    }
}
