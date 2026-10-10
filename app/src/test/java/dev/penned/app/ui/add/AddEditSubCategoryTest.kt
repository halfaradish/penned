package dev.penned.app.ui.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.TxType
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
import java.time.ZoneId

/**
 * 编辑一条**子分类**流水时，宫格要展开到它的父分类（UI-13 修的显示缺陷，真库端到端）。
 *
 * 修复前：`AddViewModel.loadForEdit` 从 `categories.value` 找父分类，而那是 WhileSubscribed 的
 * `stateIn(…, emptyList())`——编辑页刚开时还没被订阅、读到的必然是空表，`?: cid` 就回落成**子分类 id**。
 * 宫格判"哪一行展开"用的是「父格里有没有这个 id」，于是父格不选中、**子分类条带整条不渲染**：
 * 用户看到的是"这条流水好像没分类"。数据本身没丢（保存仍带原分类），所以只有显示层能抓到。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddEditSubCategoryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        val bookId = "book-sub-test"
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test"),
        )
        app.database.categoryDao().upsertAll(
            listOf(
                CategoryEntity(
                    id = "cat-parent", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE,
                    sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test",
                ),
                CategoryEntity(
                    id = "cat-child", bookId = bookId, parentId = "cat-parent", name = "饮料酒水",
                    type = CategoryType.EXPENSE, sortOrder = 1, createdAt = now, updatedAt = now, deviceId = "test",
                ),
                CategoryEntity(
                    id = "cat-other", bookId = bookId, name = "出行交通", type = CategoryType.EXPENSE,
                    sortOrder = 2, createdAt = now, updatedAt = now, deviceId = "test",
                ),
            ),
        )
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-sub", bookId = bookId, type = TxType.EXPENSE, amountCents = 600,
                categoryId = "cat-child",
                dateEpochMinute = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toEpochSecond() / 60,
                note = "", creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
            ),
        )
        app.settings.setCurrentBook(bookId)
    }

    @Test
    fun `编辑子分类流水时宫格展开到父分类并显示子分类`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        // 单独建一个 VM 断言状态：展开的必须是**父分类 id**（修复前是子分类 id）
        val viewModel = AddViewModel(app, editId = "tx-sub")

        composeRule.setContent {
            PennedTheme {
                AddTransactionScreen(
                    editId = "tx-sub",
                    app = app,
                    onDone = {},
                    onBack = {},
                )
            }
        }
        // 金额到达 = loadForEdit 跑完了，状态可以断言
        composeRule.waitUntil(5_000) { count("6.00") >= 1 }
        composeRule.waitUntil(5_000) { count("饮料酒水") == 1 }

        // 状态层：展开的是父分类（修复前回落成 cat-child）
        composeRule.waitForIdle()
        assertEquals("cat-parent", viewModel.expandedParentId.value)
        assertEquals("cat-child", viewModel.selectedCategoryId.value)

        // 界面层：子分类条带整条渲染出来，父格里也在
        composeRule.onNodeWithText("饮料酒水").assertIsDisplayed()
        composeRule.onNodeWithText("食品餐饮").assertIsDisplayed()
    }

    @Test
    fun `父分类流水照旧：展开自己这一行`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        // 把那条流水的分类改成一级分类（父分类的 parentId 为空 → 展开自己，与旧行为一致）
        runBlocking {
            val dao = app.database.transactionDao()
            val tx = dao.get("tx-sub")!!
            dao.upsert(tx.copy(categoryId = "cat-parent"))
        }
        val viewModel = AddViewModel(app, editId = "tx-sub")

        composeRule.setContent {
            PennedTheme {
                AddTransactionScreen(editId = "tx-sub", app = app, onDone = {}, onBack = {})
            }
        }
        composeRule.waitUntil(5_000) { count("6.00") >= 1 }
        composeRule.waitForIdle()

        assertEquals("cat-parent", viewModel.expandedParentId.value)
        assertEquals("cat-parent", viewModel.selectedCategoryId.value)
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size
}
