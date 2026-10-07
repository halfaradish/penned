package dev.penned.app.ui.stats

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.StatsPeriod
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatsScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 测试里的"今天"：让近七日/自定义的文案与年份判定可复现。 */
    private val today = LocalDate.of(2026, 10, 7)

    private val expenseCategories = listOf(
        StatsMath.CategoryTotal("食品餐饮", 15600, 110, 0.483f),
        StatsMath.CategoryTotal("购物消费", 9500, 15, 0.294f),
        StatsMath.CategoryTotal("出行交通", 4200, 6, 0.13f),
        StatsMath.CategoryTotal("其他", 3000, 4, 0.093f),
    )

    private val incomeCategories = listOf(StatsMath.CategoryTotal("工资", 350000, 1, 1f))

    @Test
    fun captureStatsScreen() {
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    period = StatsPeriod.Month(YearMonth.of(2026, 10)),
                    window = StatsPeriods.window(StatsPeriod.Month(YearMonth.of(2026, 10)), today = today),
                    totals = StatsMath.PeriodTotals(incomeCents = 350000, expenseCents = 32300),
                    expenseCategories = expenseCategories,
                    incomeCategories = incomeCategories,
                    today = today,
                )
            }
        }
        // 时间维度四分段都在、当前维度是月统计
        composeRule.onNodeWithText("近七日").assertIsDisplayed()
        composeRule.onNodeWithText("月统计").assertIsDisplayed()
        composeRule.onNodeWithText("年统计").assertIsDisplayed()
        composeRule.onNodeWithText("自定义").assertIsDisplayed()
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
        // 占比 + 数据合成切换式：默认支出侧，行里带笔数与占比
        composeRule.onNodeWithText("支出占比").assertIsDisplayed()
        composeRule.onNodeWithText("支出数据").assertIsDisplayed()
        composeRule.onNodeWithText("110笔  48%").assertIsDisplayed()
        // 收入不再是一整段卡片，只在卡头切换里出现
        composeRule.onNodeWithText("收入构成").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_month.png")
    }

    @Test
    fun captureStatsIncomeSide() {
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    period = StatsPeriod.Month(YearMonth.of(2026, 10)),
                    window = StatsPeriods.window(StatsPeriod.Month(YearMonth.of(2026, 10)), today = today),
                    totals = StatsMath.PeriodTotals(incomeCents = 350000, expenseCents = 32300),
                    expenseCategories = expenseCategories,
                    incomeCategories = incomeCategories,
                    type = TxType.INCOME,
                    today = today,
                )
            }
        }
        // 切到收入：标题、环心、数据行整卡跟着换
        composeRule.onNodeWithText("收入占比").assertIsDisplayed()
        composeRule.onNodeWithText("总收入").assertIsDisplayed()
        composeRule.onNodeWithText("收入数据").assertIsDisplayed()
        composeRule.onNodeWithText("1笔  100%").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_income.png")
    }

    @Test
    fun captureStatsSideWithoutData() {
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    period = StatsPeriod.Month(YearMonth.of(2026, 10)),
                    window = StatsPeriods.window(StatsPeriod.Month(YearMonth.of(2026, 10)), today = today),
                    totals = StatsMath.PeriodTotals(incomeCents = 350000, expenseCents = 0),
                    expenseCategories = emptyList(),
                    incomeCategories = incomeCategories,
                    type = TxType.EXPENSE,
                    today = today,
                )
            }
        }
        // 支出侧本期没数据：卡内给句话，切换器还在原处
        composeRule.onNodeWithText("本期没有支出记录").assertIsDisplayed()
        composeRule.onNodeWithText("支出占比").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_empty_side.png")
    }

    @Test
    fun captureStatsLast7Days() {
        val period = StatsPeriod.Last7Days
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    period = period,
                    window = StatsPeriods.window(period, today = today),
                    totals = StatsMath.PeriodTotals(incomeCents = 0, expenseCents = 32300),
                    expenseCategories = expenseCategories,
                    incomeCategories = emptyList(),
                    today = today,
                )
            }
        }
        // 近七日只报区间，没有翻页箭头
        composeRule.onNodeWithText("10月1日–10月7日").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_last7days.png")
    }

    @Test
    fun captureStatsYear() {
        val period = StatsPeriod.Year(2026)
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    period = period,
                    window = StatsPeriods.window(period, today = today),
                    totals = StatsMath.PeriodTotals(incomeCents = 350000, expenseCents = 32300),
                    expenseCategories = expenseCategories,
                    incomeCategories = incomeCategories,
                    today = today,
                )
            }
        }
        composeRule.onNodeWithText("2026年").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_year.png")
    }

    @Test
    fun captureStatsCustomRange() {
        val period = StatsPeriods.custom(
            LocalDate.of(2026, 10, 1).toEpochDay(),
            LocalDate.of(2026, 10, 15).toEpochDay(),
        )
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    period = period,
                    window = StatsPeriods.window(period, today = today),
                    totals = StatsMath.PeriodTotals(incomeCents = 350000, expenseCents = 32300),
                    expenseCategories = expenseCategories,
                    incomeCategories = incomeCategories,
                    today = today,
                )
            }
        }
        // 自定义：两个日期 chip
        composeRule.onNodeWithText("开始").assertIsDisplayed()
        composeRule.onNodeWithText("10月1日").assertIsDisplayed()
        composeRule.onNodeWithText("结束").assertIsDisplayed()
        composeRule.onNodeWithText("10月15日").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_custom.png")
    }

    @Test
    fun captureStatsScreenEmpty() {
        val period = StatsPeriod.Last7Days
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    period = period,
                    window = StatsPeriods.window(period, today = today),
                    totals = StatsMath.PeriodTotals(0, 0),
                    expenseCategories = emptyList(),
                    incomeCategories = emptyList(),
                    emptyTitle = StatsPeriods.emptyTitle(period, today = today),
                    today = today,
                )
            }
        }
        // 空态文案跟着期走（不再一律"本月"）
        composeRule.onNodeWithText("近七日没有收支记录").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_empty.png")
    }
}
