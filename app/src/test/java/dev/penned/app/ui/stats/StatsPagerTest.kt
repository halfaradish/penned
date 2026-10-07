package dev.penned.app.ui.stats

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.StatsPeriod
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.StatsTrend
import dev.penned.app.domain.StatsView
import dev.penned.app.ui.theme.PennedTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 左右滑动切视图（AN 轮）：图外滑翻页、**图内滑是扫柱不翻页**（用户裁决）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatsPagerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 7)

    private fun page(period: StatsPeriod, withData: Boolean = true): StatsPage {
        val window = StatsPeriods.window(period, today = today)
        return StatsPage(
            period = period,
            window = window,
            totals = StatsMath.PeriodTotals(incomeCents = 0, expenseCents = 32300),
            expenseCategories = if (withData) {
                listOf(StatsMath.CategoryTotal("食品餐饮", 32300, 3, 1f))
            } else {
                emptyList()
            },
            trendBars = if (withData) List(window.dayCount.coerceAtMost(31)) { 0L }.toMutableList()
                .also { if (it.isNotEmpty()) it[0] = 1600 } else emptyList(),
            trendAxis = StatsTrend.axisOf(period, window),
        )
    }

    private fun render(onSelectView: (StatsView) -> Unit) {
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    view = StatsView.MONTH,
                    pages = mapOf(
                        StatsView.LAST_7_DAYS to page(StatsPeriod.Last7Days),
                        StatsView.MONTH to page(StatsPeriod.Month(YearMonth.of(2026, 10))),
                        StatsView.YEAR to page(StatsPeriod.Year(2026)),
                        StatsView.CUSTOM to page(
                            StatsPeriods.custom(
                                LocalDate.of(2026, 10, 1).toEpochDay(),
                                LocalDate.of(2026, 10, 15).toEpochDay(),
                            ),
                        ),
                    ),
                    onSelectView = onSelectView,
                    today = today,
                )
            }
        }
    }

    @Test
    fun `图外左滑翻到下一个视图`() {
        var selected = StatsView.MONTH
        render { selected = it }
        // 在页面下部（数据卡那一带，离图表很远）左滑
        composeRule.onNodeWithTag(STATS_PAGER_TAG).performTouchInput {
            // 沿页面下部滑（数据卡那一带），离图表远一点
            swipe(
                start = Offset(right - 1f, bottom * 0.9f),
                end = Offset(left + 1f, bottom * 0.9f),
                durationMillis = 200,
            )
        }
        composeRule.waitForIdle()
        assertEquals(StatsView.YEAR, selected)
    }

    @Test
    fun `图内左滑是扫柱_不翻页`() {
        var selected = StatsView.MONTH
        render { selected = it }
        composeRule.onNodeWithTag(CHART_TAG).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        // 图表把横向拖动认领了：视图不动（浮层随手指扫过，松手收起）
        assertEquals(StatsView.MONTH, selected)
    }

    @Test
    fun `点分段也能翻页`() {
        var selected = StatsView.MONTH
        render { selected = it }
        composeRule.onNodeWithText("年统计").performClick()
        composeRule.waitForIdle()
        assertEquals(StatsView.YEAR, selected)
    }
}
