package dev.penned.app.ui.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 信息条 chips 单行横滑（优化轮 3）：一行平铺不再换行、两头都滚得到也点得动。
 * 换行那版（FlowRow，D044）在 400dp 下支出 Tab 要占三行；现在行高 = 一枚 chip 高。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddChipRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @androidx.compose.runtime.Composable
    private fun content() = AddTransactionContent(
        type = TxType.EXPENSE,
        amountText = "16.5",
        note = "",
        selectedId = "p0",
        expandedId = null,
        canSave = true,
        showEquals = false,
        accountId = null,
        fromId = null,
        toId = null,
        feeText = "",
        accounts = emptyList(),
        parents = listOf(CategoryUi("p0", "购物消费", hasSubs = false)),
        expandedSubs = emptyList(),
        onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
        onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
        onSwap = {}, onFeeChange = {}, onEvaluate = {}, onSave = {}, onSaveAgain = {},
        onBack = {}, onEdit = {},
    )

    @Test
    fun `信息条单行平铺不换行_最后一枚滚得到也点得动`() {
        composeRule.setContent { PennedTheme { content() } }

        // 单行：行高 = 一枚 chip 的高（换行版本这里是两三倍）
        val rowHeight = with(composeRule.density) {
            composeRule.onNodeWithTag(ADD_CHIP_ROW_TAG).fetchSemanticsNode().size.height.toDp()
        }
        assertTrue(
            "信息条该是单行（一枚 chip 约 ${PennedDimens.chipHeight}），实测 $rowHeight",
            rowHeight <= PennedDimens.chipHeight + 4.dp,
        )

        // 最后一枚（报销 chip）在视口外：滚出来、看得见、能点开抽屉
        composeRule.onNodeWithTag(ADD_CHIP_ROW_TAG).performScrollToNode(hasText("不报销"))
        composeRule.onNodeWithText("不报销").assertIsDisplayed()
        composeRule.onNodeWithText("不报销").performClick()
        composeRule.onNodeWithText("选好了").assertIsDisplayed()

        // 再滚回最左端：头一枚（日期 chip）也回得来
        composeRule.onNodeWithTag(ADD_CHIP_ROW_TAG).performScrollToNode(hasText("今天", substring = true))
        composeRule.onNodeWithText("今天", substring = true).assertIsDisplayed()
    }
}
