package dev.penned.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    // 中性浅灰，去掉默认 M3 的紫灰色偏（记一笔页背景/圆底用）
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDF0EF),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF5A6462),
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
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
