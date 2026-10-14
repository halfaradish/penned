package dev.penned.app.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.TxType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 组合筛选链路（M3-7）：真库 + 真导航图。关键词、分类（含二级）、标签、开关、清空——
 * 每一步都断言结果集与汇总（N 笔 · 收入 · 支出）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilterFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-filter-flow"
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun minute(day: Int, hour: Int = 12) =
        LocalDate.of(2026, 10, day).atTime(hour, 0).atZone(zone).toEpochSecond() / 60

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(id = "p1", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(id = "s1", bookId = bookId, parentId = "p1", name = "午餐", type = CategoryType.EXPENSE, sortOrder = 1, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(id = "p2", bookId = bookId, name = "工资", type = CategoryType.INCOME, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.accountDao().upsert(
            AccountEntity(id = "a1", bookId = bookId, name = "微信", type = AccountType.DEBIT, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.tagDao().upsert(
            TagEntity(id = "g1", bookId = bookId, name = "出差", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.transactionDao().upsertAll(
            listOf(
                TransactionEntity(
                    id = "e-lunch", bookId = bookId, type = TxType.EXPENSE, amountCents = 1650,
                    accountId = "a1", categoryId = "s1", dateEpochMinute = minute(10), note = "公司楼下",
                    creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
                ),
                TransactionEntity(
                    id = "e-taxi", bookId = bookId, type = TxType.EXPENSE, amountCents = 3000,
                    accountId = "a1", categoryId = "p1", dateEpochMinute = minute(12), note = "打车去机场",
                    creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
                ),
                TransactionEntity(
                    id = "i-salary", bookId = bookId, type = TxType.INCOME, amountCents = 350_000,
                    accountId = "a1", categoryId = "p2", dateEpochMinute = minute(5),
                    creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
                ),
                TransactionEntity(
                    id = "e-off", bookId = bookId, type = TxType.EXPENSE, amountCents = 500,
                    accountId = "a1", categoryId = "s1", dateEpochMinute = minute(11),
                    excludeFromStats = true, creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
                ),
            )
        )
        app.database.tagDao().setTagsForTx("e-taxi", listOf("g1"))
        app.settings.setCurrentBook(bookId)
    }

    private fun renderApp(app: PennedApplication) {
        composeRule.setContent {
            PennedTheme {
                CompositionLocalProvider(LocalApp provides app) { PennedApp(app) }
            }
        }
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun countSub(text: String): Int =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun awaitUi(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (predicate()) return
            Thread.sleep(20)
        }
        error("等界面超时")
    }

    private fun openFilterPage() {
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.waitUntil(5_000) { count("筛选") == 1 }
        awaitUi { count("输入关键词，或点上面的条件开始筛选") == 1 }
    }

    @Test
    fun `关键词筛选：结果与汇总跟着走`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        openFilterPage()

        composeRule.onNode(hasSetTextAction()).performTextInput("打车")
        awaitUi { count("1 笔 · 收入 0.00 · 支出 30.00") == 1 }
        composeRule.onNodeWithText("打车去机场").assertIsDisplayed()

        // 换成收入的关键词：结果变空
        composeRule.onNodeWithContentDescription("清除").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("工资")
        awaitUi { count("1 笔 · 收入 3,500.00 · 支出 0.00") == 1 }
    }

    @Test
    fun `分类筛选含二级：一级选中连子树，汇总按统计口径（不计收支的不进合计）`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        openFilterPage()

        // 分类：选一级「食品餐饮」→ 连二级「午餐」一起（3 笔：30.00 + 5.00 + 16.50）
        composeRule.onNodeWithText("分类").performClick()
        composeRule.waitUntil(5_000) { count("食品餐饮") == 1 }
        composeRule.onNodeWithText("食品餐饮").performClick()
        composeRule.onNodeWithText("选好了").performClick()
        // 汇总走 StatsMath.totals：不计收支的那 5.00 不出现在合计里（与统计页同一口径）
        awaitUi { count("3 笔 · 收入 0.00 · 支出 46.50") == 1 }
        composeRule.onNodeWithText("分类 · 1").assertIsDisplayed()

        // 清空：回到初始提示
        composeRule.onNodeWithText("清空").performClick()
        awaitUi { count("输入关键词，或点上面的条件开始筛选") == 1 }
    }

    @Test
    fun `标签筛选与清空`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        openFilterPage()

        composeRule.onNodeWithText("标签").performClick()
        composeRule.waitUntil(5_000) { count("出差") == 1 }
        composeRule.onNodeWithText("出差").performClick()
        composeRule.onNodeWithText("选好了").performClick()
        awaitUi { count("1 笔 · 收入 0.00 · 支出 30.00") == 1 }
        composeRule.onNodeWithText("标签 · 1").assertIsDisplayed()

        // 清空：回到初始提示
        composeRule.onNodeWithText("清空").performClick()
        awaitUi { count("输入关键词，或点上面的条件开始筛选") == 1 }
        composeRule.onNodeWithText("标签").assertIsDisplayed()
    }

    @Test
    fun `类型筛选：转账与收入分开`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        openFilterPage()

        composeRule.onNodeWithText("类型").performClick()
        composeRule.waitUntil(5_000) { count("全部") == 1 }
        composeRule.onNodeWithText("收入").performClick()
        awaitUi { count("1 笔 · 收入 3,500.00 · 支出 0.00") == 1 }
        composeRule.onAllNodesWithText("收入").onFirst().assertIsDisplayed()
    }
}
