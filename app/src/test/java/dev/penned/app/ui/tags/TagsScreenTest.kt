package dev.penned.app.ui.tags

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 标签管理页的渲染（M3-1）：两级列表 + 已隐藏标记 + 口径说明。
 *
 * 弹窗里的操作（重命名/隐藏/删除）**不在这里驱动**：AlertDialog 内含文本输入框时
 * Robolectric 永远不 idle（看板已知坑，分类新增/重命名的测试也是同一处理）。
 * 那些动作由 `TagsViewModelTest` 在 VM 层测，UI 层交互在模拟机走查里过一遍。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-tags-screen"

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        listOf(
            TagEntity("tag-trip", bookId, null, "出差", 0, false, now, now, false, "test"),
            TagEntity("tag-transport", bookId, "tag-trip", "交通", 1, false, now, now, false, "test"),
            TagEntity("tag-old", bookId, null, "旧标签", 2, true, now, now, false, "test"),
        ).forEach { app.database.tagDao().upsert(it) }
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-1", bookId = bookId, type = TxType.EXPENSE, amountCents = 100,
                dateEpochMinute = now / 60_000, creatorDeviceId = "test",
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.tagDao().setTagsForTx("tx-1", listOf("tag-transport"))
        app.settings.setCurrentBook(bookId)
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun captureTagsScreen() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        val viewModel = TagsViewModel(app)
        composeRule.setContent { PennedTheme { TagsScreen(viewModel = viewModel, onBack = {}) } }
        composeRule.waitUntil(5_000) { count("出差") >= 1 }

        // 两级列表都在，隐藏的那个带「已隐藏」标记（隐藏只是不进选择器，管理页照旧列出）
        composeRule.onNodeWithText("出差").assertIsDisplayed()
        composeRule.onNodeWithText("交通").assertIsDisplayed()
        composeRule.onNodeWithText("旧标签").assertIsDisplayed()
        composeRule.onNodeWithText("已隐藏").assertIsDisplayed()
        // 口径写在页脚：隐藏 vs 软删的区别
        composeRule.onNodeWithText("隐藏的标签不出现在记账选择器与筛选里，已经记过的流水照常显示。").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_tags_screen.png")
    }
}
