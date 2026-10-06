package dev.penned.app.ui.assets

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 账户删除走真库端到端：
 * 空账户左滑 → 软删落库（净资产同步减少）→ Snackbar「撤销」→ 反软删放回来；
 * 名下有流水的账户左滑 → 不删，只提示条数（净资产与流水都不会对不上）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssetsDeleteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        val bookId = "book-assets-test"
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test"),
        )
        app.settings.setCurrentBook(bookId)
        // 误建的账户：没有流水，可以删
        app.database.accountDao().upsert(
            AccountEntity(
                id = "acc-empty", bookId = bookId, name = "误建账户", type = AccountType.CASH,
                initialBalanceCents = 10_000, createdAt = now, updatedAt = now, deviceId = "test",
            ),
        )
        // 在用账户：名下有 1 笔流水，不给删
        app.database.accountDao().upsert(
            AccountEntity(
                id = "acc-used", bookId = bookId, name = "在用账户", type = AccountType.DEBIT,
                initialBalanceCents = 5_000, createdAt = now, updatedAt = now, deviceId = "test",
            ),
        )
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-acc", bookId = bookId, type = TxType.EXPENSE, amountCents = 1_200,
                accountId = "acc-used",
                dateEpochMinute = LocalDate.now(zone).atTime(12, 0).atZone(zone).toEpochSecond() / 60,
                note = "用账户的流水", creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
            ),
        )
    }

    private fun launch(app: PennedApplication, onOpenAccount: (String) -> Unit = {}) {
        composeRule.setContent {
            PennedTheme {
                CompositionLocalProvider(LocalApp provides app) {
                    AssetsScreen(onAddAccount = {}, onOpenAccount = onOpenAccount)
                }
            }
        }
        composeRule.waitUntil(5_000) { count("误建账户") == 1 }
        composeRule.waitUntil(5_000) { count("在用账户") == 1 }
    }

    @Test
    fun tappingAccountRowOpensItForEditing() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        var opened: String? = null
        launch(app, onOpenAccount = { opened = it })

        composeRule.onNodeWithText("在用账户").performClick()
        assertEquals("acc-used", opened)
    }

    @Test
    fun swipeDeletesEmptyAccountAndUndoBringsItBack() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        launch(app)

        // 初始净资产 = 100.00（误建）+ 38.00（在用 50.00 - 12.00 支出）；两个账户都是正数，
        // 所以净资产、总资产、资金账户组小计三处都是 138.00
        composeRule.onAllNodesWithText("138.00").assertCountEquals(3)

        composeRule.onNodeWithText("误建账户").performTouchInput { swipeLeft() }
        composeRule.waitUntil(5_000) { count("误建账户") == 0 }
        composeRule.onNodeWithText("已删除 1 个账户").assertIsDisplayed()
        // 净资产跟着少掉被删账户的钱；库里是软删
        composeRule.waitUntil(5_000) { count("138.00") == 0 }
        assertTrue(runBlocking { app.database.accountDao().get("acc-empty")!!.deleted })

        // 撤销 → 账户与净资产都回来（先点撤销，缩短与 Snackbar 超时的竞争窗口）
        composeRule.onNodeWithText("撤销").performClick()
        composeRule.waitUntil(5_000) { count("误建账户") == 1 }
        composeRule.waitForIdle()
        composeRule.waitForIdle()
        assertFalse(runBlocking { app.database.accountDao().get("acc-empty")!!.deleted })
        composeRule.waitUntil(5_000) { count("138.00") == 3 }
    }

    @Test
    fun swipeOnAccountWithTransactionsIsBlocked() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        launch(app)

        composeRule.onNodeWithText("在用账户").performTouchInput { swipeLeft() }
        composeRule.waitUntil(5_000) { count("「在用账户」还有 1 笔流水，先处理流水再删除") == 1 }
        composeRule.waitForIdle()
        assertFalse(runBlocking { app.database.accountDao().get("acc-used")!!.deleted })
        // 行滑出后归位，仍在列表里
        composeRule.waitUntil(5_000) { count("在用账户") == 1 }
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size
}
