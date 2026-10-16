package dev.penned.app.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.StatsPeriod
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.StatsStrip
import dev.penned.app.domain.StatsView
import dev.penned.app.ui.stats.StatsContent
import dev.penned.app.ui.stats.StatsPage
import dev.penned.app.ui.theme.PennedTheme
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
 * 快速定位（AK 轮）：自绘「年 | 月」双列滚轮。
 * 滚轮本体单独测——弹窗窗口在 Robolectric 里不便截图，所以内容抽成 [YearMonthWheel]。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class YearMonthPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = YearMonth.of(2026, 10)

    /** 条 / 弹窗里的年份是"新→旧"，滚轮内部翻成自上而下递增（与参考图同向）。 */
    private val stripYears: List<Int> = StatsStrip.years(YearMonth.of(2024, 3), today)
    private val years: List<Int> = stripYears.reversed()
    private val months: List<Int> = StatsStrip.pickerMonths(2026, today)

    @Test
    fun captureWheel() {
        composeRule.setContent {
            PennedTheme {
                YearMonthWheel(
                    years = years,
                    months = months,
                    selectedYear = 2025,
                    selectedMonth = 8,
                    onYearChange = {},
                    onMonthChange = {},
                    modifier = Modifier.width(240.dp),
                )
            }
        }
        // 居中那格就是当前选中：年份列 2025，月份列 8
        composeRule.onNodeWithText("2025").assertIsDisplayed()
        composeRule.onNodeWithText("8").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ak_stats_wheel.png")
    }

    @Test
    fun `wheel keeps the selection it was opened with`() {
        var year = 2025
        var month = 8
        composeRule.setContent {
            PennedTheme {
                YearMonthWheel(
                    years = years,
                    months = months,
                    selectedYear = year,
                    selectedMonth = month,
                    onYearChange = { year = it },
                    onMonthChange = { month = it },
                    modifier = Modifier.width(240.dp),
                )
            }
        }
        composeRule.waitForIdle()
        // 首帧不许把选中顶成第一项（两边都是：年份列中段、月份列中段）
        assertEquals(2025, year)
        assertEquals(8, month)
    }

    @Test
    fun `wheel keeps the selection when it sits at either end`() {
        // 最老的一年 / 最新的一个月：内容 padding 对称性不够时这里会被顶到邻格
        var year = years.first()
        var month = months.last()
        composeRule.setContent {
            PennedTheme {
                YearMonthWheel(
                    years = years,
                    months = months,
                    selectedYear = year,
                    selectedMonth = month,
                    onYearChange = { year = it },
                    onMonthChange = { month = it },
                    modifier = Modifier.width(240.dp),
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(years.first(), year)
        assertEquals(months.last(), month)
    }

    @Test
    fun `tapping a number brings it to the middle and reports it`() {
        var year = 2026
        composeRule.setContent {
            PennedTheme {
                YearMonthWheel(
                    years = years,
                    months = months,
                    selectedYear = year,
                    selectedMonth = 10,
                    onYearChange = { year = it },
                    onMonthChange = {},
                    modifier = Modifier.width(240.dp),
                )
            }
        }
        composeRule.onNodeWithText("2024").performClick()
        composeRule.waitForIdle()
        assertEquals(2024, year)
    }

    @Test
    fun `month view opens the picker from the year column`() {
        val period = StatsPeriod.Month(YearMonth.of(2026, 10))
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    view = StatsView.MONTH,
                    pages = mapOf(
                        StatsView.MONTH to StatsPage(
                            period = period,
                            window = StatsPeriods.window(period, today = LocalDate.of(2026, 10, 7)),
                            totals = StatsMath.PeriodTotals(incomeCents = 0, expenseCents = 32300),
                        ),
                    ),
                    // 条上不给月份格：弹窗里的年份数字才唯一，能直接比上下顺序
                    stripMonths = emptyList(),
                    stripYears = stripYears,
                    today = LocalDate.of(2026, 10, 7),
                )
            }
        }
        composeRule.onNodeWithContentDescription("快速定位").performClick()
        composeRule.onNodeWithText("选择年月").assertIsDisplayed()
        composeRule.onNodeWithText("确定").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertIsDisplayed()

        // 进弹窗的年份列必须自上而下递增——这是真机验出来的坑：条是"新→旧"，
        // 直接喂给滚轮就会上下颠倒（2026 在上、2024 在下），与参考图相反
        val y2025 = composeRule.onNodeWithText("2025").fetchSemanticsNode().boundsInRoot.top
        val y2024 = composeRule.onNodeWithText("2024").fetchSemanticsNode().boundsInRoot.top
        assertTrue("2024 应排在 2025 上面（自上而下递增）", y2024 < y2025)
    }
}
