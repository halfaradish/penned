package dev.penned.app.ui.templates

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
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.data.db.entity.TxTemplateEntity
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
 * 模板链路（M3-5）：真库 + 真导航图。
 * ① 我的 → 模板记账 → 新建模板（名称/金额/分类/账户）→ 保存 → 列表出现；
 * ② 记一笔「模板」chip → 选模板 → 字段预填（金额/分类/账户/备注）→ 保存 → 落库的流水照模板；
 * ③ 新建一笔手工流水 → 首页提示条「存为模板」→ 模板新建页预填（金额取落库净额）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TemplatesFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-tpl-flow"

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-food", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE,
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

    @Test
    fun `新建模板后记一笔用模板：字段预填并照模板落库`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)

        // 我的 → 模板记账 → 新建模板
        composeRule.onNodeWithText("我的").performClick()
        composeRule.waitUntil(5_000) { count("模板记账") == 1 }
        composeRule.onNodeWithText("模板记账").performClick()
        composeRule.waitUntil(5_000) { count("还没有模板") == 1 }
        composeRule.onNodeWithText("新建模板").performClick()
        composeRule.waitUntil(5_000) { count("比如「午饭」") == 1 }

        // 名称 + 金额 + 分类 + 账户（本页有三个可输入行：名称/金额/备注 → 按顺序取）
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("午饭")
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("16.50")
        composeRule.onNodeWithText("选择分类").performClick()
        composeRule.waitUntil(5_000) { count("食品餐饮") == 1 }
        composeRule.onNodeWithText("食品餐饮").performClick()
        composeRule.onNodeWithText("无账户").performClick()
        composeRule.waitUntil(5_000) { count("选择账户") == 1 }
        composeRule.onNodeWithText("微信").performClick()
        composeRule.onNodeWithText("保存").performClick()

        // 列表出现（摘要写全）
        composeRule.waitUntil(5_000) { count("支出 · 16.50 · 食品餐饮 · 微信") == 1 }
        val saved = awaitDb { app.database.templateDao().observeByBook(bookId).first().firstOrNull() }
        assertEquals("午饭", saved.name)
        assertEquals(1650L, saved.amountCents)
        assertEquals("cat-food", saved.categoryId)
        assertEquals("acc-wechat", saved.accountId)

        // 回首页 → 记一笔 → 模板 chip → 选模板 → 预填
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
        composeRule.onNodeWithText("添加一条新记账").performClick()
        composeRule.waitUntil(5_000) { count("添加备注") == 1 }
        composeRule.onNodeWithText("模板").performClick()
        composeRule.waitUntil(5_000) { count("选择模板") == 1 }
        composeRule.onNodeWithText("午饭").performClick()
        // 抽屉收起动画走完再点键盘（否则那一下会落在正在消失的遮罩上——M3-3 的抽屉同款坑）
        awaitUi { count("管理模板") == 0 }
        // 预填：金额 16.50（信息条）、分类选中、账户 chip 是微信
        awaitUi { count("16.50") >= 1 }
        composeRule.onNodeWithText("微信").assertIsDisplayed()
        // 抽屉收起后的触摸注入在这台"设备"上会被窗口路由吃掉（点按坐标落在已 dismiss 的浮层窗口上），
        // 这里直接走语义动作触发 OnClick —— 与点击等价、不依赖坐标（M3-5 的坑）
        composeRule.onNodeWithTag("amount-key-完成").performClick()
        // 保存后回到首页：提示条「已记账」+「存为模板」（10s，入口会被提示条挡住，所以等提示条而不是入口）
        composeRule.waitUntil(5_000) { count("已记账") == 1 }

        // 落库的流水照模板（金额/分类/账户）
        val tx = awaitDb { app.database.transactionDao().allInBook(bookId).firstOrNull() }
        assertEquals(1650L, tx.amountCents)
        assertEquals("cat-food", tx.categoryId)
        assertEquals("acc-wechat", tx.accountId)
        assertEquals(TxType.EXPENSE, tx.type)
    }

    @Test
    fun `手工记一笔后首页提示条的存为模板：带出草稿`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)

        // 手工记一笔 支出 20（分类 + 账户）
        composeRule.onNodeWithText("添加一条新记账").performClick()
        composeRule.waitUntil(5_000) { count("添加备注") == 1 }
        // 宫格数据是异步来的（WhileSubscribed 的 stateIn），等分类出来再点
        composeRule.waitUntil(5_000) { count("食品餐饮") >= 1 }
        composeRule.onNodeWithText("食品餐饮").performClick()
        composeRule.onNodeWithText("无账户").performClick()
        composeRule.waitUntil(5_000) { count("选择账户") == 1 }
        composeRule.onNodeWithText("微信").performClick()
        press("2"); press("0")
        press("完成")

        // 首页提示条带「存为模板」动作
        composeRule.waitUntil(5_000) { count("存为模板") == 1 }
        composeRule.onNodeWithText("存为模板").performClick()
        awaitUi { count("新建模板") == 1 }
        // 预填：金额 20.00、分类、账户都在；名称留空等用户填
        awaitUi { count("20.00") >= 1 }
        composeRule.onNodeWithText("食品餐饮").assertIsDisplayed()
        composeRule.onNodeWithText("微信").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsNotEnabled()

        // 补个名字即可保存（名称是第一个可输入行）
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("晚饭")
        composeRule.onNodeWithText("保存").performClick()
        val saved = awaitDb { app.database.templateDao().observeByBook(bookId).first().firstOrNull() }
        assertEquals("晚饭", saved.name)
        assertEquals(2000L, saved.amountCents)
        assertEquals("cat-food", saved.categoryId)
    }

    /** 供「存为模板」那条链路复用：先塞一笔流水（等价于手工记一笔，省掉键盘步骤）。 */
    private fun seedTx(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-seed", bookId = bookId, type = TxType.EXPENSE, amountCents = 2000,
                accountId = "acc-wechat", categoryId = "cat-food", dateEpochMinute = 30_000_000L,
                note = null, creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
    }

    @Suppress("unused")
    private fun seedTemplate(app: PennedApplication, name: String = "午饭") = runBlocking {
        val now = System.currentTimeMillis()
        app.database.templateDao().upsert(
            TxTemplateEntity(
                id = "tpl-1", bookId = bookId, name = name, type = TxType.EXPENSE, amountCents = 1650,
                categoryId = "cat-food", accountId = "acc-wechat", createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
    }
}
