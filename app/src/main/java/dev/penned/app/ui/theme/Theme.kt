package dev.penned.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerLight,
    surfaceContainerHighest = SurfaceContainerLight,
    surfaceContainerLow = SurfaceVariantLight,
    surfaceContainerLowest = SurfaceLight,
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
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerDark,
    surfaceContainerHighest = SurfaceContainerDark,
    surfaceContainerLow = SurfaceVariantDark,
    surfaceContainerLowest = SurfaceDark,
)

@Composable
fun PennedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PennedTypography,
        content = content,
    )
}

/** 支出红：暗色下用提亮变体（多处直接引用色值而非 scheme 槽位）。 */
@Composable
fun expenseColor(): Color = if (isSystemInDarkTheme()) ExpenseRedDark else ExpenseRed

/** 收入青绿：暗色下用提亮变体。 */
@Composable
fun incomeColor(): Color = if (isSystemInDarkTheme()) IncomeTealDark else IncomeTeal
