package dev.penned.app.ui.appearance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.PennedApplication
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.DarkMode
import dev.penned.app.ui.theme.PennedBrands
import dev.penned.app.ui.theme.PennedTheme
import dev.penned.app.ui.theme.PennedThemeFromSettings
import dev.penned.app.ui.theme.ThemeAccent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.material3.MaterialTheme

/**
 * 主题外观（M3-12）：四套色相 × 浅深八张基线 + 选中态；
 * 另有一条真库真导航的链路测试（改设置 → DataStore → 整棵树换色）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val accent = mutableStateOf(ThemeAccent.TEAL)
    private val mode = mutableStateOf(DarkMode.LIGHT)

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    /** 页面本身跟着当前色相渲染（真机上就是"选中即生效"的样子）。 */
    private fun renderPage() {
        composeRule.setContent {
            PennedTheme(darkTheme = mode.value == DarkMode.DARK, accent = accent.value) {
                AppearanceContent(
                    accent = accent.value,
                    darkMode = mode.value,
                    darkTheme = mode.value == DarkMode.DARK,
                    onPickAccent = { accent.value = it },
                    onPickDarkMode = { mode.value = it },
                    onBack = {},
                )
            }
        }
    }

    /** 四套色相 × 浅色：每套一张（切色相 → 断言已选标记跟着走 → 截图）。 */
    @Test
    fun captureFourAccentsLight() {
        renderPage()
        ThemeAccent.entries.forEach { item ->
            accent.value = item
            composeRule.waitForIdle()
            // 四行都在；「已选」在当前那一行（四套里只有一处）
            ThemeAccent.entries.forEach { composeRule.onNodeWithText(it.label).assertIsDisplayed() }
            assertEquals(1, composeRule.onAllNodesWithText("已选").fetchSemanticsNodes().size)
            capture("m3_theme_${item.name.lowercase()}_light")
        }
    }

    /** 四套色相 × 深色：同上一轮，深色下品牌一族换的是深色那一档。 */
    @Test
    fun captureFourAccentsDark() {
        mode.value = DarkMode.DARK
        renderPage()
        ThemeAccent.entries.forEach { item ->
            accent.value = item
            composeRule.waitForIdle()
            capture("m3_theme_${item.name.lowercase()}_dark")
        }
    }

    /** 深浅模式三段：点「深色」→ 选中项跟着走（真机上同时整树换色）。 */
    @Test
    fun darkModeSegments() {
        renderPage()
        composeRule.onNodeWithText("深浅模式").performScrollTo()
        composeRule.onNodeWithText("深色").performClick()
        composeRule.waitForIdle()
        assertEquals(DarkMode.DARK, mode.value)
        composeRule.onNodeWithText("跟随系统").assertIsDisplayed()
        composeRule.onNodeWithText("浅色").assertIsDisplayed()
    }

    /**
     * 链路：我的 → 主题外观 → 选靛青 + 深色 → DataStore 写入 → 整棵树换成靛青深色
     * （品牌色从 `MaterialTheme.colorScheme.primary` 读出来核对）。
     */
    @Test
    fun pickingThemePersistsAndRetintsApp() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        var seenPrimary: Color? = null
        var seenDark = false
        composeRule.setContent {
            PennedThemeFromSettings(app) {
                seenPrimary = MaterialTheme.colorScheme.primary
                seenDark = androidx.compose.foundation.isSystemInDarkTheme() || seenDark
                CompositionLocalProvider(LocalApp provides app) { PennedApp(app) }
            }
        }
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }

        composeRule.onNodeWithText("我的").performClick()
        composeRule.waitUntil(5_000) { count("主题外观") == 1 }
        composeRule.onNodeWithText("主题外观").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { count("品牌色相") == 1 }

        // 选靛青：DataStore 落键 + 已选标记搬家
        composeRule.onNodeWithText("靛青").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { app.settings.themeAccentKey.first() } == ThemeAccent.INDIGO.name
        }
        composeRule.waitForIdle()
        assertEquals(PennedBrands.light(ThemeAccent.INDIGO).primary, seenPrimary)

        // 选深色：整棵树的 primary 换成靛青的深色档
        composeRule.onNodeWithText("深浅模式").performScrollTo()
        composeRule.onNodeWithText("深色").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { app.settings.darkModeKey.first() } == DarkMode.DARK.name
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { seenPrimary == PennedBrands.dark(ThemeAccent.INDIGO).primary }
        assertEquals(PennedBrands.dark(ThemeAccent.INDIGO).primary, seenPrimary)
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size
}
