package dev.penned.app.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.TxType
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
 * 左滑删除（D024 交互范式二 + D031 二次确认）走真库端到端：
 * 左滑 → 弹确认 → 取消不动 / 确认才软删（行消失）→ Snackbar「撤销」→ 反软删把行放回来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeSwipeDeleteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        val bookId = "book-swipe-test"
        app.database.bookDao().upsert(
            BookEntity(
                id = bookId,
                name = "测试账本",
                sortOrder = 0,
                createdAt = now,
                updatedAt = now,
                deviceId = "test",
            ),
        )
        app.settings.setCurrentBook(bookId)
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-swipe-a",
                bookId = bookId,
                type = TxType.EXPENSE,
                amountCents = 1234,
                dateEpochMinute = LocalDate.now(zone).atTime(12, 0).atZone(zone).toEpochSecond() / 60,
                note = "左滑测试流水",
                creatorDeviceId = "test",
                createdAt = now,
                updatedAt = now,
                deviceId = "test",
            ),
        )
    }

    @Test
    fun swipeDeletesAndUndoBringsItBack() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        val vm = HomeViewModel(app)
        composeRule.setContent {
            PennedTheme {
                HomeScreen(
                    viewModel = vm,
                    onAdd = {},
                    onOpenDetail = {},
                    onManageBooks = {},
                )
            }
        }

        composeRule.waitUntil(5_000) { count("左滑测试流水") == 1 }

        // 左滑 → 先弹二次确认，不直接删
        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("删除这条流水？").assertIsDisplayed()
        // 弹窗正文 = 「删的是哪一条」+ 撤销提示（一条多行文本，按子串断言）
        composeRule.onNodeWithText("删除后可在底部提示条里撤销", substring = true).assertIsDisplayed()
        // 取消 → 什么也没发生
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()
        assertEquals(1, count("左滑测试流水"))
        assertFalse(runBlocking { app.database.transactionDao().get("tx-swipe-a")!!.deleted })

        // 再滑一次 → 确认 → 软删
        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(5_000) { count("左滑测试流水") == 0 }
        composeRule.onNodeWithText("已删除 1 条").assertIsDisplayed()
        assertTrue(runBlocking { app.database.transactionDao().get("tx-swipe-a")!!.deleted })

        // 撤销 → 行回到列表（先点撤销，缩短与 Snackbar 超时的竞争窗口）
        composeRule.onNodeWithText("撤销").performClick()
        composeRule.waitUntil(5_000) { count("左滑测试流水") == 1 }
        // 软删而非物理删：撤销后 deleted 归 0，行还在库里，且不会被"复活的滑动状态"再删一次
        composeRule.waitForIdle()
        composeRule.waitForIdle()
        assertEquals(
            1,
            runBlocking { app.database.transactionDao().get("tx-swipe-a")!!.let { if (it.deleted) 0 else 1 } },
        )
        assertEquals(0, vm.deletedIds.value.size)
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size
}
