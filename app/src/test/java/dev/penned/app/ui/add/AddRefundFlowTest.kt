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
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.domain.CategoryType
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

/**
 * 退款链路（M3-2，D069 保存净额）：真库 + 真导航图——
 * 记一笔填退款（键盘指到退款格）→ 落库金额是净额 → 首页行按净额显示并带「退款」标记
 * → 详情页退款行可点进编辑 → 金额框反推回原价 → 再存一次金额与退款都不变（往返幂等）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddRefundFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-refund-flow"

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

    /** 按键盘键（tag 定位，避开与宫格重名的文本）。 */
    private fun press(key: String) = composeRule.onNodeWithTag("amount-key-$key").performClick()

    private fun awaitDb(timeoutMs: Long = 5_000, block: suspend () -> LongArray?): LongArray {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            runBlocking { block() }?.let { return it }
            Thread.sleep(20)
        }
        error("等数据库结果超时")
    }

    /** 真时间轮询界面条件（Room 的查询在后台线程上，虚拟时钟推不出结果）。 */
    private fun awaitUi(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (predicate()) return
            Thread.sleep(20)
        }
        error("等界面超时")
    }

    /** 库里的 (净额, 退款, 优惠)。 */
    private fun stored(app: PennedApplication): suspend () -> LongArray? = {
        app.database.transactionDao().allInBook(bookId).firstOrNull()
            ?.let { longArrayOf(it.amountCents, it.refundCents, it.discountCents) }
    }

    @Test
    fun `填退款保存成净额_首页带退款标记_详情进编辑反推原价_再存不变`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)

        // 记一笔：备注 + 分类 + 原价 100
        composeRule.onNodeWithText("添加一条新记账").performClick()
        composeRule.waitUntil(5_000) { count("添加备注") == 1 }
        composeRule.waitUntil(5_000) { count("购物消费") >= 1 }
        composeRule.onNode(hasSetTextAction()).performTextInput("退邮费的外套")
        composeRule.onAllNodesWithText("购物消费").onFirst().performClick()
        press("1"); press("0"); press("0")

        // 点「退款」chip：键盘指到退款格，接着打的 30 进退款而不是主金额
        composeRule.onNodeWithText("退款").performClick()
        composeRule.waitUntil(5_000) { count("正在输入：退款") == 1 }
        press("3"); press("0")

        // 信息条就地回显：chip 带值 + 一条实付算式（保存前就能看清会记进去多少钱）
        composeRule.onNodeWithText("退款 30").assertIsDisplayed()
        composeRule.onNodeWithText("实付 70.00（100.00 − 退款 30.00）").assertIsDisplayed()

        // 「回到金额」把键盘还给主金额，再按完成保存
        composeRule.onNodeWithText("回到金额").performClick()
        composeRule.waitUntil(5_000) { count("正在输入：退款") == 0 }
        press("完成")
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }

        // 落库：金额是**净额** 70，退款 30 存原值
        val saved = awaitDb { stored(app)() }
        assertEquals(7000L, saved[0])
        assertEquals(3000L, saved[1])
        assertEquals(0L, saved[2])

        // 首页行：金额按净额、旁边一个「退款」标记（不是原价，也不是说不清的 0）。
        // 月卡的「支出」合计也是 -70.00，所以这里取第一处（同一口径，两处一致更好）
        composeRule.waitUntil(5_000) { count("退邮费的外套") == 1 }
        composeRule.onAllNodesWithText("-70.00").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("退款").assertIsDisplayed()

        // 详情页：退款行可点 → 直接进编辑页（原本只能从底部「编辑」进）
        composeRule.onNodeWithText("退邮费的外套").performClick()
        awaitUi { count("部分退款") == 1 }
        composeRule.onNodeWithText("退款").performScrollTo().performClick()

        // 编辑页：金额框反推回原价 100.00，退款 chip 回填 30.00
        awaitUi { count("退款 30.00") == 1 }
        composeRule.onNodeWithText("100.00").assertIsDisplayed()

        // 再存一次：净额与退款都不变（往返幂等——表驱动那套的端到端版）。
        // 编辑页是从详情页推进来的，保存后回的是详情页（不是首页）
        press("完成")
        awaitUi { count("账单详情") == 1 }
        val again = awaitDb { stored(app)() }
        assertEquals(7000L, again[0])
        assertEquals(3000L, again[1])
        assertEquals(0L, again[2])
    }
}
