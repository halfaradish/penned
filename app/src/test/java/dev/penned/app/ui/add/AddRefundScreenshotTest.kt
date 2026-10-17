package dev.penned.app.ui.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.RefundMath
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 记一笔的退款 / 优惠态（M3-2）：chip 回显、实付算式、键盘目标条。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddRefundScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @androidx.compose.runtime.Composable
    private fun content(
        refundText: String = "",
        discountText: String = "",
        amountField: AmountField = AmountField.AMOUNT,
        paySummary: String? = null,
        onFocusField: (AmountField) -> Unit = {},
    ) = AddTransactionContent(
        type = TxType.EXPENSE,
        amountText = if (refundText.isEmpty() && discountText.isEmpty()) "16.5" else "20.00",
        note = "",
        selectedId = "p0",
        expandedId = null,
        canSave = true,
        showEquals = false,
        accountId = null,
        fromId = null,
        toId = null,
        feeText = "",
        refundText = refundText,
        discountText = discountText,
        amountField = amountField,
        paySummary = paySummary,
        accounts = emptyList(),
        parents = listOf(
            CategoryUi("p0", "购物消费", hasSubs = false),
            CategoryUi("p1", "食品餐饮", hasSubs = false),
        ),
        expandedSubs = emptyList(),
        onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
        onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
        onSwap = {}, onFeeChange = {}, onFocusField = onFocusField,
        onEvaluate = {}, onSave = {}, onSaveAgain = {}, onBack = {}, onEdit = {},
    )

    /** 信息条 chips 单行横滑（优化轮 3）：把目标滚进视口再点/断（LazyRow 视口外的 chip 还没 compose）。 */
    private fun revealChip(text: String) =
        composeRule.onNodeWithTag(ADD_CHIP_ROW_TAG).performScrollToNode(hasText(text))

    @Test
    fun captureRefundState() {
        composeRule.setContent {
            PennedTheme {
                content(
                    refundText = "3.00",
                    amountField = AmountField.REFUND,
                    paySummary = RefundMath.summaryText(2000L, 300L, 0L),
                )
            }
        }
        // 规格断言：两个 chip 在、退款 chip 回显金额、实付行写成算式、键盘提示条指在退款格
        revealChip("退款 3.00")
        composeRule.onNodeWithText("退款 3.00").assertIsDisplayed()
        revealChip("优惠")
        composeRule.onNodeWithText("优惠").assertIsDisplayed()
        composeRule.onNodeWithText("实付 17.00（20.00 − 退款 3.00）").assertIsDisplayed()
        composeRule.onNodeWithText("正在输入：退款").assertIsDisplayed()
        composeRule.onNodeWithText("回到金额").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_refund.png")
    }

    @Test
    fun captureRefundAndDiscountState() {
        composeRule.setContent {
            PennedTheme {
                content(
                    refundText = "3.00",
                    discountText = "5.00",
                    amountField = AmountField.DISCOUNT,
                    paySummary = RefundMath.summaryText(2000L, 300L, 500L),
                )
            }
        }
        composeRule.onNodeWithText("实付 12.00（20.00 − 退款 3.00 − 优惠 5.00）").assertIsDisplayed()
        composeRule.onNodeWithText("正在输入：优惠").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_refund_discount.png")
    }

    @Test
    fun refundAndDiscountChipsFocusTheKeypad() {
        // 无状态组件只上报"键盘该指哪一格"；主金额态没有提示条
        var focused: AmountField? = null
        composeRule.setContent { PennedTheme { content(onFocusField = { focused = it }) } }
        revealChip("退款")
        composeRule.onNodeWithText("退款").performClick()
        assertEquals(AmountField.REFUND, focused)
        revealChip("优惠")
        composeRule.onNodeWithText("优惠").performClick()
        assertEquals(AmountField.DISCOUNT, focused)
        // 键盘在主金额格时两样都不出现：没有提示条、也没有实付行（chip 就是默认文案）
        composeRule.onNodeWithText("正在输入：退款").assertDoesNotExist()
        composeRule.onNodeWithText("回到金额").assertDoesNotExist()
        composeRule.onNodeWithText("实付", substring = true).assertDoesNotExist()
    }
}
