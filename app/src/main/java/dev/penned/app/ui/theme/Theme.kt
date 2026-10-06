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
    surfaceContainerLowest = SurfaceDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighDark,
)

/** 金额语义色（不进 ColorScheme：M3 没有金额语义槽位）。 */
@Immutable
data class PennedMoneyColors(val expense: Color, val income: Color, val primaryInk: Color)

private val LightMoney = PennedMoneyColors(ExpenseLight, IncomeLight, PrimaryInkLight)
private val DarkMoney = PennedMoneyColors(ExpenseDark, IncomeDark, PrimaryInkDark)

/** 当前主题是否深色。读 PennedTheme 的入参而非系统值——截图测试会强制 darkTheme=true。 */
private val LocalPennedDark = staticCompositionLocalOf { false }
private val LocalPennedMoney = staticCompositionLocalOf { LightMoney }

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
        LocalPennedMoney provides if (darkTheme) DarkMoney else LightMoney,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PennedTypography,
            shapes = PennedShapes,
            content = content,
        )
    }
}

/** 支出朱砂。 */
@Composable
@ReadOnlyComposable
fun expenseColor(): Color = LocalPennedMoney.current.expense

/** 收入青绿。 */
@Composable
@ReadOnlyComposable
fun incomeColor(): Color = LocalPennedMoney.current.income

/** 品牌文字色（用于正文/图标的青绿，比 primary 深一档）。 */
@Composable
@ReadOnlyComposable
fun primaryInkColor(): Color = LocalPennedMoney.current.primaryInk
