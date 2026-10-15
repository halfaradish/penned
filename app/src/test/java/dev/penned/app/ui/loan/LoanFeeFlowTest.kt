package dev.penned.app.ui.loan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.Money
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * M3-11 两条链路（真库 + 真导航图）：
 * ① 我的 → 分期房贷 → 「按首期记一笔」→ 记一笔页金额/日期/备注已预填（只预填这一笔，不生成计划）；
 * ② 资产页信用卡行：年费副标题 + 到期「记年费」→ 点它落一条支出流水 + 标记今年已记、提示随之消失。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoanFeeFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-loan-fee"

    private fun seed(app: PennedApplication, withFeeCard: Boolean = false) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-fee", bookId = bookId, name = "年费手续费", type = CategoryType.EXPENSE,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        if (withFeeCard) {
            app.database.accountDao().upsert(
                AccountEntity(
                    id = "acc-card", bookId = bookId, name = "招行信用卡", type = AccountType.CREDIT,
                    // 1 月 1 日收费：一年里任何一天都算到期（跨年那半天不用挑日期）
                    annualFeeCents = 48_000L, feeMonth = 1, feeDay = 1,
                    createdAt = now, updatedAt = now, deviceId = "test",
                )
            )
        }
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
    fun `按首期记一笔：金额与日期预填进记一笔页`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)

        // 我的 → 分期房贷（工具段在长页靠下，先滚到）
        composeRule.onNodeWithText("我的").performClick()
        awaitUi { count("分期房贷") == 1 }
        composeRule.onNodeWithText("分期房贷").performScrollTo().performClick()
        // 首期还款日那行是可点行（合并语义节点，文本是「首期还款日, 日期」）→ 等一个独立的标题
        awaitUi { count("还款计划") == 1 }
        composeRule.onNodeWithText("按首期记一笔").performScrollTo().performClick()

        // 落到记一笔页：备注已预填（有备注时占位「添加备注」不出现，所以等备注本身）、金额是首期月供
        awaitUi { count("房贷首期 · 等额本息") == 1 }
        assertEquals(1, count(Money.format(530_727L, grouped = false)))
        composeRule.onNodeWithText("房贷首期 · 等额本息").assertIsDisplayed()
        // 没有落库：只预填这一笔，不自动生成整个计划（流水仍 0 条）
        assertEquals(0, runBlocking { app.database.transactionDao().countInBook(bookId) })
    }

    @Test
    fun `信用卡年费：到期提示点按 = 记一笔支出 + 标记今年已记`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app, withFeeCard = true)
        renderApp(app)

        composeRule.onNodeWithText("资产").performClick()
        awaitUi { count("净资产(元)") == 1 }
        awaitUi { count("招行信用卡") == 1 }
        // 副标题：年费与收费日（口径在 FeeMath.label）
        composeRule.onNodeWithText("年费 ¥480.00 · 每年 1 月 1 日").assertIsDisplayed()
        // 到期（今年没标记过）→ 行尾「记年费」
        composeRule.onNodeWithText("记年费").assertIsDisplayed()
        composeRule.onNodeWithText("记年费").performClick()

        // 流水落库：支出 480.00、落在信用卡、分类挑到名字含「年费」的那个
        awaitUi { runBlocking { app.database.transactionDao().countByAccount("acc-card") == 1 } }
        val tx = runBlocking { app.database.transactionDao().allInBook(bookId) }.first()
        assertEquals(48_000L, tx.amountCents)
        assertEquals("cat-fee", tx.categoryId)
        assertEquals("信用卡年费 · 招行信用卡", tx.note)
        // 标记今年已记 → 提示消失、副标题还在
        val year = LocalDate.now().year
        awaitUi { runBlocking { app.database.accountDao().get("acc-card")!!.lastFeePaidYear == year } }
        awaitUi { count("记年费") == 0 }
        composeRule.onNodeWithText("年费 ¥480.00 · 每年 1 月 1 日").assertIsDisplayed()
        // 提示条说清楚发生了什么
        awaitUi { count("已记年费 480.00，今年的提示不再出现") == 1 }
    }
}
