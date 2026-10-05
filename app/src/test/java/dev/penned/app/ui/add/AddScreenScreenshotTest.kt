package dev.penned.app.ui.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.TxType
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
                    type = TxType.EXPENSE,
                    amountText = "16.5",
                    note = "",
                    selectedId = "p0",
                    expandedId = "p0",
                    canSave = true,
                    showEquals = false,
                    accountId = null,
                    fromId = null,
                    toId = null,
                    feeText = "",
                    accounts = emptyList(),
                    parents = listOf(
                        CategoryUi("p0", "其他", hasSubs = true),
                        CategoryUi("p1", "购物消费", hasSubs = true),
                        CategoryUi("p2", "食品餐饮", hasSubs = true),
                        CategoryUi("p3", "出行交通", hasSubs = false),
                        CategoryUi("p4", "休闲娱乐", hasSubs = false),
                        CategoryUi("p5", "居家生活", hasSubs = true),
                        CategoryUi("p6", "文化教育", hasSubs = false),
                    ),
                    expandedSubs = listOf(
                        CategoryUi("s0", "罚款赔偿", hasSubs = false),
                        CategoryUi("s1", "理财支出", hasSubs = false),
                        CategoryUi("s2", "慈善捐助", hasSubs = false),
                    ),
                    onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
                    onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
                    onSwap = {}, onFeeChange = {}, onEvaluate = {},
                    onSave = {}, onSaveAgain = {}, onBack = {}, onEdit = {},
                )
            }
        }
        // 规格断言：返回途径存在、子分类行内展开可见、键盘动作键存在
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        composeRule.onNodeWithText("罚款赔偿").assertIsDisplayed()
        composeRule.onNodeWithText("完成").assertExists()
        composeRule.onNodeWithText("再记").assertExists()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_add_screen.png")
    }
}
