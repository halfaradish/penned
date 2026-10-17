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
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 记一笔的「报销」chip 与报销抽屉（M3-3）：三态回显、账户/金额两行、金额行走同一个键盘。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddReimburseScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val accounts = listOf(
        AccountEntity("acc-1", "b1", "微信", AccountType.DEBIT, createdAt = 0, updatedAt = 0, deviceId = "d"),
        AccountEntity("acc-2", "b1", "招行信用卡", AccountType.CREDIT, createdAt = 0, updatedAt = 0, deviceId = "d"),
    )

    @androidx.compose.runtime.Composable
    private fun content(
        state: ReimburseState = ReimburseState.NONE,
        accountId: String? = null,
        claimLabel: String? = "全额 100.00",
        reimburseText: String = "",
        amountField: AmountField = AmountField.AMOUNT,
        onFocusField: (AmountField) -> Unit = {},
        onPickReimburseAccount: (String?) -> Unit = {},
    ) = AddTransactionContent(
        type = TxType.EXPENSE,
        amountText = "100",
        note = "住酒店",
        selectedId = "p0",
        expandedId = null,
        canSave = true,
        showEquals = false,
        accountId = null,
        fromId = null,
        toId = null,
        feeText = "",
        reimburseState = state,
        reimburseAccountId = accountId,
        reimburseText = reimburseText,
        claimLabel = claimLabel,
        amountField = amountField,
        accounts = accounts,
        parents = listOf(CategoryUi("p0", "购物消费", hasSubs = false)),
        expandedSubs = emptyList(),
        onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
        onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
        onSwap = {}, onFeeChange = {}, onFocusField = onFocusField,
        onPickReimburseAccount = onPickReimburseAccount,
        onEvaluate = {}, onSave = {}, onSaveAgain = {}, onBack = {}, onEdit = {},
    )

    /** 信息条 chips 单行横滑（优化轮 3）：把目标滚进视口再点/断（LazyRow 视口外的 chip 还没 compose）。 */
    private fun revealChip(text: String) =
        composeRule.onNodeWithTag(ADD_CHIP_ROW_TAG).performScrollToNode(hasText(text))

    @Test
    fun captureChipStates() {
        // 待报销（选中态）+ 自定金额：chip 只回显状态，金额在抽屉/详情里看
        composeRule.setContent {
            PennedTheme { content(state = ReimburseState.PENDING, accountId = "acc-1", claimLabel = "40.00") }
        }
        revealChip("待报销")
        composeRule.onNodeWithText("待报销").assertIsDisplayed()
        revealChip("优惠")
        composeRule.onNodeWithText("优惠").assertIsDisplayed()   // 信息条顺序没被挤乱
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_reimburse_chip.png")
    }

    @Test
    fun captureReimburseSheet() {
        composeRule.setContent {
            PennedTheme { content(state = ReimburseState.PENDING, accountId = "acc-1") }
        }
        revealChip("待报销")
        composeRule.onNodeWithText("待报销").performClick()
        // 三态都在（就地改、抽屉不关），账户/金额两行 + 说明
        composeRule.onNodeWithText("不报销").assertIsDisplayed()
        composeRule.onNodeWithText("已报销").assertIsDisplayed()
        composeRule.onNodeWithText("报销账户").assertIsDisplayed()
        composeRule.onNodeWithText("微信").assertIsDisplayed()
        composeRule.onNodeWithText("报销金额").assertIsDisplayed()
        composeRule.onNodeWithText("全额 100.00").assertIsDisplayed()
        composeRule.onNodeWithText("选好了").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_reimburse_sheet.png")
    }

    @Test
    fun noneStateSheetHasNoRows() {
        composeRule.setContent { PennedTheme { content() } }
        revealChip("不报销")
        composeRule.onNodeWithText("不报销").performClick()
        composeRule.onNodeWithText("选好了").assertIsDisplayed()
        // 不报销 = 没有账户/金额可言：两行不出现
        composeRule.onNodeWithText("报销账户").assertDoesNotExist()
        composeRule.onNodeWithText("报销金额").assertDoesNotExist()
    }

    @Test
    fun amountRowHandsTheKeypadOver() {
        // 「报销金额」行不弹输入框：收起抽屉 + 把键盘指到报销那一格（提示条随即出现）
        var focused: AmountField? = null
        composeRule.setContent {
            PennedTheme {
                content(state = ReimburseState.PENDING, reimburseText = "40", onFocusField = { focused = it })
            }
        }
        revealChip("待报销")
        composeRule.onNodeWithText("待报销").performClick()
        composeRule.onNodeWithText("报销金额").performClick()
        assertEquals(AmountField.REIMBURSE, focused)
    }

    @Test
    fun keypadOnReimburseShowsFocusBar() {
        composeRule.setContent {
            PennedTheme {
                content(
                    state = ReimburseState.PENDING,
                    reimburseText = "40",
                    amountField = AmountField.REIMBURSE,
                )
            }
        }
        composeRule.onNodeWithText("正在输入：报销金额").assertIsDisplayed()
        composeRule.onNodeWithText("回到金额").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_reimburse_focus.png")
    }
}
