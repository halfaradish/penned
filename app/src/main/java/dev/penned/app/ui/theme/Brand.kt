package dev.penned.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 品牌色相四套（M3-12，D071/D085）。
 *
 * **只换品牌一族**：`primary / onPrimary / primaryInk / primaryContainer / onPrimaryContainer / inversePrimary`
 * 与"极淡品牌纸" `dayHeader`（日头条底、关于页渐变底）。中性层（背景/表面/描边/图标灰）、
 * 语义色（支出红 / 收入青绿）、分类颜料盘与图表口径**一律不随主题变**——分类身份与统计可读性
 * 优先于主题一致性（D043/D045/D051 的底线）。
 *
 * 色值都按用户的两条硬线核算过（`build/brand_check.py`，改色前先跑）：
 * ①一眼能分清（色相拉开：青绿 174° / 靛青 230° / 墨灰近中性 / 赭石 25°）；
 * ②不能太艳太亮（大面积淡底彩度 ≤8%，容器 ≈2~8%）。
 */
enum class ThemeAccent(val label: String) {
    /** 青绿（默认，现状）。 */
    TEAL("青绿"),

    /** 靛青（冷）。 */
    INDIGO("靛青"),

    /** 墨灰（近中性）。 */
    GRAPHITE("墨灰"),

    /** 赭石（暖）。 */
    OCHRE("赭石"),
}

/** 深浅模式（跟随系统 / 浅 / 深）。 */
enum class DarkMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
    ;

    /** 当前该用深色吗：SYSTEM 时看系统。 */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** 一套品牌色（浅或深）。 */
@Immutable
data class BrandColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryInk: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    /** 极淡品牌纸：日头条底 / 关于页渐变底（彩度 ≤6%，只做"比白卡低一档"的暗示）。 */
    val dayHeader: Color,
)

// —— 青绿（默认）：值直接引用 Color.kt 的现状 token，默认主题的观感与 M3-12 之前逐位相同 ——

// —— 靛青（冷）：白字 7.54:1；ink 压白 9.52:1；容器彩度 7.7% ——
private val IndigoPrimaryLight = Color(0xFF3B4CA8)
private val IndigoInkLight = Color(0xFF303E8E)
private val IndigoContainerLight = Color(0xFFE4E9F7)
private val IndigoOnContainerLight = Color(0xFF1B2461)
private val IndigoDayHeaderLight = Color(0xFFEFF1FA)
private val IndigoPrimaryDark = Color(0xFFA3B0F0)
private val IndigoInkDark = Color(0xFF9FADEF)
private val IndigoContainerDark = Color(0xFF2A3160)
private val IndigoOnContainerDark = Color(0xFFC9D0F7)
private val IndigoDayHeaderDark = Color(0xFF1B1E2A)
private val IndigoOnPrimaryDark = Color(0xFF141B45)

// —— 墨灰（近中性）：白字 7.61:1；ink 压白 9.07:1；容器彩度 2.5% ——
private val GraphitePrimaryLight = Color(0xFF4A5560)
private val GraphiteInkLight = Color(0xFF3E4A55)
private val GraphiteContainerLight = Color(0xFFE8EBEE)
private val GraphiteOnContainerLight = Color(0xFF242C34)
private val GraphiteDayHeaderLight = Color(0xFFF1F2F4)
private val GraphitePrimaryDark = Color(0xFFAEB9C5)
private val GraphiteInkDark = Color(0xFFAAB5C1)
private val GraphiteContainerDark = Color(0xFF333A42)
private val GraphiteOnContainerDark = Color(0xFFD6DCE2)
private val GraphiteDayHeaderDark = Color(0xFF1D2023)
private val GraphiteOnPrimaryDark = Color(0xFF1B2128)

// —— 赭石（暖）：白字 5.79:1；ink 压白 7.25:1；容器彩度 7.6% ——
private val OchrePrimaryLight = Color(0xFF96552A)
private val OchreInkLight = Color(0xFF82481F)
private val OchreContainerLight = Color(0xFFF9EFE6)
private val OchreOnContainerLight = Color(0xFF472812)
private val OchreDayHeaderLight = Color(0xFFF6F1E9)
private val OchrePrimaryDark = Color(0xFFE3AC80)
private val OchreInkDark = Color(0xFFE0A87C)
private val OchreContainerDark = Color(0xFF4E3320)
private val OchreOnContainerDark = Color(0xFFF2D8C0)
private val OchreDayHeaderDark = Color(0xFF241F1B)
private val OchreOnPrimaryDark = Color(0xFF3A2410)

/** 四套品牌色的取值表。`inversePrimary` 是提示条上的动作键（浅色主题 = 该色相的深色 primary、深色主题 = 该色相的浅色 ink）。 */
object PennedBrands {

    fun light(accent: ThemeAccent): BrandColors = when (accent) {
        ThemeAccent.TEAL -> BrandColors(
            primary = PrimaryLight, onPrimary = OnPrimaryLight, primaryInk = PrimaryInkLight,
            primaryContainer = PrimaryContainerLight, onPrimaryContainer = OnPrimaryContainerLight,
            inversePrimary = PrimaryDark, dayHeader = DayHeaderLight,
        )
        ThemeAccent.INDIGO -> BrandColors(
            primary = IndigoPrimaryLight, onPrimary = OnPrimaryLight, primaryInk = IndigoInkLight,
            primaryContainer = IndigoContainerLight, onPrimaryContainer = IndigoOnContainerLight,
            inversePrimary = IndigoPrimaryDark, dayHeader = IndigoDayHeaderLight,
        )
        ThemeAccent.GRAPHITE -> BrandColors(
            primary = GraphitePrimaryLight, onPrimary = OnPrimaryLight, primaryInk = GraphiteInkLight,
            primaryContainer = GraphiteContainerLight, onPrimaryContainer = GraphiteOnContainerLight,
            inversePrimary = GraphitePrimaryDark, dayHeader = GraphiteDayHeaderLight,
        )
        ThemeAccent.OCHRE -> BrandColors(
            primary = OchrePrimaryLight, onPrimary = OnPrimaryLight, primaryInk = OchreInkLight,
            primaryContainer = OchreContainerLight, onPrimaryContainer = OchreOnContainerLight,
            inversePrimary = OchrePrimaryDark, dayHeader = OchreDayHeaderLight,
        )
    }

    fun dark(accent: ThemeAccent): BrandColors = when (accent) {
        ThemeAccent.TEAL -> BrandColors(
            primary = PrimaryDark, onPrimary = OnPrimaryDark, primaryInk = PrimaryInkDark,
            primaryContainer = PrimaryContainerDark, onPrimaryContainer = OnPrimaryContainerDark,
            inversePrimary = PrimaryInkLight, dayHeader = DayHeaderDark,
        )
        ThemeAccent.INDIGO -> BrandColors(
            primary = IndigoPrimaryDark, onPrimary = IndigoOnPrimaryDark, primaryInk = IndigoInkDark,
            primaryContainer = IndigoContainerDark, onPrimaryContainer = IndigoOnContainerDark,
            inversePrimary = IndigoInkLight, dayHeader = IndigoDayHeaderDark,
        )
        ThemeAccent.GRAPHITE -> BrandColors(
            primary = GraphitePrimaryDark, onPrimary = GraphiteOnPrimaryDark, primaryInk = GraphiteInkDark,
            primaryContainer = GraphiteContainerDark, onPrimaryContainer = GraphiteOnContainerDark,
            inversePrimary = GraphiteInkLight, dayHeader = GraphiteDayHeaderDark,
        )
        ThemeAccent.OCHRE -> BrandColors(
            primary = OchrePrimaryDark, onPrimary = OchreOnPrimaryDark, primaryInk = OchreInkDark,
            primaryContainer = OchreContainerDark, onPrimaryContainer = OchreOnContainerDark,
            inversePrimary = OchreInkLight, dayHeader = OchreDayHeaderDark,
        )
    }

    /** 落库/读库用的键（DataStore 存 name；认不出的值回默认青绿）。 */
    fun fromKey(key: String?): ThemeAccent =
        ThemeAccent.entries.firstOrNull { it.name == key } ?: ThemeAccent.TEAL

    fun darkModeFromKey(key: String?): DarkMode =
        DarkMode.entries.firstOrNull { it.name == key } ?: DarkMode.SYSTEM

    /**
     * 主题外观页的预览色：那页要**同时画四套主题**的小样，不能只靠 `MaterialTheme`
     * （当前主题只有一套），所以把"某色相 × 某模式"用到的中性色也一起给出来。
     */
    fun preview(accent: ThemeAccent, darkTheme: Boolean): BrandPreview = BrandPreview(
        brand = if (darkTheme) dark(accent) else light(accent),
        surface = if (darkTheme) SurfaceDark else SurfaceLight,
        onSurface = if (darkTheme) OnSurfaceDark else OnSurfaceLight,
        onSurfaceVariant = if (darkTheme) OnSurfaceVariantDark else OnSurfaceVariantLight,
        expense = if (darkTheme) ExpenseDark else ExpenseLight,
    )
}

/** 主题外观页画小样用的整套色（品牌一族 + 该模式的中性色）。 */
@Immutable
data class BrandPreview(
    val brand: BrandColors,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val expense: Color,
)
