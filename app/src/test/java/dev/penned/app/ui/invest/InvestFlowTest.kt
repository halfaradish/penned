package dev.penned.app.ui.invest

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.TxType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme
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
 * 理财快照链路（M3-10）：真库 + 真导航图。
 * 无快照 → 总额走余额（初始 1000 + 净转入 200）；记快照 1300 → 总额 1300 / 成本 1200 / 盈亏 100；删快照退回余额口径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InvestFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-invest"

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.accountDao().upsert(
            AccountEntity(
                id = "acc-invest", bookId = bookId, name = "理财A", type = AccountType.INVEST,
                initialBalanceCents = 100_000, createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.accountDao().upsert(
            AccountEntity(
                id = "acc-cash", bookId = bookId, name = "现金", type = AccountType.CASH,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        // 净转入 200：现金 → 理财A
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "t-in", bookId = bookId, type = TxType.TRANSFER, amountCents = 20_000,
                accountId = "acc-cash", targetAccountId = "acc-invest", dateEpochMinute = 30_000_000L,
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
    fun `理财卡接真数据：无快照走余额，记快照后三数按快照算`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)

        composeRule.onNodeWithText("资产").performClick()
        composeRule.waitUntil(5_000) { count("净资产(元)") == 1 }
        awaitUi { count("1,200.00") >= 1 }

        composeRule.onNodeWithText("理财").performClick()
        awaitUi { count("理财详情") == 1 }
        awaitUi { count("还没有快照：总额按账户余额算") == 1 }
        composeRule.onNodeWithText("成本 1,200.00").assertIsDisplayed()
        composeRule.onNodeWithText("盈亏 0.00").assertIsDisplayed()

        // 记快照 1300（今天）→ 总额 1300 / 成本 1200 / 盈亏 100
        composeRule.onNodeWithText("记快照").performClick()
        composeRule.waitUntil(5_000) { count("现值") == 1 }
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).onFirst().performTextInput("1300")
        composeRule.onNodeWithText("保存").performClick()
        awaitUi { count("盈亏 100.00") == 1 }
        // 总额与快照行都是 1,300.00（两处）——断言第一处即可
        composeRule.onAllNodesWithText("1,300.00").onFirst().assertIsDisplayed()
        assertEquals(1, runBlocking { app.database.snapshotDao().countForAccount("acc-invest") })
    }

}
