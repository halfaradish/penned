package dev.penned.app.ui.add

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.CategoryType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// qualifiers 指定真机尺寸：默认 320x480 会把 weight(1f) 的宫格区压成 0 高
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureAddScreen() {
        composeRule.setContent {
            PennedTheme {
                AddTransactionContent(
                    type = CategoryType.EXPENSE,
                    amountText = "16.5",
                    selectedCategoryId = "c0",
                    note = "北苑餐厅",
                    canSave = true,
                    categories = listOf(
                        "食品餐饮", "购物消费", "出行交通", "文化教育",
                        "休闲娱乐", "居家生活", "健康医疗", "其他",
                    ).mapIndexed { i, n -> CategoryChip("c$i", n) },
                    onSelectType = {}, onKey = {}, onSelectCategory = {}, onNoteChange = {}, onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("食品餐饮").assertExists()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m0_add_screen.png")
    }
}
