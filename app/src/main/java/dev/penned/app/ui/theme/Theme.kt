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

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
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
    inversePrimary = InversePrimaryLight,
    // "比 surface 更亮一档"的语义槽位：浅色就是白，深色要比卡片亮（分段控件的选中滑块用它）
    surfaceBright = SurfaceLight,
    surfaceDim = BackgroundLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighLight,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
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
    inversePrimary = InversePrimaryDark,
    surfaceBright = SurfaceBrightDark,
    surfaceDim = BackgroundDark,
    surfaceContainerLowest = SurfaceDark,
    surfaceContainerLow = SurfaceDark,
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

/** 当前主题是否深色。读 PennedTheme 的入参而非系统值——截图测试会强制 darkTheme=true。 */
private val LocalPennedDark = staticCompositionLocalOf { false }
private val LocalPennedSemantic = staticCompositionLocalOf { LightSemantic }

@Composable
@ReadOnlyComposable
internal fun isDarkTheme(): Boolean = LocalPennedDark.current

@Composable
fun PennedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPennedDark provides darkTheme,
        LocalPennedSemantic provides if (darkTheme) DarkSemantic else LightSemantic,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
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
