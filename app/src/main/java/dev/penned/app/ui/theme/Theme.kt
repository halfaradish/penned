package dev.penned.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 浅色配色表：中性层固定，品牌一族来自 [brand]（M3-12 起可换色相）。
 * 默认青绿的值与 M3-12 之前逐位相同——默认主题的观感一行没变。
 */
private fun lightColors(brand: BrandColors) = lightColorScheme(
    primary = brand.primary,
    onPrimary = brand.onPrimary,
    primaryContainer = brand.primaryContainer,
    onPrimaryContainer = brand.onPrimaryContainer,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    // 提示条（Snackbar）用的反向槽位：深底浅字（浅色主题）/ 浅底深字（深色主题）
    inverseSurface = OnSurfaceLight,
    inverseOnSurface = BackgroundLight,
    inversePrimary = brand.inversePrimary,
    // "比 surface 更亮一档"的语义槽位：浅色就是白，深色要比卡片亮（分段控件的选中滑块用它）
    surfaceBright = SurfaceLight,
    surfaceDim = BackgroundLight,
    surfaceContainerLowest = SurfaceLight,
    // 低强调容器 = 日头条底（极淡品牌纸，随色相走；见 Brand.kt）
    surfaceContainerLow = brand.dayHeader,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighLight,
)

private fun darkColors(brand: BrandColors) = darkColorScheme(
    primary = brand.primary,
    onPrimary = brand.onPrimary,
    primaryContainer = brand.primaryContainer,
    onPrimaryContainer = brand.onPrimaryContainer,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    inverseSurface = OnSurfaceDark,
    inverseOnSurface = SurfaceDark,
    inversePrimary = brand.inversePrimary,
    surfaceBright = SurfaceBrightDark,
    surfaceDim = BackgroundDark,
    surfaceContainerLowest = SurfaceDark,
    surfaceContainerLow = brand.dayHeader,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighDark,
)

/**
 * M3 没有的语义槽位（金额红绿、品牌墨色、非文字图标灰）。
 * 对照度有承诺：见 docs/界面规范.md 第三节的实测列，改值要同步改 `ContrastTest`。
 */
@Immutable
data class PennedSemanticColors(
    val expense: Color,
    val income: Color,
    val primaryInk: Color,
    val iconMuted: Color,
)

private val LightSemantic = PennedSemanticColors(
    expense = ExpenseLight,
    income = IncomeLight,
    primaryInk = PrimaryInkLight,
    iconMuted = IconMutedLight,
)

private val DarkSemantic = PennedSemanticColors(
    expense = ExpenseDark,
    income = IncomeDark,
    primaryInk = PrimaryInkDark,
    iconMuted = IconMutedDark,
)

/** 语义色里的 `primaryInk` 跟着品牌色相走（支出红/收入青绿/图标灰一律不动）。 */
private fun semanticFor(brand: BrandColors, darkTheme: Boolean): PennedSemanticColors =
    (if (darkTheme) DarkSemantic else LightSemantic).copy(primaryInk = brand.primaryInk)

/** 当前主题是否深色。读 PennedTheme 的入参而非系统值——截图测试会强制 darkTheme=true。 */
private val LocalPennedDark = staticCompositionLocalOf { false }
private val LocalPennedSemantic = staticCompositionLocalOf { LightSemantic }

@Composable
@ReadOnlyComposable
internal fun isDarkTheme(): Boolean = LocalPennedDark.current

/**
 * 应用主题：品牌色相（M3-12，[accent]）+ 深浅（[darkTheme]）。
 * 中性层、语义色、分类颜料盘不随主题变；默认参数 = 青绿 + 跟随系统，与 M3-12 之前完全一致。
 */
@Composable
fun PennedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: ThemeAccent = ThemeAccent.TEAL,
    content: @Composable () -> Unit,
) {
    val brand = if (darkTheme) PennedBrands.dark(accent) else PennedBrands.light(accent)
    CompositionLocalProvider(
        LocalPennedDark provides darkTheme,
        LocalPennedSemantic provides semanticFor(brand, darkTheme),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColors(brand) else lightColors(brand),
            typography = PennedTypography,
            shapes = PennedShapes,
            content = content,
        )
    }
}

/** 支出红。 */
@Composable
@ReadOnlyComposable
fun expenseColor(): Color = LocalPennedSemantic.current.expense

/** 收入青绿。 */
@Composable
@ReadOnlyComposable
fun incomeColor(): Color = LocalPennedSemantic.current.income

/** 品牌文字色（用于正文/图标的青绿，比 primary 深一档）。 */
@Composable
@ReadOnlyComposable
fun primaryInkColor(): Color = LocalPennedSemantic.current.primaryInk

/** 非文字控件灰（右箭头、占位图标；≥3:1，别拿来写字）。 */
@Composable
@ReadOnlyComposable
fun iconMutedColor(): Color = LocalPennedSemantic.current.iconMuted
