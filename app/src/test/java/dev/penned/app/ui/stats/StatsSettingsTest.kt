package dev.penned.app.ui.stats

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.PennedApplication
import dev.penned.app.domain.StatsLayout
import dev.penned.app.domain.StatsLayoutCodec
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.StatsModule
import dev.penned.app.domain.StatsPeriod
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.StatsStrip
import dev.penned.app.domain.StatsView
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * 自定义统计（M3-14）：统计设置的开关/顺序 + 统计页按设置渲染（含全关空态）+ 真库真导航链路。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatsSettingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun resetLayout() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        app.settings.setStatsOrder(StatsLayoutCodec.encodeOrder(StatsModule.entries.toList()))
        app.settings.setStatsEnabled(StatsLayoutCodec.encodeEnabled(StatsModule.entries.toSet()))
    }

    // —— 统计设置页 ——

    @Test
    fun captureStatsSettings() {
        composeRule.setContent {
            PennedTheme {
                StatsSettingsContent(
                    layout = StatsLayout(),
                    onToggle = { _, _ -> },
                    onMoveUp = {},
                    onMoveDown = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("统计设置").assertIsDisplayed()
        // 七个模块都在，各带一句说明；首行不给「上移」、末行不给「下移」
        StatsModule.entries.forEach { composeRule.onNodeWithText(it.label).assertIsDisplayed() }
        // 首行的「上移」、末行的「下移」都在，只是置灰（位置稳定，不忽隐忽现）
        composeRule.onNodeWithContentDescription("上移汇总").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("下移资产走势").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("下移汇总").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("上移资产走势").assertIsDisplayed()
        capture("m3_stats_settings")
    }

    @Test
    fun settingsToggleAndMove() {
        val state = mutableStateOf(StatsLayout())
        val toggles = mutableListOf<Pair<StatsModule, Boolean>>()
        val moves = mutableListOf<String>()
        composeRule.setContent {
            PennedTheme {
                StatsSettingsContent(
                    layout = state.value,
                    onToggle = { module, on -> toggles += module to on; state.value = state.value.toggle(module, on) },
                    onMoveUp = { moves += "up:${it.name}"; state.value = state.value.moveUp(it) },
                    onMoveDown = { moves += "down:${it.name}"; state.value = state.value.moveDown(it) },
                    onBack = {},
                )
            }
        }
        // 关掉「趋势」（第二个开关）
        composeRule.onAllNodes(isToggleable())[1].performClick()
        composeRule.waitForIdle()
        assertEquals(StatsModule.TREND to false, toggles.first())
        // 「汇总」下移一位 → 顺序里它排到「趋势」后面
        composeRule.onNodeWithContentDescription("下移汇总").performClick()
        composeRule.waitForIdle()
        assertEquals("down:SUMMARY", moves.first())
        assertEquals(
            listOf(StatsModule.TREND, StatsModule.SUMMARY, StatsModule.DONUT),
            state.value.order.take(3),
        )
    }

    // —— 统计页按设置渲染 ——

    private fun page() = StatsPage(
        period = StatsPeriod.Month(YearMonth.of(2026, 9)),
        window = StatsPeriods.window(StatsPeriod.Month(YearMonth.of(2026, 9))),
        totals = StatsMath.PeriodTotals(incomeCents = 350_000, expenseCents = 296_770),
        expenseCategories = listOf(
            StatsMath.CategoryTotal("食品餐饮", 180_000, 110, 0.607f),
            StatsMath.CategoryTotal("购物消费", 80_000, 15, 0.270f),
            StatsMath.CategoryTotal("其他", 35_570, 4, 0.120f),
        ),
        incomeCategories = listOf(StatsMath.CategoryTotal("工资", 350_000, 1, 1f)),
        trendBars = List(30) { 0L }.toMutableList().also { it[3] = 12600; it[11] = 8000 },
        tagTotals = listOf(
            StatsMath.CategoryTotal("出差", 15_000, 2, 1f, drillable = false),
            StatsMath.CategoryTotal("报销", 10_000, 1, 0.66f, drillable = false),
        ),
        assetOpeningCents = 400_000,
        assetSeries = List(30) { 400_000L + it * 100 },
    )

    private fun renderStats(layout: StatsLayout) {
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    view = StatsView.MONTH,
                    pages = mapOf(StatsView.MONTH to page()),
                    stripMonths = StatsStrip.months(YearMonth.of(2024, 3), YearMonth.of(2026, 10), include = YearMonth.of(2026, 9)),
                    stripYears = StatsStrip.years(YearMonth.of(2024, 3), YearMonth.of(2026, 10)),
                    layout = layout,
                    today = LocalDate.of(2026, 10, 7),
                )
            }
        }
    }

    /** 默认布局：原有四块都在（观感与 M3-14 之前一致）+ 三块新模块也默认开。 */
    @Test
    fun defaultLayoutShowsAllModules() {
        renderStats(StatsLayout())
        // 「支出」在汇总格、切换器与两张卡标题里都出现 → 按"存在"断言，别按唯一
        assertTrue(count("支出") >= 1)
        composeRule.onNodeWithText("支出占比").assertIsDisplayed()
        composeRule.onNodeWithText("支出数据").assertIsDisplayed()
        // 后三块在长页靠下：按"存在"断言（可见性由截图看）
        composeRule.onNodeWithText("标签占比").assertExists()
        composeRule.onNodeWithText("标签数据").assertExists()
        composeRule.onNodeWithText("资产走势").assertExists()
        composeRule.onNodeWithContentDescription("统计设置").assertIsDisplayed()
        capture("m3_stats_all_modules")
    }

    /** 关掉「趋势」「占比」「数据」，把「资产走势」排到最前：页面只剩汇总 + 资产走势，且顺序按设置。 */
    @Test
    fun layoutOrderAndToggleFollowSettings() {
        val layout = StatsLayout(
            order = listOf(StatsModule.ASSET_TREND) + StatsModule.entries.filterNot { it == StatsModule.ASSET_TREND },
            enabled = setOf(StatsModule.SUMMARY, StatsModule.ASSET_TREND),
        )
        renderStats(layout)
        composeRule.onNodeWithText("资产走势").assertIsDisplayed()
        composeRule.onNodeWithText("支出占比").assertDoesNotExist()
        composeRule.onNodeWithText("支出数据").assertDoesNotExist()
        composeRule.onNodeWithText("标签占比").assertDoesNotExist()
        // 资产走势在汇总之前（顺序按设置）
        val assetY = composeRule.onNodeWithText("资产走势").fetchSemanticsNode().boundsInRoot.top
        val summaryY = composeRule.onNodeWithText("支出").fetchSemanticsNode().boundsInRoot.top
        assertTrue("资产走势应排在汇总之前（$assetY < $summaryY）", assetY < summaryY)
        capture("m3_stats_custom_layout")
    }

    /** 全关：统计页给空态（规格 M3-14 的"含全关的空态"）。 */
    @Test
    fun allOffShowsEmptyState() {
        renderStats(StatsLayout(enabled = emptySet()))
        composeRule.onNodeWithText("统计页没有模块").assertIsDisplayed()
        composeRule.onNodeWithText("点右上「⋯」去统计设置里打开几块").assertIsDisplayed()
        composeRule.onNodeWithText("支出占比").assertDoesNotExist()
        capture("m3_stats_all_off")
    }

    /** 真库真导航：统计 → 右上「⋯」→ 关掉「趋势」→ 回统计页趋势卡消失；设置落 DataStore。 */
    @Test
    fun settingsPersistThroughDataStore() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        resetLayout()
        composeRule.setContent {
            PennedTheme {
                CompositionLocalProvider(LocalApp provides app) { PennedApp(app) }
            }
        }
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
        composeRule.onNodeWithText("统计").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("统计设置").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("统计设置").performClick()
        composeRule.waitUntil(5_000) { count("模块") == 1 }
        // 关掉「趋势」（第二个开关）
        composeRule.onAllNodes(isToggleable())[1].performClick()
        composeRule.waitForIdle()
        // 顶栏「返回」回统计页：趋势卡没了，其余照旧
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitUntil(5_000) { count("模块") == 0 }
        // 这个测试库没有流水：统计页走"有模块但没数据"的分支——汇总 + 资产走势在，其余给空态
        assertTrue("资产走势仍在", count("资产走势") >= 1)
        composeRule.onNodeWithText("支出占比").assertDoesNotExist()
        // 设置落 DataStore：键里没有 TREND
        val enabled = runBlocking { app.settings.statsEnabled.first() }
        // 注意别用字符串 contains("TREND")：ASSET_TREND 也含它——按解码后的集合判
        assertTrue(
            "TREND 应已关掉：$enabled",
            StatsModule.TREND !in StatsLayoutCodec.decodeEnabled(enabled),
        )
        resetLayout()
    }
}
