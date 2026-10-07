package dev.penned.app.ui.stats

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.StatsPeriod
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.StatsStrip
import dev.penned.app.domain.TrendAxis
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

    /** 切换条的跨度起点：2024-03 起有流水，条会一直排到本月。 */
    private val earliest = YearMonth.of(2024, 3)

    /** 按日柱高：只在给定下标放钱、其余补 0（截图里看着像真数据）。 */
    private fun dailyBars(count: Int, vararg nonZero: Pair<Int, Long>): List<Long> =
        List(count) { 0L }.toMutableList().also { bars -> nonZero.forEach { bars[it.first] = it.second } }

    private fun dayAxis(startDay: LocalDate, count: Int) = TrendAxis.Days(startDay.toEpochDay(), count)

    private fun stripMonths(include: YearMonth? = null): List<YearMonth> =
        StatsStrip.months(earliest, YearMonth.from(today), include)

    private fun stripYears(include: Int? = null): List<Int> =
        StatsStrip.years(earliest, YearMonth.from(today), include)

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
                    // 31 天按日柱：标签稀疏（每 6 根一个），横轴两行
                    trendBars = dailyBars(31, 3 to 1600L, 11 to 3000L, 19 to 9500L, 28 to 4200L),
                    trendAxis = dayAxis(LocalDate.of(2026, 10, 1), 31),
                    stripMonths = stripMonths(YearMonth.of(2026, 10)),
                    stripYears = stripYears(),
                    today = today,
                )
            }
        }
        // 时间维度四分段都在、当前维度是月统计
        composeRule.onNodeWithText("近七日").assertIsDisplayed()
        composeRule.onNodeWithText("月统计").assertIsDisplayed()
        composeRule.onNodeWithText("年统计").assertIsDisplayed()
        composeRule.onNodeWithText("自定义").assertIsDisplayed()
        // 期选择行换成切换条：左边一列年份 + 可横滑的月格（本月在最前），旧的箭头行没了
        composeRule.onAllNodesWithText("2026").assertCountEquals(1)
        composeRule.onNodeWithText("本月").assertIsDisplayed()
        composeRule.onNodeWithText("上月").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("上一期").assertDoesNotExist()
        // 趋势卡在汇总卡下方（在占比卡之前）
        composeRule.onNodeWithText("支出趋势").assertIsDisplayed()
        // 占比 + 数据合成切换式：默认支出侧，行里带笔数与占比
        composeRule.onNodeWithText("支出占比").assertIsDisplayed()
        composeRule.onNodeWithText("支出数据").assertIsDisplayed()
        composeRule.onNodeWithText("110笔  48%").assertIsDisplayed()
        // 收入不再是一整段卡片，只在卡头切换里出现
        composeRule.onNodeWithText("收入构成").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_month.png")
    }

    @Test
    fun captureStatsMonthStripScrolled() {
        // 选中一个老月份：条自动滚过去，左边年份列跟着变成 2025（参考图 p2 的行为）
        val period = StatsPeriod.Month(YearMonth.of(2025, 12))
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    period = period,
                    window = StatsPeriods.window(period, today = today),
                    totals = StatsMath.PeriodTotals(incomeCents = 0, expenseCents = 32300),
                    expenseCategories = expenseCategories,
                    incomeCategories = emptyList(),
                    trendBars = dailyBars(31, 4 to 1580L, 17 to 3900L, 24 to 2200L),
                    trendAxis = dayAxis(LocalDate.of(2025, 12, 1), 31),
                    stripMonths = stripMonths(YearMonth.of(2025, 12)),
                    stripYears = stripYears(),
                    today = today,
                )
            }
        }
        composeRule.onAllNodesWithText("2025").assertCountEquals(1)
        composeRule.onNodeWithText("12月").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ak_stats_strip_scrolled.png")
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
                    // 收入侧：一笔工资落在 10 号
                    trendBars = dailyBars(31, 9 to 350000L),
                    trendAxis = dayAxis(LocalDate.of(2026, 10, 1), 31),
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
                    // 近七日＝7 根日柱，横轴两行（周几 / 月/日）
                    trendBars = listOf(0, 1600, 0, 0, 3000, 0, 250),
                    trendAxis = TrendAxis.Days(LocalDate.of(2026, 10, 1).toEpochDay(), 7),
                    today = today,
                )
            }
        }
        // 区间文案行已去掉（窗口信息由趋势卡横轴承担）
        composeRule.onNodeWithText("10月1日–10月7日").assertDoesNotExist()
        composeRule.onNodeWithText("支出趋势").assertIsDisplayed()
        composeRule.onNodeWithText("周四").assertIsDisplayed()
        composeRule.onNodeWithText("10/1").assertIsDisplayed()
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
                    // 年统计的趋势＝12 个月柱，只有 3 月与 10 月有数据
                    trendBars = List(12) { 0L }.toMutableList().also { it[2] = 420000; it[9] = 32300 },
                    trendAxis = TrendAxis.Months,
                    stripMonths = stripMonths(),
                    stripYears = stripYears(2026),
                    today = today,
                )
            }
        }
        // 年统计的期选择行也是切换条：一行年份格（最前是今年）
        composeRule.onAllNodesWithText("2026").assertCountEquals(1)
        composeRule.onNodeWithText("2025").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("下一期").assertDoesNotExist()
        // 趋势卡挪到汇总卡下方（不再是页面底部的「月度趋势」）
        composeRule.onNodeWithText("支出趋势").assertIsDisplayed()
        composeRule.onNodeWithText("月度趋势").assertDoesNotExist()
        composeRule.onNodeWithText("1月").assertIsDisplayed()
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
                    // 15 天区间：按日 15 根，横轴两行
                    trendBars = List(15) { 0L }.toMutableList().also { it[3] = 1600; it[9] = 3000 },
                    trendAxis = TrendAxis.Days(LocalDate.of(2026, 10, 1).toEpochDay(), 15),
                    today = today,
                )
            }
        }
        // 自定义：两个日期 chip
        composeRule.onNodeWithText("开始").assertIsDisplayed()
        composeRule.onNodeWithText("10月1日").assertIsDisplayed()
        composeRule.onNodeWithText("结束").assertIsDisplayed()
        composeRule.onNodeWithText("10月15日").assertIsDisplayed()
        composeRule.onNodeWithText("支出趋势").assertIsDisplayed()
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
