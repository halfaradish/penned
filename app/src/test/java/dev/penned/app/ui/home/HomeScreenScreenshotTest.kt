package dev.penned.app.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首页：月汇总/预算合并卡随列表滚动 + 右下角 FAB 在「添加一条新记账」滚出视野后接管。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.systemDefault()

    /** 跨 7 天 14 行，保证列表可滚动（卡片与按钮能滚出视野）。 */
    private val rows: List<TxWithCategory> = buildList {
        var id = 0
        for (dayOffset in 0 until 7) {
            val day = LocalDate.now(zone).minusDays(dayOffset.toLong())
            repeat(2) { i ->
                id++
                add(
                    TxWithCategory(
                        id = "tx-$id",
                        type = if (i == 0) TxType.EXPENSE else TxType.INCOME,
                        amountCents = if (i == 0) 1600L + dayOffset * 100 else 150000L,
                        accountId = null,
                        categoryId = null,
                        dateEpochMinute = day.atTime(12, 30).atZone(zone).toEpochSecond() / 60,
                        note = "第 ${dayOffset + 1} 天第 ${i + 1} 笔",
                        categoryName = if (i == 0) "晚餐" else "工资",
                        categoryParentName = if (i == 0) "食品餐饮" else null,
                        categoryIconKey = null,
                        targetAccountName = null,
                        excludeFromStats = false,
                    )
                )
            }
        }
    }

    private val budget = HomeViewModel.BudgetSummary(totalCents = 300000, spentCents = 2200)

    @androidx.compose.runtime.Composable
    private fun content(onAdd: () -> Unit = {}) = HomeContent(
        bookName = "日常账本",
        rows = rows,
        onAdd = onAdd,
        budgetSummary = budget,
    )

    private fun tx(
        id: String,
        date: LocalDate,
        type: TxType,
        cents: Long,
        name: String?,
        parent: String?,
        note: String?,
        hour: Int = 12,
        minute: Int = 30,
    ) = TxWithCategory(
        id = id,
        type = type,
        amountCents = cents,
        accountId = null,
        categoryId = null,
        dateEpochMinute = date.atTime(hour, minute).atZone(zone).toEpochSecond() / 60,
        note = note,
        categoryName = name,
        categoryParentName = parent,
        categoryIconKey = null,
        targetAccountName = null,
        excludeFromStats = false,
    )

    /** 本月一号 / 今天 / 上月一号 / 去年同一天：一张月卡一个月份，最后一张要带年份。 */
    private fun monthRows(): List<TxWithCategory> {
        val today = LocalDate.now(zone)
        val firstOfMonth = today.withDayOfMonth(1)
        return listOf(
            tx("cur-1", firstOfMonth, TxType.EXPENSE, 1200, "晚餐", "食品餐饮", "本月一号", hour = 19, minute = 30),
            tx("cur-2", today, TxType.INCOME, 300000, "工资", null, null, hour = 8, minute = 5),
            tx("prev-1", firstOfMonth.minusMonths(1), TxType.EXPENSE, 4500, "打车", "出行交通", null),
            tx("old-1", firstOfMonth.minusYears(1), TxType.EXPENSE, 9900, "夜宵", "食品餐饮", "去年的账"),
        )
    }

    @Test
    fun captureHomeTop() {
        composeRule.setContent { PennedTheme { content() } }
        // 月汇总卡（本月）：支/收/结余三列
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("收入").assertIsDisplayed()
        composeRule.onNodeWithText("结余").assertIsDisplayed()
        // 预算卡：与月汇总同款三列 + 进度条
        composeRule.onNodeWithText("预算").assertIsDisplayed()
        composeRule.onNodeWithText("已用").assertIsDisplayed()
        composeRule.onNodeWithText("剩余").assertIsDisplayed()
        composeRule.onNodeWithText("添加一条新记账").assertIsDisplayed()
        // 当天日头条：相对词在最前（参考图口径）；行内时段
        composeRule.onNodeWithText("今天", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("12:30").onFirst().assertIsDisplayed()
        // 按钮还在视野里 → FAB 不出现
        composeRule.onNodeWithTag("home-fab").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_home_top.png")
    }

    @Test
    fun captureMonthCards() {
        composeRule.setContent { PennedTheme { HomeContent(bookName = "日常账本", rows = monthRows(), onAdd = {}) } }
        val today = LocalDate.now(zone)
        // 月标题行：只显示 yyyy/m
        composeRule.onNodeWithText("${today.year}/${today.monthValue}").assertIsDisplayed()
        // 年只在月标题里以 yyyy/m 出现——日头条的日期不带年份（本年）
        composeRule.onNodeWithText("${today.year}年", substring = true).assertDoesNotExist()
        // 当日收入不为 0 → 日头条带「收」
        composeRule.onNodeWithText("收 3,000.00", substring = true).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ag_home_month_cards.png")
    }

    @Test
    fun crossYearDayHeaderCarriesYear() {
        composeRule.setContent { PennedTheme { HomeContent(bookName = "日常账本", rows = monthRows(), onAdd = {}) } }
        val lastYearDay = LocalDate.now(zone).withDayOfMonth(1).minusYears(1)
        // 去年的那张卡：日头条带 yyyy年（截图基线见 captureMonthCards）
        composeRule.onNodeWithTag("home-list")
            .performScrollToNode(hasText("${lastYearDay.year}年", substring = true))
        composeRule.onNodeWithText(
            "${lastYearDay.year}年${lastYearDay.monthValue}月${lastYearDay.dayOfMonth}日",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun dayHeaderHidesZeroIncome() {
        val day = LocalDate.now(zone)
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = listOf(tx("only-expense", day, TxType.EXPENSE, 1800, "晚餐", "食品餐饮", null)),
                    onAdd = {},
                )
            }
        }
        // 只有支出的一天：整条日头条就是「支 18.00」——精确匹配即证明没有「收」那一段
        composeRule.onNodeWithText("支 18.00").assertIsDisplayed()
    }

    @Test
    fun captureHomeScrolledWithFab() {
        var added = 0
        composeRule.setContent { PennedTheme { content(onAdd = { added++ }) } }
        // 往下滚：卡片与"添加一条新记账"滚出视野
        composeRule.onNodeWithTag("home-list").performScrollToIndex(6)
        composeRule.onNodeWithText("添加一条新记账").assertDoesNotExist()
        composeRule.onNodeWithText("剩余").assertDoesNotExist()
        // FAB 接管
        composeRule.onNodeWithTag("home-fab").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_home_scrolled.png")
        composeRule.onNodeWithTag("home-fab").performClick()
        org.junit.Assert.assertEquals(1, added)
    }

    @Test
    fun captureBudgetOverspent() {
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = rows,
                    onAdd = {},
                    // 已用 > 预算：剩余显示红色负数、进度条转红
                    budgetSummary = HomeViewModel.BudgetSummary(totalCents = 100000, spentCents = 123456),
                )
            }
        }
        composeRule.onNodeWithText("-234.56").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_home_budget_overspent.png")
    }

    @Test
    fun fabHiddenInSelectionMode() {
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = rows,
                    onAdd = {},
                    budgetSummary = budget,
                    selectionMode = true,
                    selectedIds = setOf("tx-1"),
                )
            }
        }
        // 多选模式：合并卡与添加按钮都不渲染，FAB 也不该出现
        composeRule.onNodeWithText("已选 1 条").assertIsDisplayed()
        composeRule.onNodeWithTag("home-fab").assertDoesNotExist()
        composeRule.onNodeWithText("添加一条新记账").assertDoesNotExist()
        composeRule.onNodeWithText("剩余").assertDoesNotExist()
    }
}
