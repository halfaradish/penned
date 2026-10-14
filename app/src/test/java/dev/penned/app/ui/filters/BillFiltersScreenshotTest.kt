package dev.penned.app.ui.filters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.entity.BillFilterEntity
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 账单组合列表（M3-8）：行 = 名称 + 条件摘要；空态给「去筛选」出路。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BillFiltersScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun row(id: String, name: String, summary: String) = BillFilterRow(
        entity = BillFilterEntity(
            id = id, bookId = "b1", name = name, type = TxType.EXPENSE,
            createdAt = 0, updatedAt = 0, deviceId = "d",
        ),
        summary = summary,
    )

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureBillFilters() {
        composeRule.setContent {
            PennedTheme {
                BillFiltersContent(
                    rows = listOf(
                        row("f1", "上月餐饮", "支出 · 食品餐饮、购物消费 · 近 30 天"),
                        row("f2", "待报销", "1 个标签 · 不计收支 · ≥ 100.00"),
                    ),
                    onBack = {}, onApply = {},
                )
            }
        }
        composeRule.onNodeWithText("账单组合").assertIsDisplayed()
        composeRule.onNodeWithText("上月餐饮").assertIsDisplayed()
        composeRule.onNodeWithText("支出 · 食品餐饮、购物消费 · 近 30 天").assertIsDisplayed()
        composeRule.onNodeWithText("待报销").assertIsDisplayed()
        capture("m3_bill_filters")
    }

    @Test
    fun captureBillFiltersEmpty() {
        composeRule.setContent {
            PennedTheme { BillFiltersContent(rows = emptyList(), onBack = {}, onApply = {}) }
        }
        composeRule.onNodeWithText("还没有账单组合").assertIsDisplayed()
        composeRule.onNodeWithText("去筛选").assertIsDisplayed()
        capture("m3_bill_filters_empty")
    }

    @Test
    fun captureRowActionRevealed() {
        composeRule.setContent {
            PennedTheme {
                BillFiltersContent(
                    rows = listOf(row("f1", "上月餐饮", "支出 · 近 30 天")),
                    onBack = {}, onApply = {}, revealedRowId = "f1",
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("重命名").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
        capture("m3_bill_filters_action")
    }
}
