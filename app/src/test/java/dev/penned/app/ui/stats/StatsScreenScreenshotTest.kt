package dev.penned.app.ui.stats

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.StatsPeriod
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.StatsStrip
import dev.penned.app.domain.StatsTrend
import dev.penned.app.domain.StatsView
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

    /** 造一页数据（四个视图各一页；截图只看当前页，其余页给空壳即可）。 */
    private fun pageOf(
        period: StatsPeriod,
        totalExpense: Long = 32300,
        totalIncome: Long = 0,
        categories: List<StatsMath.CategoryTotal> = emptyList(),
        incomeCategories: List<StatsMath.CategoryTotal> = emptyList(),
        bars: List<Long> = emptyList(),
        today: LocalDate = LocalDate.of(2026, 10, 7),
    ): StatsPage {
        val window = StatsPeriods.window(period, today = today)
        return StatsPage(
            period = period,
            window = window,
            totals = StatsMath.PeriodTotals(incomeCents = totalIncome, expenseCents = totalExpense),
            expenseCategories = categories,
            incomeCategories = incomeCategories,
            trendBars = bars,
            trendAxis = StatsTrend.axisOf(period, window),
        )
    }

    /** 按日柱高：只在给定下标放钱、其余补 0（截图里看着像真数据）。 */
    private fun dailyBars(count: Int, vararg nonZero: Pair<Int, Long>): List<Long> =
        List(count) { 0L }.toMutableList().also { bars -> nonZero.forEach { bars[it.first] = it.second } }

    private fun stripMonths(include: YearMonth? = null): List<YearMonth> =
        StatsStrip.months(earliest, YearMonth.from(today), include)

    private fun stripYears(include: Int? = null): List<Int> =
        StatsStrip.years(earliest, YearMonth.from(today), include)

    @Test
    fun captureStatsScreen() {
        val month = StatsPeriod.Month(YearMonth.of(2026, 10))
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    view = StatsView.MONTH,
                    pages = mapOf(
                        StatsView.MONTH to pageOf(
                            period = month,
                            totalIncome = 350000,
                            categories = expenseCategories,
                            incomeCategories = incomeCategories,
                            // 31 天按日柱：标签稀疏（每 6 根一个），月统计不写周几、只标 月/日
                            bars = dailyBars(31, 3 to 1600L, 11 to 3000L, 19 to 9500L, 28 to 4200L),
                        ),
                    ),
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
                    view = StatsView.MONTH,
                    pages = mapOf(
                        StatsView.MONTH to pageOf(
                            period = period,
                            categories = expenseCategories,
                            bars = dailyBars(31, 4 to 1580L, 17 to 3900L, 24 to 2200L),
                        ),
                    ),
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
        val month = StatsPeriod.Month(YearMonth.of(2026, 10))
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    view = StatsView.MONTH,
                    pages = mapOf(
                        StatsView.MONTH to pageOf(
                            period = month,
                            totalIncome = 350000,
                            categories = expenseCategories,
                            incomeCategories = incomeCategories,
                            // 收入侧：一笔工资落在 10 号
                            bars = dailyBars(31, 9 to 350000L),
                        ),
                    ),
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
                    view = StatsView.MONTH,
                    pages = mapOf(
                        StatsView.MONTH to pageOf(
                            period = StatsPeriod.Month(YearMonth.of(2026, 10)),
                            totalExpense = 0,
                            totalIncome = 350000,
                            incomeCategories = incomeCategories,
                        ),
                    ),
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
                    view = StatsView.LAST_7_DAYS,
                    pages = mapOf(
                        StatsView.LAST_7_DAYS to pageOf(
                            period = period,
                            categories = expenseCategories,
                            // 近七日＝7 根日柱，横轴两行（周几 / 月/日）
                            bars = listOf(0, 1600, 0, 0, 3000, 0, 250),
                        ),
                    ),
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
                    view = StatsView.YEAR,
                    pages = mapOf(
                        StatsView.YEAR to pageOf(
                            period = period,
                            totalIncome = 350000,
                            categories = expenseCategories,
                            incomeCategories = incomeCategories,
                            // 年统计的趋势＝12 个月柱，只有 3 月与 10 月有数据
                            bars = List(12) { 0L }.toMutableList().also { it[2] = 420000; it[9] = 32300 },
                        ),
                    ),
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
                    view = StatsView.CUSTOM,
                    pages = mapOf(
                        StatsView.CUSTOM to pageOf(
                            period = period,
                            totalIncome = 350000,
                            categories = expenseCategories,
                            incomeCategories = incomeCategories,
                            // 15 天区间：按日 15 根，横轴两行
                            bars = dailyBars(15, 3 to 1600L, 9 to 3000L),
                        ),
                    ),
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
    fun captureStatsLongRange() {
        // 一整年的自定义区间：柱子变细、横轴退回单行月份（与分类详细页同口径）
        val start = LocalDate.of(2025, 9, 1)
        val period = StatsPeriods.custom(start.toEpochDay(), LocalDate.of(2026, 8, 31).toEpochDay())
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    view = StatsView.CUSTOM,
                    pages = mapOf(
                        StatsView.CUSTOM to pageOf(
                            period = period,
                            categories = expenseCategories,
                            bars = dailyBars(365, 20 to 1600L, 120 to 5200L, 250 to 3000L, 350 to 9500L),
                        ),
                    ),
                    today = today,
                )
            }
        }
        // 长窗口不标周几，只标月份
        composeRule.onNodeWithText("9月").assertIsDisplayed()
        composeRule.onAllNodesWithText("周四").assertCountEquals(0)
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/al_stats_long_range.png")
    }

    @Test
    fun captureStatsTooltip() {
        // 按住最左边那根柱（第 0 根，按在刻度栏右侧也算第 0 根）→ 浮层给出日期 + 金额
        val period = StatsPeriod.Month(YearMonth.of(2026, 10))
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    view = StatsView.MONTH,
                    pages = mapOf(
                        StatsView.MONTH to pageOf(
                            period = period,
                            categories = expenseCategories,
                            bars = dailyBars(31, 0 to 1234L, 3 to 1600L, 11 to 3000L, 19 to 9500L),
                        ),
                    ),
                    stripMonths = stripMonths(YearMonth.of(2026, 10)),
                    stripYears = stripYears(),
                    today = today,
                )
            }
        }
        composeRule.onNodeWithTag(CHART_TAG).performTouchInput { down(Offset(2f, center.y)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("10月1日").assertIsDisplayed()
        composeRule.onNodeWithText("支出 12.34").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/am_stats_tooltip.png")
        composeRule.onNodeWithTag(CHART_TAG).performTouchInput { up() }
    }

    @Test
    fun captureStatsScreenEmpty() {
        val period = StatsPeriod.Last7Days
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    view = StatsView.LAST_7_DAYS,
                    pages = mapOf(
                        StatsView.LAST_7_DAYS to pageOf(period = period, totalExpense = 0),
                    ),
                    today = today,
                )
            }
        }
        // 空态文案跟着期走（不再一律"本月"）
        composeRule.onNodeWithText("近七日没有收支记录").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_empty.png")
    }
}
