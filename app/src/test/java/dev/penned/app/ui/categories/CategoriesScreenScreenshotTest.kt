package dev.penned.app.ui.categories

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.PennedApplication
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoriesScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureCategoriesScreen() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val viewModel = CategoriesViewModel(app)
        composeRule.setContent {
            PennedTheme {
                CategoriesScreen(viewModel = viewModel, onBack = {})
            }
        }
        // 两个分区与新增按钮常显（数据异步加载不参与断言）
        composeRule.onNodeWithText("支出分类").assertIsDisplayed()
        composeRule.onNodeWithText("收入分类").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_categories_screen.png")
    }
}
