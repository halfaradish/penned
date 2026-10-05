package dev.penned.app.ui.budgets

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
class BudgetScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureBudgetScreenEmpty() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val viewModel = BudgetsViewModel(app)
        composeRule.setContent {
            PennedTheme {
                BudgetScreen(viewModel = viewModel, onBack = {})
            }
        }
        composeRule.onNodeWithText("设置本月总预算").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_budget_screen.png")
    }
}
