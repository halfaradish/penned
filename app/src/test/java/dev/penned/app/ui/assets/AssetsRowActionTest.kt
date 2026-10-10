package dev.penned.app.ui.assets

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import org.junit.Assert.assertNull
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
 * 资产页账户行左滑动作区（UI-13 / D054）走真库端到端，与首页同一套口径：
 * 左滑只露出「删除」（账户行点一下就进编辑页，不再重复给「编辑」）→ 点按钮才动作。
 * 名下有流水的账户点删除 → 被拦下、只提示条数（原来的弹窗防护改成"必须滑开再点按钮"）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssetsRowActionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 账户行只有一个 64dp 动作块（`PennedDimens.rowActionWidth`）。 */
    private val revealedWidth = 64f

    private fun seed(app: PennedApplication, withEmpty: Boolean = true, withUsed: Boolean = true) =
        runBlocking {
            val now = System.currentTimeMillis()
            val bookId = "book-assets-test"
            app.database.bookDao().upsert(
                BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test"),
            )
            if (withEmpty) {
                // 误建的账户：没有流水，可以删
                app.database.accountDao().upsert(
                    AccountEntity(
                        id = "acc-empty", bookId = bookId, name = "误建账户", type = AccountType.CASH,
                        initialBalanceCents = 10_000, createdAt = now, updatedAt = now, deviceId = "test",
                    ),
                )
            }
            if (withUsed) {
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
            app.settings.setCurrentBook(bookId)
        }

    /**
     * 每个账户行都带自己的「删除」块，所以断言按钮的用例一律只种一个账户，节点才唯一。
     */
    private fun launch(app: PennedApplication, onOpenAccount: (String) -> Unit = {}) {
        composeRule.setContent {
            PennedTheme {
                CompositionLocalProvider(LocalApp provides app) {
                    AssetsScreen(onAddAccount = { _, _ -> }, onOpenAccount = onOpenAccount)
                }
            }
        }
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun leftOf(text: String): Float =
        composeRule.onNodeWithText(text).getUnclippedBoundsInRoot().left.value

    private fun isDeleted(app: PennedApplication, id: String): Boolean =
        runBlocking { app.database.accountDao().get(id)!!.deleted }

    @Test
    fun tappingAccountRowOpensItForEditing() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        var opened: String? = null
        launch(app, onOpenAccount = { opened = it })
        composeRule.waitUntil(5_000) { count("在用账户") == 1 }

        composeRule.onNodeWithText("在用账户").performClick()
        assertEquals("acc-used", opened)
    }

    @Test
    fun `左滑只露出删除块_不弹确认也不删`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app, withUsed = false)
        launch(app)
        composeRule.waitUntil(5_000) { count("误建账户") == 1 }
        val closedLeft = leftOf("误建账户")

        composeRule.onNodeWithText("误建账户").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals("账户行只该露出一个 64dp 动作块", revealedWidth, closedLeft - leftOf("误建账户"), 2f)
        // 旧行为：滑过阈值弹「删除「误建账户」？」——现在这条路径整条不存在
        assertEquals(0, count("删除「误建账户」？"))
        assertFalse(isDeleted(app, "acc-empty"))
        composeRule.onNodeWithText("删除").assertIsDisplayed()
    }

    @Test
    fun `点删除才软删_撤销把账户放回来`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app, withUsed = false)
        launch(app)
        composeRule.waitUntil(5_000) { count("误建账户") == 1 }
        // 只种了一个账户（余额 = 净资产 = 100.00）：净资产 / 总资产 / 组小计 / 账户行自身 四处都是它
        composeRule.onAllNodesWithText("100.00").assertCountEquals(4)

        composeRule.onNodeWithText("误建账户").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertFalse(isDeleted(app, "acc-empty"))   // 展开 ≠ 删除

        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(5_000) { count("误建账户") == 0 }
        composeRule.onNodeWithText("已删除 1 个账户").assertIsDisplayed()
        assertTrue(isDeleted(app, "acc-empty"))
        // 净资产跟着少掉被删账户的钱
        composeRule.waitUntil(5_000) { count("100.00") == 0 }

        // 撤销 → 账户与净资产都回来（先点撤销，缩短与 Snackbar 超时的竞争窗口）
        composeRule.waitForIdle()
        composeRule.onNodeWithText("撤销").performClick()
        composeRule.waitUntil(5_000) { count("误建账户") == 1 }
        composeRule.waitForIdle()
        assertFalse(isDeleted(app, "acc-empty"))
        composeRule.waitUntil(5_000) { count("100.00") == 4 }
    }

    @Test
    fun `有流水的账户点删除被拦下_只提示不删`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app, withEmpty = false)
        launch(app)
        composeRule.waitUntil(5_000) { count("在用账户") == 1 }
        val closedLeft = leftOf("在用账户")

        composeRule.onNodeWithText("在用账户").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(5_000) { count("「在用账户」还有 1 笔流水，先处理流水再删除") == 1 }
        composeRule.waitForIdle()

        assertFalse(isDeleted(app, "acc-used"))
        // 行还在列表里，动作区收起（拦下的说明在底部提示条里）
        assertEquals(1, count("在用账户"))
        assertEquals(closedLeft, leftOf("在用账户"), 0.5f)
    }

    @Test
    fun `展开态点行内容只收起_不进编辑页`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app, withUsed = false)
        var opened: String? = null
        launch(app, onOpenAccount = { opened = it })
        composeRule.waitUntil(5_000) { count("误建账户") == 1 }
        val closedLeft = leftOf("误建账户")

        composeRule.onNodeWithText("误建账户").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("误建账户").performClick()
        composeRule.waitForIdle()
        assertNull("展开态点行内容不该进编辑页", opened)
        assertEquals(closedLeft, leftOf("误建账户"), 0.5f)

        // 收起态再点：照旧进编辑页
        composeRule.onNodeWithText("误建账户").performClick()
        composeRule.waitForIdle()
        assertEquals("acc-empty", opened)
    }
}
