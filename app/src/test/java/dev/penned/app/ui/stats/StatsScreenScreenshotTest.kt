package dev.penned.app.ui.stats

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.ui.home.HomeGroups
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatsScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureStatsScreen() {
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    month = YearMonth.of(2026, 10),
                    totals = HomeGroups.MonthTotals(incomeCents = 350000, expenseCents = 32300),
                    expenseCategories = listOf(
                        HomeGroups.CategoryTotal("食品餐饮", 15600, 0.483f),
                        HomeGroups.CategoryTotal("购物消费", 9500, 0.294f),
                        HomeGroups.CategoryTotal("出行交通", 4200, 0.13f),
                        HomeGroups.CategoryTotal("其他", 3000, 0.093f),
                    ),
                    incomeCategories = listOf(
                        HomeGroups.CategoryTotal("工资", 350000, 1f),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("支出构成").assertIsDisplayed()
        composeRule.onNodeWithText("食品餐饮").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_stats_screen.png")
    }

    @Test
    fun captureStatsScreenEmpty() {
        composeRule.setContent {
            PennedTheme {
                StatsContent(
                    month = YearMonth.of(2026, 10),
                    totals = HomeGroups.MonthTotals(0, 0),
                    expenseCategories = emptyList(),
                    incomeCategories = emptyList(),
                )
            }
        }
        composeRule.onNodeWithText("本月没有收支记录").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_stats_empty.png")
    }
}
