package dev.penned.app.ui.books

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M1 S3：账本管理页（含删除=软删）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BooksScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun book(id: String, name: String) = BookEntity(
        id = id, name = name, sortOrder = 0, createdAt = 1L, updatedAt = 1L, deviceId = "d",
    )

    private val books = listOf(book("b1", "日常账本"), book("b2", "旅行账本"), book("b3", "装修账本"))

    @Test
    fun captureBooksScreen() {
        composeRule.setContent {
            PennedTheme {
                BooksContent(books = books, currentId = "b2", deleteCount = 128)
            }
        }
        composeRule.onNodeWithText("当前账本").assertIsDisplayed()
        composeRule.onNodeWithText("旅行账本").assertIsDisplayed()
        // 每个账本都有重命名 + 删除
        composeRule.onAllNodesWithText("重命名").assertCountEquals(3)
        composeRule.onAllNodesWithText("删除").assertCountEquals(3)
        composeRule.onAllNodesWithText("删除")[0].assertIsEnabled()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_books_screen.png")
    }

    @Test
    fun deleteDisabledWhenOnlyOneBookLeft() {
        composeRule.setContent {
            PennedTheme {
                BooksContent(books = listOf(book("b1", "日常账本")), currentId = "b1")
            }
        }
        // 至少保留一个账本：最后一个账本的删除键不可点
        composeRule.onNodeWithText("删除").assertIsNotEnabled()
    }
}
