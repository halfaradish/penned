package dev.penned.app.ui.recurring

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.RecurringGenerator
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.RecurringRuleEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.RecurringPeriod
import dev.penned.app.domain.TxType
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
import java.time.LocalDate

/**
 * 周期记账链路（M3-6）：真库 + 真导航图。
 * ① 启动即补（D070）：规则到期 → 渲染 App 时自动补记 → 首页给「已补记 N 笔」提示；再补一次为 0（幂等）；
 * ② 我的 → 周期记账 → 新建规则（每天/金额/分类/账户）→ 保存 → 列表行 + 开关；停用后不再补。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecurringFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-rec-flow"

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-home", bookId = bookId, name = "居家生活", type = CategoryType.EXPENSE,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.accountDao().upsert(
            AccountEntity(
                id = "acc-card", bookId = bookId, name = "储蓄卡", type = AccountType.DEBIT,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.settings.setCurrentBook(bookId)
    }

    private fun seedRule(app: PennedApplication, startEpochDay: Long, enabled: Boolean = true) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.recurringDao().upsert(
            RecurringRuleEntity(
                id = "rule-1", bookId = bookId, name = "房租", type = TxType.EXPENSE, amountCents = 200_000,
                categoryId = "cat-home", accountId = "acc-card", periodType = RecurringPeriod.DAILY, interval = 1,
                startEpochDay = startEpochDay, nextEpochDay = startEpochDay, enabled = enabled,
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
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun awaitUi(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (predicate()) return
            Thread.sleep(20)
        }
        error("等界面超时")
    }

    @Test
    fun `启动即补：到期的规则渲染 App 时补出来并给提示，再补为 0`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        val today = LocalDate.now().toEpochDay()
        seedRule(app, startEpochDay = today - 2) // 前天起：前天/昨天/今天 共 3 期

        renderApp(app)
        // 首页提示条：补了几笔说清楚（可关闭的短提示）
        awaitUi { count("周期记账已补记 3 笔") == 1 }
        val txs = runBlocking { app.database.transactionDao().allInBook(bookId) }
        assertEquals(3, txs.size)
        assertEquals(listOf("rule-1"), txs.map { it.recurringRuleId }.distinct())
        // 期次是 9:00 本地、按天
        assertEquals(
            (0..2).map { today - it }.sorted(),
            txs.map { dev.penned.app.domain.AddDate.dayFromMinute(it.dateEpochMinute) }.sorted(),
        )

        // 再启动（等价于再跑一次启动补记）：幂等，不再补
        val again = runBlocking { RecurringGenerator(app.database, app.deviceId).catchUp() }
        assertEquals(0, again)
        assertEquals(3, runBlocking { app.database.transactionDao().allInBook(bookId).size })
        // 指针推到明天
        assertEquals(today + 1, runBlocking { app.database.recurringDao().get("rule-1")!!.nextEpochDay })
    }

    @Test
    fun `停用的规则启动不补`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        seedRule(app, startEpochDay = LocalDate.now().toEpochDay() - 3, enabled = false)
        renderApp(app)
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
        assertEquals(0, runBlocking { app.database.transactionDao().allInBook(bookId).size })
        assertEquals(0, count("周期记账已补记 3 笔"))
    }

    @Test
    fun `新建规则：列表行与开关都在`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }

        composeRule.onNodeWithText("我的").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { count("周期记账") == 1 }
        composeRule.onNodeWithText("周期记账").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { count("还没有周期记账") == 1 }
        composeRule.onNodeWithText("新建规则").performClick()
        composeRule.waitUntil(5_000) { count("比如「房租」") == 1 }

        // 名称 + 金额（本页可输入行：名称/金额/每隔/备注 → 名称是第 0 个、金额第 1 个）
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("房租")
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("2000")
        composeRule.onNodeWithText("选择分类").performClick()
        composeRule.waitUntil(5_000) { count("居家生活") == 1 }
        composeRule.onNodeWithText("居家生活").performClick()
        composeRule.onNodeWithText("无账户").performClick()
        composeRule.waitUntil(5_000) { count("选择账户") == 1 }
        composeRule.onNodeWithText("储蓄卡").performClick()
        // 周期默认「月」，改成「天」（本次只验列表与开关，周期推进在纯函数测试里钉）
        composeRule.onNodeWithText("天").performClick()
        composeRule.onNodeWithText("保存").performClick()

        // 列表行：摘要 + 周期/下次 + 开关
        composeRule.waitUntil(5_000) { count("房租") == 1 }
        // 转场动画期间行可能还没落位：按子串轮询到它出现，再断言存在（displayed 会在动画帧上抖）
        awaitUi { composeRule.onAllNodesWithText("每天 · 下次", substring = true).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("每天 · 下次", substring = true).assertExists()
        val saved = runBlocking { app.database.recurringDao().observeByBook(bookId).first().first() }
        assertEquals("房租", saved.name)
        assertEquals(RecurringPeriod.DAILY, saved.periodType)
        assertEquals(200_000L, saved.amountCents)
        assertEquals("cat-home", saved.categoryId)
        assertEquals("acc-card", saved.accountId)
        // 起始日 = 今天 → 没有到期期次，列表出现后首页也不会冒出补记（今天这一期在下次启动补）
        assertEquals(0, runBlocking { app.database.transactionDao().allInBook(bookId).size })
    }

    @Test
    fun `删除规则：提示条写明流水保留`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        val today = LocalDate.now().toEpochDay()
        seedRule(app, startEpochDay = today)
        renderApp(app)
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }

        composeRule.onNodeWithText("我的").performClick()
        composeRule.waitUntil(5_000) { count("周期记账") == 1 }
        composeRule.onNodeWithText("周期记账").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { count("房租") == 1 }
        // 进编辑页删（比左滑手势稳）
        composeRule.onNodeWithText("房租").performClick()
        composeRule.waitUntil(5_000) { count("编辑规则") == 1 }
        composeRule.onNodeWithText("删除规则").performClick()
        composeRule.waitUntil(5_000) { count("删除规则？") == 1 }
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(5_000) { count("还没有周期记账") == 1 }
        assertEquals(0, runBlocking { app.database.recurringDao().observeByBook(bookId).first().size })
    }

    @Test
    fun `保存置灰：名称金额分类缺一不可`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
        composeRule.onNodeWithText("我的").performClick()
        composeRule.waitUntil(5_000) { count("周期记账") == 1 }
        composeRule.onNodeWithText("周期记账").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { count("还没有周期记账") == 1 }
        composeRule.onNodeWithText("新建规则").performClick()
        composeRule.waitUntil(5_000) { count("比如「房租」") == 1 }
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
    }
}
