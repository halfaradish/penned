package dev.penned.app.ui.filters

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TransactionEntity
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * 账单组合链路（M3-8）：真库 + 真导航图。
 * ① 筛选页设条件 → 右上「存为组合」→ 命名 → 落库；
 * ② 我的 → 账单组合 → 行 = 名称 + 条件摘要 → 点行进筛选页**条件回填、结果即出**（与手工筛选一致）；
 * ③ 重命名（重名给提示）；左滑删除（软删、可撤销）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BillFiltersFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-bf-flow"
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun minute(day: Int) =
        LocalDate.of(2026, 10, day).atTime(12, 0).atZone(zone).toEpochSecond() / 60

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(id = "p1", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(id = "s1", bookId = bookId, parentId = "p1", name = "午餐", type = CategoryType.EXPENSE, sortOrder = 1, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.transactionDao().upsertAll(
            listOf(
                TransactionEntity(
                    id = "e-lunch", bookId = bookId, type = TxType.EXPENSE, amountCents = 1650,
                    categoryId = "s1", dateEpochMinute = minute(10), note = "公司楼下",
                    creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
                ),
                TransactionEntity(
                    id = "e-taxi", bookId = bookId, type = TxType.EXPENSE, amountCents = 3000,
                    categoryId = "p1", dateEpochMinute = minute(12), note = "打车去机场",
                    creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
                ),
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

    private fun openFilterPage() {
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.waitUntil(5_000) { count("筛选") == 1 }
    }

    /** 筛选页设一个条件（类型 = 支出）并存成组合。 */
    private fun saveExpenseFilterAs(name: String) {
        openFilterPage()
        composeRule.onNodeWithText("类型").performClick()
        composeRule.waitUntil(5_000) { count("全部") == 1 }
        composeRule.onNodeWithText("支出").performClick()
        awaitUi { count("2 笔 · 收入 0.00 · 支出 46.50") == 1 }
        composeRule.onNodeWithText("存为组合").performClick()
        composeRule.waitUntil(5_000) { count("存为组合") == 2 }
        // 页面上有两个可输入行（搜索框 + 抽屉里的名称框）：取最后一个（抽屉）
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).onLast()
            .performTextInput(name)
        composeRule.onNodeWithText("保存").performClick()
        awaitUi { count("存为组合") == 1 }
    }

    @Test
    fun `存为组合后从我的进列表：摘要正确，点行回填并出结果`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)

        saveExpenseFilterAs("只看支出")
        val saved = runBlocking { app.database.billFilterDao().observeByBook(bookId).first() }
        assertEquals(1, saved.size)
        assertEquals("只看支出", saved.first().name)
        assertEquals(TxType.EXPENSE, saved.first().type)

        // 回首页 → 我的 → 账单组合
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("我的").performClick()
        composeRule.waitUntil(5_000) { count("账单组合") == 1 }
        composeRule.onNodeWithText("账单组合").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { count("只看支出") == 1 }
        // 摘要：支出（条件回显）
        composeRule.onAllNodesWithText("支出", substring = true).onFirst().assertIsDisplayed()

        // 点行 → 筛选页条件回填 + 结果即出
        composeRule.onNodeWithText("只看支出").performClick()
        composeRule.waitUntil(5_000) { count("筛选") == 1 }
        awaitUi { count("2 笔 · 收入 0.00 · 支出 46.50") == 1 }
        composeRule.onNodeWithText("打车去机场").assertIsDisplayed()
    }

    @Test
    fun `重名给提示：同名组合存不进去`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        saveExpenseFilterAs("只看支出")

        // 再存一个同名的（筛选页还在，条件也还在）
        composeRule.onNodeWithText("存为组合").performClick()
        composeRule.waitUntil(5_000) { count("存为组合") == 2 }
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).onLast()
            .performTextInput("只看支出")
        composeRule.onNodeWithText("保存").performClick()
        awaitUi { count("已经有一个叫「只看支出」的组合了") == 1 }
        assertEquals(1, runBlocking { app.database.billFilterDao().observeByBook(bookId).first() }.size)
    }

    /** VM 层等待：`runBlocking` 会占住主线程、饿死 viewModelScope（Main.immediate）——
     *  所以要先 `waitForIdle()` 把主 looper 推起来再读（M3-2 的老坑）。 */
    private fun <T> awaitVm(timeoutMs: Long = 5_000, block: suspend () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            runBlocking { block() }?.let { return it }
            Thread.sleep(20)
        }
        error("等 VM 超时")
    }

    @Test
    fun `重命名与删除：走 VM（重名给提示、软删可撤销）`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        composeRule.setContent { } // 空宿主：waitForIdle 要有个 composition 才推得动主 looper
        runBlocking {
            app.database.billFilterDao().upsert(
                dev.penned.app.domain.BillFilterCodec.toEntity(
                    id = "f1", bookId = bookId, name = "只看支出",
                    filter = dev.penned.app.domain.TxFilter(type = TxType.EXPENSE),
                    sortOrder = 0, now = 1L, deviceId = "test",
                )
            )
            app.database.billFilterDao().upsert(
                dev.penned.app.domain.BillFilterCodec.toEntity(
                    id = "f2", bookId = bookId, name = "只看收入",
                    filter = dev.penned.app.domain.TxFilter(type = TxType.INCOME),
                    sortOrder = 1, now = 1L, deviceId = "test",
                )
            )
        }
        val vm = BillFiltersViewModel(app)
        // 行按 DAO 读回的实体手搓（摘要口径由 TxFilterTest 钉；这里测的是重命名/删除逻辑）
        val stored = awaitVm {
            runBlocking { app.database.billFilterDao().observeByBook(bookId).first() }.takeIf { it.isNotEmpty() }
        }
        assertEquals(listOf("只看支出", "只看收入"), stored.map { it.name })
        val rows = stored.map { BillFilterRow(it, "") }

        // 重名：报错、不改库
        var error: String? = null
        vm.rename(rows[1], "只看支出") { error = it }
        awaitVm { error?.takeIf { true } }
        assertEquals("已经有一个叫「只看支出」的组合了", error)
        assertEquals("只看收入", runBlocking { app.database.billFilterDao().get("f2")!!.name })

        // 正常改名
        error = null
        vm.rename(rows[1], "工资月") { error = it }
        awaitVm { runBlocking { app.database.billFilterDao().get("f2")!!.name }.takeIf { it == "工资月" } }
        assertEquals(null, error)
        assertEquals("工资月", runBlocking { app.database.billFilterDao().get("f2")!!.name })

        // 删除 + 撤销
        vm.delete(rows[0])
        val alive = awaitVm { runBlocking { app.database.billFilterDao().observeByBook(bookId).first() }
            .takeIf { it.size == 1 } }
        assertEquals(listOf("f2"), alive.map { it.id })
        vm.undoDelete()
        val restored = awaitVm { runBlocking { app.database.billFilterDao().observeByBook(bookId).first() }
            .takeIf { it.size == 2 } }
        assertEquals(2, restored.size)
    }
}
