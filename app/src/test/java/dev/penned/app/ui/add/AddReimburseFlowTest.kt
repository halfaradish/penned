package dev.penned.app.ui.add

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.ReimburseState
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 报销链路（M3-3）：真库 + 真导航图——记一笔选「待报销」+ 报销账户 → 落库 → 详情行显示
 * 状态 · 金额 · 账户；报销金额行把键盘指过去按自定额存；改状态后资产页「可报/已报」两张数字换边。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddReimburseFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-reimburse-flow"

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-shopping", bookId = bookId, name = "购物消费", type = CategoryType.EXPENSE,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.accountDao().upsert(
            AccountEntity(
                id = "acc-wechat", bookId = bookId, name = "微信", type = AccountType.DEBIT,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
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

    private fun press(key: String) = composeRule.onNodeWithTag("amount-key-$key").performClick()

    /** 真时间轮询界面条件（Room/DataStore 在后台线程上，虚拟时钟推不出结果）。 */
    private fun awaitUi(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (predicate()) return
            Thread.sleep(20)
        }
        error("等界面超时")
    }

    private fun <T> awaitDb(timeoutMs: Long = 5_000, block: suspend () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            runBlocking { block() }?.let { return it }
            Thread.sleep(20)
        }
        error("等数据库结果超时")
    }

    private fun stored(app: PennedApplication): suspend () -> TransactionEntity? = {
        app.database.transactionDao().allInBook(bookId).firstOrNull()
    }

    /** 记一笔：备注 + 分类 + 金额 100。 */
    private fun fillBasicForm(note: String) {
        composeRule.onNodeWithText("添加一条新记账").performClick()
        composeRule.waitUntil(5_000) { count("添加备注") == 1 }
        composeRule.waitUntil(5_000) { count("购物消费") >= 1 }
        composeRule.onNode(hasSetTextAction()).performTextInput(note)
        composeRule.onAllNodesWithText("购物消费").onFirst().performClick()
        press("1"); press("0"); press("0")
    }

    @Test
    fun `待报销加账户_落库与详情行都对`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        fillBasicForm("差旅打车")

        // 报销抽屉：默认不报销 → 选待报销 → 账户/金额两行出现
        composeRule.onNodeWithText("不报销").performClick()
        composeRule.waitUntil(5_000) { count("选好了") == 1 }
        composeRule.onNodeWithText("待报销").performClick()
        composeRule.waitUntil(5_000) { count("报销账户") == 1 }
        composeRule.onNodeWithText("报销账户").assertIsDisplayed()
        composeRule.onNodeWithText("报销金额").assertIsDisplayed()
        composeRule.onNodeWithText("未指定").assertIsDisplayed()
        // 金额缺省 = 按流水净额全额 100.00
        composeRule.onNodeWithText("全额 100.00").assertIsDisplayed()

        // 报销账户 → 收起报销抽屉、起账户选择（两层不叠）
        composeRule.onNodeWithText("报销账户").performClick()
        composeRule.waitUntil(5_000) { count("选择账户") == 1 }
        composeRule.onNodeWithText("微信").performClick()
        composeRule.waitUntil(5_000) { count("选择账户") == 0 }

        // chip 回显状态；保存
        composeRule.onNodeWithText("待报销").assertIsDisplayed()
        press("完成")
        // 保存后首页提示条在（10s）：浮动记账入口会被提示条挡着，等提示条本身（M3-5 起）
        composeRule.waitUntil(5_000) { count("已记账") == 1 || count("已保存") == 1 }

        // 落库：状态 + 账户 + 金额（0 = 全额）
        val saved = awaitDb { stored(app)() }
        assertEquals(ReimburseState.PENDING, saved.reimburseState)
        assertEquals("acc-wechat", saved.reimburseAccountId)
        assertEquals(0L, saved.reimburseCents)

        // 详情页「报销」行 = 状态 · 金额（按全额 100.00）· 账户
        composeRule.waitUntil(5_000) { count("差旅打车") == 1 }
        composeRule.onNodeWithText("差旅打车").performClick()
        awaitUi { count("待报销 · 100.00 · 微信") == 1 }
        composeRule.onNodeWithText("报销").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("待报销 · 100.00 · 微信").assertIsDisplayed()
    }

    @Test
    fun `自定报销金额走键盘_改成已报销后可报已报换边`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        fillBasicForm("住酒店")

        // 待报销 → 报销金额行把键盘指过去
        composeRule.onNodeWithText("不报销").performClick()
        composeRule.waitUntil(5_000) { count("选好了") == 1 }
        composeRule.onNodeWithText("待报销").performClick()
        composeRule.waitUntil(5_000) { count("报销金额") == 1 }
        composeRule.onNodeWithText("报销金额").performClick()
        composeRule.waitUntil(5_000) { count("正在输入：报销金额") == 1 }
        press("4"); press("0")

        // 回主金额再存（键盘在报销格时「完成」也能存，这里走显式路径更好读）
        composeRule.onNodeWithText("回到金额").performClick()
        composeRule.waitUntil(5_000) { count("正在输入：报销金额") == 0 }
        press("完成")
        // 保存后首页提示条在（10s）：浮动记账入口会被提示条挡着，等提示条本身（M3-5 起）
        composeRule.waitUntil(5_000) { count("已记账") == 1 || count("已保存") == 1 }

        val saved = awaitDb { stored(app)() }
        assertEquals(ReimburseState.PENDING, saved.reimburseState)
        assertEquals(4_000L, saved.reimburseCents)
        // 可报 = 自定额 40.00（不是全额 100.00）
        assertEquals(4_000L, awaitDb { app.database.transactionDao().observeReimburseSums(bookId).first().pendingCents })

        // 编辑：状态改「已报销」→ 可报归零、已报 40.00
        composeRule.onNodeWithText("住酒店").performClick()
        // 详情页先画顶栏占位、数据到了才出明细——等**备注行**，别等只在占位帧也有的「账单详情」
        awaitUi { count("住酒店") == 1 }
        composeRule.onNodeWithText("编辑").performScrollTo().performClick()
        awaitUi { count("待报销") == 1 }
        composeRule.onNodeWithText("待报销").performClick()
        composeRule.waitUntil(5_000) { count("已报销") == 1 }
        composeRule.onNodeWithText("已报销").performClick()
        composeRule.onNodeWithText("选好了").performClick()
        press("完成")
        awaitUi { count("账单详情") == 1 }

        val edited = awaitDb { stored(app)() }
        assertEquals(ReimburseState.DONE, edited.reimburseState)
        assertEquals(4_000L, edited.reimburseCents)
        val sums = awaitDb { app.database.transactionDao().observeReimburseSums(bookId).first().let { it.doneCents.takeIf { d -> d > 0 } } }
        assertEquals(4_000L, sums)
        assertEquals(0L, runBlocking { app.database.transactionDao().observeReimburseSums(bookId).first().pendingCents })
        // 详情行跟着换词
        awaitUi { count("已报销 · 40.00") == 1 }
    }
}
