package dev.penned.app.ui.add

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.DebtEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.DebtDirection
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.TxType
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

/**
 * 债务链路（M3-4）：真库 + 真导航图。
 *
 * ①「债务」Tab 借入 → 落库带 `debtId` 且 `excludeFromStats = true`（借入不是收入：统计两数不变）、
 * 账户余额 +1000、债务剩余 1000；
 * ②债务详情「记一笔还款」→ 记一笔页预填债务 + 清偿那一格 + 剩余额 → 保存 → 已归还 1000、剩余 0、账户回到 0。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddDebtFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-debt-flow"

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.accountDao().upsert(
            AccountEntity(
                id = "acc-cash", bookId = bookId, name = "现金", type = AccountType.CASH,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.debtDao().upsert(
            DebtEntity(
                id = "debt-1", bookId = bookId, direction = DebtDirection.BORROW, counterparty = "张三",
                principalCents = 100_000L, startEpochDay = 20_000L,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.settings.setCurrentBook(bookId)
    }

    /** 借入那一笔（测试 ② 的前置）：钱已经进过账户，债务剩余 1000。 */
    private fun seedBorrowFlow(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-borrow", bookId = bookId, type = TxType.INCOME, amountCents = 100_000L,
                accountId = "acc-cash", dateEpochMinute = 30_000_000L, debtId = "debt-1",
                excludeFromStats = true, creatorDeviceId = "test",
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
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

    private suspend fun flows(app: PennedApplication): List<TransactionEntity> =
        app.database.transactionDao().allInBook(bookId).sortedBy { it.createdAt }

    /** 借入 1000：债务 Tab → 选债务 → 选账户 → 金额 → 完成。 */
    private fun borrowThousand() {
        composeRule.onNodeWithText("添加一条新记账").performClick()
        composeRule.waitUntil(5_000) { count("添加备注") == 1 }
        composeRule.onNodeWithText("债务").performClick()
        composeRule.waitUntil(5_000) { count("选择债务") == 1 }
        composeRule.onNodeWithText("选择债务").performClick()
        composeRule.waitUntil(5_000) { count("新建债务") == 1 }
        composeRule.onAllNodesWithText("张三").onFirst().performClick()
        // 选完抽屉收起：行上换成对方名（「新建债务」这一行消失）
        composeRule.waitUntil(5_000) { count("新建债务") == 0 }
        composeRule.onNodeWithText("张三").assertIsDisplayed()
        // 资金账户
        composeRule.onNodeWithText("选择账户").performClick()
        composeRule.waitUntil(5_000) { count("选择账户") >= 2 }
        composeRule.onAllNodesWithText("现金").onFirst().performClick()
        // 选完抽屉收起：行上换成账户名，占位「选择账户」消失
        awaitUi { count("选择账户") == 0 }
        press("1"); press("0"); press("0"); press("0")
        press("完成")
    }

    @Test
    fun `债务 Tab 借入：落库带 debtId、不计收支、账户与债务都对`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)

        borrowThousand()
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }

        // 落库：收入型流水（钱进账户）+ debtId + 默认不计收支 + 没有分类
        val saved = awaitDb { flows(app).firstOrNull { it.debtId == "debt-1" } }
        assertEquals(TxType.INCOME, saved.type)
        assertEquals(100_000L, saved.amountCents)
        assertEquals("acc-cash", saved.accountId)
        assertEquals(null, saved.categoryId)
        assertTrue("借入不是收入：默认不计入收支", saved.excludeFromStats)
        assertTrue("也不进预算（两个开关是两回事，模拟机走查抓过一次）", saved.excludeFromBudget)

        // 账户余额 +1000（钱确实进了账户）
        val balance = awaitDb { app.database.accountDao().observeWithBalance(bookId).first().firstOrNull()?.balanceCents }
        assertEquals(100_000L, balance)

        // 统计两数不变（excludeFromStats 回归：借入后收支仍是 0/0）
        val rows = awaitDb { app.database.transactionDao().observeRangeTxsWithCategory(bookId, 0L, Long.MAX_VALUE).first() }
        val totals = StatsMath.totals(rows)
        assertEquals("借入不进收入", 0L, totals.incomeCents)
        assertEquals("借入不进支出", 0L, totals.expenseCents)

        // 债务：剩余 = 本金 − 已归还 = 1000 − 0
        val debt = awaitDb { app.database.debtDao().observeByBook(bookId).first().firstOrNull() }
        assertEquals(0L, debt.repaidCents)
        assertEquals(100_000L, debt.remainingCents)

        // 首页行标题写「借入 · 张三」而不是"未分类"
        awaitUi { count("借入 · 张三") == 1 }
        composeRule.onNodeWithText("借入 · 张三").assertIsDisplayed()
    }

    @Test
    fun `债务详情的记一笔还款：预填剩余额并冲减`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        seedBorrowFlow(app)
        renderApp(app)

        // 资产 Tab → 债务卡 → 债务列表 → 张三 → 债务详情
        composeRule.onAllNodesWithText("资产").onFirst().performClick()
        composeRule.waitUntil(5_000) { count("净资产(元)") == 1 }
        composeRule.onNodeWithText("债务").performClick()
        composeRule.waitUntil(5_000) { count("应付 · 我欠对方") == 1 }
        composeRule.onNodeWithText("张三").performClick()
        awaitUi { count("债务详情") == 1 && count("记一笔还款") == 1 }
        // 详情：剩余 1,000.00（本金 1000，还没还过）
        composeRule.onNodeWithText("本金 1,000.00 · 已还 0.00").assertIsDisplayed()

        // 记一笔还款：预填这笔债务 + 清偿那一格 + 剩余额 1000 + 上次用过的账户
        composeRule.onNodeWithText("记一笔还款").performClick()
        awaitUi { count("添加备注") == 1 }
        awaitUi { count("张三") == 1 }
        composeRule.onNodeWithText("1000.00").assertIsDisplayed()
        composeRule.onNodeWithText("现金").assertIsDisplayed()
        press("完成")
        awaitUi { count("债务详情") == 1 }

        // 落库：支出型流水（钱出账户）+ debtId + 不计收支
        val repay = awaitDb { flows(app).firstOrNull { it.type == TxType.EXPENSE } }
        assertEquals(100_000L, repay.amountCents)
        assertEquals("debt-1", repay.debtId)
        assertEquals("acc-cash", repay.accountId)
        assertTrue("还款不是支出：默认不计入收支", repay.excludeFromStats)
        assertTrue("也不进预算", repay.excludeFromBudget)

        // 预算口径回归：还款（支出型）不进预算已用（模拟机走查抓到过只关收支的版本）
        val budgetSpend = awaitDb {
            app.database.transactionDao().observeMonthSpendByCategory(bookId, 0L, Long.MAX_VALUE)
                .first().sumOf { it.cents }
        }
        assertEquals("还款不进预算", 0L, budgetSpend)

        // 债务：已还 1000、剩余 0；账户回到 0（借入 +1000、还款 −1000）
        val debt = awaitDb { app.database.debtDao().observeByBook(bookId).first().firstOrNull() }
        assertEquals(100_000L, debt.repaidCents)
        assertEquals(0L, debt.remainingCents)
        val balance = awaitDb { app.database.accountDao().observeWithBalance(bookId).first().firstOrNull()?.balanceCents }
        assertEquals(0L, balance)

        // 详情页还款记录里两笔都在（借入 + 还款）
        awaitUi { count("还款") >= 1 }
        composeRule.onAllNodesWithText("借入").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("还款").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("本金 1,000.00 · 已还 1,000.00").assertIsDisplayed()
    }
}
