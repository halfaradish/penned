package dev.penned.app.ui.data

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.PennedApplication
import dev.penned.app.ui.mine.MineScreen
import dev.penned.app.ui.theme.PennedTheme
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// qualifiers 指定真机尺寸（D018）：默认 320x480 会压扁内容区
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DataScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureMineScreen() {
        composeRule.setContent {
            PennedTheme {
                MineScreen(onOpenDataManagement = {}, onOpenCategories = {})
            }
        }
        composeRule.onNodeWithText("数据管理").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_mine_screen.png")
    }

    @Test
    fun captureDataManagementScreen() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val viewModel = DataViewModel(app)
        composeRule.setContent {
            PennedTheme {
                DataManagementScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenBatches = {},
                )
            }
        }
        composeRule.onNodeWithText("一木记账 xls 导入").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_data_management.png")
    }

    @Test
    fun captureImportBatchesEmptyScreen() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val viewModel = DataViewModel(app)
        composeRule.setContent {
            PennedTheme {
                ImportBatchesScreen(
                    viewModel = viewModel,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("还没有导入记录").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_import_batches_empty.png")
    }
}
