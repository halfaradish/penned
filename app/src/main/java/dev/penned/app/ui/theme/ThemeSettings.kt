package dev.penned.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.penned.app.PennedApplication
import kotlinx.coroutines.flow.combine

/**
 * 从 DataStore 读主题设置并套上 [PennedTheme]（M3-12）。
 *
 * 抽出来是为了让"设置 → 整棵树换色"这条链路**只有一份**：`MainActivity` 与流程测试共用，
 * 测试才能真的验到"改了设置页面就换色"，而不是各写一遍收集逻辑。
 * 首值未到前用默认（青绿 + 跟随系统）——与没有这个设置时的观感一致。
 */
@Composable
fun PennedThemeFromSettings(app: PennedApplication, content: @Composable () -> Unit) {
    val theme by produceState(initialValue = ThemeAccent.TEAL to DarkMode.SYSTEM) {
        combine(app.settings.themeAccentKey, app.settings.darkModeKey) { accentKey, modeKey ->
            PennedBrands.fromKey(accentKey) to PennedBrands.darkModeFromKey(modeKey)
        }.collect { value = it }
    }
    PennedTheme(
        darkTheme = theme.second.isDark(isSystemInDarkTheme()),
        accent = theme.first,
        content = content,
    )
}
