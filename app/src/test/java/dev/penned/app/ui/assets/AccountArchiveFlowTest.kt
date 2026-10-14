package dev.penned.app.ui.assets

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
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

/**
 * 资产模块三件（M3-9）：真库 + 真导航图。
 * ① 停用账户 → 资产页收进「已停用」段（默认折叠）、记账选择器不列它（但编辑用它的流水时仍在）；
 * ② 流水迁移 → 原账户流水数归零、余额归零 → 可删（删完从列表消失）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountArchiveFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-acc-flow"

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.accountDao().upsert(
            AccountEntity(
                id = "acc-old", bookId = bookId, name = "旧卡", type = AccountType.DEBIT,
                initialBalanceCents = 10_000, createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.accountDao().upsert(
            AccountEntity(
                id = "acc-new", bookId = bookId, name = "新卡", type = AccountType.DEBIT,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-1", bookId = bookId, type = TxType.EXPENSE, amountCents = 2_000,
                accountId = "acc-old", categoryId = "cat", dateEpochMinute = 30_000_000L,
                creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
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
    fun `停用账户：资产页进已停用段，记账选择器不列它`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        // 直接停用（编辑页的开关在下面那条测试里走一遍）
        runBlocking { app.database.accountDao().setArchived("acc-old", true, 1L) }
        renderApp(app)

        // 资产页：已停用段（折叠态标题在、行不在）——展开后能看到
        composeRule.onNodeWithText("资产").performClick()
        composeRule.waitUntil(5_000) { count("净资产(元)") == 1 }
        awaitUi { count("已停用") == 1 }
        composeRule.onNodeWithText("旧卡").assertDoesNotExist()
        composeRule.onNodeWithText("已停用").performClick()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("旧卡").assertIsDisplayed()

        // 记账选择器：只有新卡，没有旧卡
        composeRule.onNodeWithText("首页").performClick()
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
        composeRule.onNodeWithText("添加一条新记账").performClick()
        composeRule.waitUntil(5_000) { count("添加备注") == 1 }
        composeRule.onNodeWithText("无账户").performClick()
        composeRule.waitUntil(5_000) { count("选择账户") == 1 }
        composeRule.onNodeWithText("新卡").assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("旧卡").assertDoesNotExist()
        composeRule.onNodeWithText("新卡").performClick()
        awaitUi { count("选择账户") == 0 }
        // 选中后 chip 回显新卡（旧卡不进选择器这一条已经验完）
        composeRule.onNodeWithText("新卡").assertIsDisplayed()
    }

    @Test
    fun `迁移流水：原账户流水数归零、余额并到新账户、随后可删`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)

        composeRule.onNodeWithText("资产").performClick()
        composeRule.waitUntil(5_000) { count("净资产(元)") == 1 }
        awaitUi { count("旧卡") == 1 }

        // ① 编辑页：停用开关（只有 Switch 本身可点，按 Toggleable 语义定位）+ 保存
        composeRule.onNodeWithText("旧卡").performClick()
        awaitUi { count("编辑账户") == 1 }
        composeRule.onRoot().performTouchInput { swipeUp() }   // 长表单：滑到底
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        composeRule.onNodeWithText("保存").performClick()
        awaitUi { runBlocking { app.database.accountDao().get("acc-old")!!.archived } }
        // 保存后回到资产页：旧卡已收进「已停用」段（默认折叠 → 展开才看得到）
        awaitUi { count("已停用") == 1 }
        composeRule.onNodeWithText("旧卡").assertDoesNotExist()
        composeRule.onNodeWithText("已停用").performClick()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        awaitUi { count("旧卡") == 1 }

        // ② 再进编辑页：迁移（名下 1 笔流水 → 新卡）
        composeRule.onNodeWithText("旧卡").performClick()
        awaitUi { count("编辑账户") == 1 }
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("把名下 1 笔流水转到…").performClick()
        composeRule.waitUntil(5_000) { count("转到哪个账户") == 1 }
        composeRule.onNodeWithText("新卡").performClick()
        composeRule.waitUntil(5_000) { count("迁移流水？") == 1 }
        composeRule.onNodeWithText("迁移").performClick()

        awaitUi { runBlocking { app.database.transactionDao().countByAccount("acc-old") == 0 } }
        assertEquals("acc-new", runBlocking { app.database.transactionDao().get("tx-1")!!.accountId })
        // 初始余额一并并过去：旧卡 100 - 20 = 80 → 新卡接住 80，旧卡 0
        awaitUi {
            val rows = runBlocking { app.database.accountDao().observeWithBalance(bookId).first() }
            rows.first { it.id == "acc-old" }.balanceCents == 0L &&
                rows.first { it.id == "acc-new" }.balanceCents == 8_000L
        }

        // ③ 迁完可删：删除键从置灰变可点，删完从列表消失；净资产不变（钱都还在）
        composeRule.onNodeWithText("删除账户").performClick()
        composeRule.waitUntil(5_000) { count("删除账户？") == 1 }
        composeRule.onNodeWithText("删除").performClick()
        awaitUi { count("净资产(元)") == 1 }
        awaitUi { count("旧卡") == 0 }
        assertEquals(true, runBlocking { app.database.accountDao().get("acc-old")!!.deleted })
        composeRule.onAllNodesWithText("80.00").onFirst().assertIsDisplayed()
    }
}
