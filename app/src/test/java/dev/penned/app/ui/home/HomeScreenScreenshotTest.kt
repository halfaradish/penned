package dev.penned.app.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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

    @Test
    fun captureHomeTop() {
        composeRule.setContent { PennedTheme { content() } }
        // 合并卡：月汇总与预算同一张卡
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("收入").assertIsDisplayed()
        composeRule.onNodeWithText("结余").assertIsDisplayed()
        // 预算卡：与月汇总同款三列 + 进度条
        composeRule.onNodeWithText("预算").assertIsDisplayed()
        composeRule.onNodeWithText("已用").assertIsDisplayed()
        composeRule.onNodeWithText("剩余").assertIsDisplayed()
        composeRule.onNodeWithText("添加一条新记账").assertIsDisplayed()
        // 按钮还在视野里 → FAB 不出现
        composeRule.onNodeWithTag("home-fab").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_home_top.png")
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
