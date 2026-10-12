package dev.penned.app.ui.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 分类隐藏（M3-1，D074）：隐藏的分类不进记账宫格，**但正在编辑的那条流水的分类要留着**
 * （否则编辑页显示不出所属分类、保存时也查不到它——UI-13 那个 `WhileSubscribed` 空表坑的同型）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddHiddenCategoryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-hidden-cat"

    private fun seed(app: PennedApplication, hidden: Boolean) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-visible", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE,
                sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-hidden", bookId = bookId, name = "隐藏分类", type = CategoryType.EXPENSE,
                sortOrder = 1, hidden = hidden, createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-hidden-cat", bookId = bookId, type = TxType.EXPENSE, amountCents = 500,
                categoryId = "cat-hidden", dateEpochMinute = now / 60_000,
                creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.settings.setCurrentBook(bookId)
    }

    @Test
    fun `隐藏的分类不出现在宫格_可见的照旧`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app, hidden = true)
        composeRule.setContent {
            PennedTheme { AddTransactionScreen(editId = null, app = app, onDone = {}, onBack = {}) }
        }
        composeRule.waitUntil(5_000) { count("食品餐饮") >= 1 }
        composeRule.onNodeWithText("食品餐饮").assertIsDisplayed()
        composeRule.onNodeWithText("隐藏分类").assertDoesNotExist()
    }

    @Test
    fun `编辑用隐藏分类记的流水_宫格仍显示它且能保存`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app, hidden = true)
        composeRule.setContent {
            PennedTheme { AddTransactionScreen(editId = "tx-hidden-cat", app = app, onDone = {}, onBack = {}) }
        }
        // 隐藏分类是"不再用"，不是"看不见历史"：编辑这条流水时它必须还在宫格里，且是选中态可保存
        composeRule.waitUntil(5_000) { count("隐藏分类") >= 1 }
        composeRule.onNodeWithText("隐藏分类").assertIsDisplayed()
        composeRule.onNodeWithText("完成").assertIsEnabled().performClick()

        val saved = runBlocking { app.database.transactionDao().get("tx-hidden-cat")!! }
        assertEquals("cat-hidden", saved.categoryId)
        assertEquals(500L, saved.amountCents)
        assertTrue(runBlocking { app.database.categoryDao().get("cat-hidden")!!.hidden })
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size
}
