package dev.penned.app.ui.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.DebtWithRepaid
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.DebtAction
import dev.penned.app.domain.DebtDirection
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 记一笔「债务」Tab（M3-4）：选择债务 + 借入/归还 + 资金账户；空态给「新建债务」出路。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddDebtScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val accounts = listOf(
        AccountEntity(
            id = "a1", bookId = "b1", name = "现金", type = AccountType.CASH,
            createdAt = 0, updatedAt = 0, deviceId = "d",
        )
    )

    private fun debt(
        id: String = "d1",
        direction: DebtDirection = DebtDirection.BORROW,
        counterparty: String = "张三",
        principal: Long = 100_000L,
        repaid: Long = 40_000L,
    ) = DebtWithRepaid(
        id = id, direction = direction, counterparty = counterparty,
        principalCents = principal, startEpochDay = 20_000L, dueEpochDay = null, note = null,
        closed = false, sortOrder = 0, repaidCents = repaid,
    )

    @androidx.compose.runtime.Composable
    private fun content(
        debts: List<DebtWithRepaid>,
        debtId: String?,
        action: DebtAction,
        accountId: String? = "a1",
    ) = AddTransactionContent(
        type = TxType.EXPENSE,
        amountText = "1000",
        note = "",
        selectedId = null,
        expandedId = null,
        canSave = true,
        showEquals = false,
        accountId = accountId,
        fromId = null,
        toId = null,
        feeText = "",
        accounts = accounts,
        parents = emptyList(),
        expandedSubs = emptyList(),
        debtTab = true,
        debts = debts,
        debtId = debtId,
        debtAction = action,
        onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
        onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
        onSwap = {}, onFeeChange = {}, onEvaluate = {}, onSave = {}, onSaveAgain = {},
        onBack = {}, onEdit = {},
    )

    @Test
    fun captureBorrowPanel() {
        composeRule.setContent {
            PennedTheme { content(debts = listOf(debt()), debtId = "d1", action = DebtAction.OPEN) }
        }
        // 四格 Tab：债务在第四格
        composeRule.onNodeWithText("债务").assertIsDisplayed()
        // 选择债务行：对方 + 立场 + 剩余
        composeRule.onNodeWithText("张三").assertIsDisplayed()
        composeRule.onNodeWithText("我欠对方 · 剩余 600.00").assertIsDisplayed()
        // 两格：借入 / 还款（我欠人这一面的词）
        composeRule.onNodeWithText("借入").assertIsDisplayed()
        composeRule.onNodeWithText("还款").assertIsDisplayed()
        // 资金账户 + 说明
        composeRule.onNodeWithText("现金").assertIsDisplayed()
        composeRule.onNodeWithText("钱进资金账户；这笔债务的欠款从这里开始记").assertIsDisplayed()
        composeRule.onNodeWithText("不计入收支与预算：借入不是收入、还款不是支出（详情页可改）").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_debt_borrow.png")
    }

    @Test
    fun captureRepayPanelForLend() {
        // 人欠我这一面：词换成 借出 / 收到还款，说明也跟着换
        composeRule.setContent {
            PennedTheme {
                content(
                    debts = listOf(debt(direction = DebtDirection.LEND, counterparty = "李四", principal = 30_000L, repaid = 0L)),
                    debtId = "d1",
                    action = DebtAction.SETTLE,
                )
            }
        }
        composeRule.onNodeWithText("李四").assertIsDisplayed()
        composeRule.onNodeWithText("对方欠我 · 剩余 300.00").assertIsDisplayed()
        composeRule.onNodeWithText("借出").assertIsDisplayed()
        composeRule.onNodeWithText("收到还款").assertIsDisplayed()
        composeRule.onNodeWithText("钱进资金账户；冲减对方欠你的钱").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_debt_lend.png")
    }

    @Test
    fun captureEmptyDebtPanel() {
        composeRule.setContent {
            PennedTheme { content(debts = emptyList(), debtId = null, action = DebtAction.OPEN, accountId = null) }
        }
        // 空态给出路（D044）：先去建一笔债务
        composeRule.onNodeWithText("还没有债务").assertIsDisplayed()
        composeRule.onNodeWithText("新建债务").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_debt_empty.png")
    }

    @Test
    fun debtPickerSheetListsDebtsAndCreateEntry() {
        composeRule.setContent {
            PennedTheme { content(debts = listOf(debt(), debt(id = "d2", counterparty = "李四")), debtId = null, action = DebtAction.OPEN) }
        }
        composeRule.onNodeWithText("选择债务").performClick()
        composeRule.onAllNodesWithText("张三").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("李四").assertIsDisplayed()
        composeRule.onNodeWithText("新建债务").assertIsDisplayed()
        composeRule.onNodeWithText("建完回到这里就能选它").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_debt_picker.png")
    }
}
