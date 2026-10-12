package dev.penned.app.ui.detail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.TxType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 详情页「标签」行（M3-1）：标签来自关系表，库里改了要跟着变。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailTagsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-detail-tags"

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-1", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-1", bookId = bookId, type = TxType.EXPENSE, amountCents = 1600,
                categoryId = "cat-1", dateEpochMinute = now / 60_000, note = "北苑餐厅",
                creatorDeviceId = "test", createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.tagDao().upsert(
            TagEntity("tag-trip", bookId, null, "出差", 0, false, now, now, false, "test")
        )
        app.database.tagDao().setTagsForTx("tx-1", listOf("tag-trip"))
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun `详情页显示标签行_清空后消失`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        composeRule.setContent {
            PennedTheme {
                CompositionLocalProvider(LocalApp provides app) {
                    DetailScreen(txId = "tx-1", onEdit = {}, onBack = {})
                }
            }
        }
        composeRule.waitUntil(5_000) { count("标签") == 1 }
        composeRule.onNodeWithText("出差").assertExists()

        // 关系表清空 → 这一行整个消失（不是显示"无"）
        runBlocking { app.database.tagDao().setTagsForTx("tx-1", emptyList()) }
        composeRule.waitUntil(5_000) { count("标签") == 0 }
    }
}
