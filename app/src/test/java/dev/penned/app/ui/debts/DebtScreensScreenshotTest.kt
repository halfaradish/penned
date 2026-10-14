package dev.penned.app.ui.debts

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.DebtFlowRow
import dev.penned.app.data.db.dao.DebtWithRepaid
import dev.penned.app.domain.DebtDirection
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 债务三屏（M3-4）：列表（应付/应收两段）/ 新建编辑 / 详情（还款记录）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DebtScreensScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.now()

    private fun borrow(
        id: String = "d1",
        counterparty: String = "张三",
        principal: Long = 100_000L,
        repaid: Long = 40_000L,
        closed: Boolean = false,
    ) = DebtWithRepaid(
        id = id, direction = DebtDirection.BORROW, counterparty = counterparty,
        principalCents = principal, startEpochDay = today.minusDays(30).toEpochDay(),
        dueEpochDay = today.plusDays(20).toEpochDay(), note = null,
        closed = closed, sortOrder = 0, repaidCents = repaid,
    )

    private fun lend(id: String = "d2", counterparty: String = "李四", principal: Long = 30_000L) =
        DebtWithRepaid(
            id = id, direction = DebtDirection.LEND, counterparty = counterparty,
            principalCents = principal, startEpochDay = today.minusDays(5).toEpochDay(),
            dueEpochDay = null, note = "垫的团建费",
            closed = false, sortOrder = 1, repaidCents = 0L,
        )

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureDebtListTwoSections() {
        composeRule.setContent {
            PennedTheme {
                DebtListContent(
                    debts = listOf(borrow(), lend(), borrow(id = "d3", counterparty = "老王", principal = 50_000L, repaid = 50_000L, closed = true)),
                    onBack = {}, onOpenDebt = {}, onNewDebt = {},
                )
            }
        }
        // 两段 + 段小计（应付 600.00 = 1000−400；应收 300.00；已结清的不进小计）
        composeRule.onNodeWithText("应付 · 我欠对方").assertIsDisplayed()
        composeRule.onNodeWithText("应收 · 对方欠我").assertIsDisplayed()
        // 段小计与行剩余同额：各出现两次
        composeRule.onAllNodesWithText("600.00").assertCountEquals(2)
        composeRule.onAllNodesWithText("300.00").assertCountEquals(2)
        // 行：对方 + 结清标记 + 本金/已还/到期
        composeRule.onNodeWithText("张三").assertIsDisplayed()
        composeRule.onNodeWithText("李四").assertIsDisplayed()
        composeRule.onNodeWithText("已结清").assertIsDisplayed()
        composeRule.onAllNodesWithText("剩余").onFirst().assertIsDisplayed()
        capture("m3_debt_list")
    }

    @Test
    fun captureDebtListEmpty() {
        composeRule.setContent {
            PennedTheme { DebtListContent(debts = emptyList(), onBack = {}, onOpenDebt = {}, onNewDebt = {}) }
        }
        composeRule.onNodeWithText("还没有债务").assertIsDisplayed()
        composeRule.onNodeWithText("新建债务").assertIsDisplayed()
        capture("m3_debt_list_empty")
    }

    @Test
    fun captureNewDebt() {
        composeRule.setContent {
            PennedTheme {
                DebtEditContent(
                    state = DebtEditUiState(counterparty = "张三", principalText = "1000.00"),
                    onBack = {},
                    onPickDirection = {}, onCounterpartyChange = {}, onPrincipalChange = {},
                    onPickStartDay = {}, onPickDueDay = {}, onNoteChange = {}, onToggleClosed = {}, onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("新建债务").assertIsDisplayed()
        // 两面 + 本金与日期
        composeRule.onNodeWithText("我欠对方").assertIsDisplayed()
        composeRule.onNodeWithText("对方欠我").assertIsDisplayed()
        composeRule.onNodeWithText("本金").assertIsDisplayed()
        composeRule.onNodeWithText("起始日").assertIsDisplayed()
        composeRule.onNodeWithText("未设").assertIsDisplayed()
        // 新建时没有结清开关与删除键
        composeRule.onNodeWithText("标为结清").assertDoesNotExist()
        composeRule.onNodeWithText("删除债务").assertDoesNotExist()
        capture("m3_debt_new")
    }

    @Test
    fun captureNewDebtCannotSaveWithoutPrincipal() {
        composeRule.setContent {
            PennedTheme {
                DebtEditContent(
                    state = DebtEditUiState(counterparty = "", principalText = ""),
                    onBack = {},
                    onPickDirection = {}, onCounterpartyChange = {}, onPrincipalChange = {},
                    onPickStartDay = {}, onPickDueDay = {}, onNoteChange = {}, onToggleClosed = {}, onSave = {},
                )
            }
        }
        // 对方或本金缺一，保存置灰（不弹错，也不许存半笔）
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
    }

    @Test
    fun captureEditDebtClosed() {
        composeRule.setContent {
            PennedTheme {
                DebtEditContent(
                    state = DebtEditUiState(
                        debtId = "d1",
                        direction = DebtDirection.LEND,
                        counterparty = "李四",
                        principalText = "300.00",
                        note = "垫的团建费",
                        dueEpochDay = today.plusMonths(1).toEpochDay(),
                        closed = true,
                    ),
                    onBack = {},
                    onPickDirection = {}, onCounterpartyChange = {}, onPrincipalChange = {},
                    onPickStartDay = {}, onPickDueDay = {}, onNoteChange = {}, onToggleClosed = {}, onSave = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("编辑债务").assertIsDisplayed()
        composeRule.onNodeWithText("标为结清").assertIsDisplayed()
        composeRule.onNodeWithText("删除债务").assertIsDisplayed()
        capture("m3_debt_edit")
    }

    @Test
    fun captureDebtDetailWithFlows() {
        composeRule.setContent {
            PennedTheme {
                DebtDetailContent(
                    detail = DebtDetailUi(
                        debt = borrow(),
                        flows = listOf(
                            DebtFlowRow("f2", TxType.EXPENSE, 40_000L, "a1", today.minusDays(2).toEpochDay() * 1440L, null, "现金"),
                            DebtFlowRow("f1", TxType.INCOME, 100_000L, "a1", today.minusDays(30).toEpochDay() * 1440L, null, "现金"),
                        ),
                    ),
                    onBack = {}, onEdit = {}, onSettle = {},
                )
            }
        }
        // 剩余 600.00 是主角；三数齐；还款记录两条（借入 + 还款）
        composeRule.onNodeWithText("剩余").assertIsDisplayed()
        composeRule.onNodeWithText("600.00").assertIsDisplayed()
        composeRule.onNodeWithText("本金 1,000.00 · 已还 400.00").assertIsDisplayed()
        // 立场出现两次：hero 副标题 + 「方向」明细行
        composeRule.onAllNodesWithText("我欠对方").assertCountEquals(2)
        composeRule.onNodeWithText("还款记录").assertIsDisplayed()
        composeRule.onNodeWithText("借入").assertIsDisplayed()
        composeRule.onNodeWithText("还款").assertIsDisplayed()
        composeRule.onNodeWithText("记一笔还款").assertIsDisplayed()
        capture("m3_debt_detail")
    }

    @Test
    fun captureDebtDetailWithoutFlows() {
        composeRule.setContent {
            PennedTheme {
                DebtDetailContent(
                    detail = DebtDetailUi(debt = lend(), flows = emptyList()),
                    onBack = {}, onEdit = {}, onSettle = {},
                )
            }
        }
        // 空态给出路（D044）：告诉他去哪儿记
        composeRule.onNodeWithText("还没有资金流水——借入的钱到账、还了钱，都用记一笔的「债务」Tab 记").assertIsDisplayed()
        // 人欠我的主按钮用「收款」
        composeRule.onNodeWithText("记一笔收款").assertIsDisplayed()
        capture("m3_debt_detail_empty")
    }
}
