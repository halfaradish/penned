package dev.penned.app.ui.assets

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.AccountWithBalance
import dev.penned.app.domain.AccountType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** M1 第一批验收缺项：资产页 Roborazzi 截图（AD-2 按参考图重做后的版面）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssetsScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    // groupName 故意留空：分组标题由账户大类推导（此前 groupName 从没写过值，标题永远是"默认分组"）
    private val accounts = listOf(
        AccountWithBalance("a1", "支付宝", AccountType.DEBIT, null, 128_450L, remark = "尾号 6688"),
        AccountWithBalance("a2", "现金", AccountType.CASH, null, 3_200L),
        AccountWithBalance("a3", "招行信用卡", AccountType.CREDIT, null, -58_900L),
    )

    private fun state() = AssetsUiState(
        accounts = accounts,
        // 净资产 = Σ账户 − 应付 + 应收（M3-4：债务并入后 727.50 − 500.00 + 300.00）
        netCents = accounts.sumOf { it.balanceCents } - 50_000L + 30_000L,
        assetCents = accounts.filter { it.balanceCents > 0 }.sumOf { it.balanceCents },
        liabilityCents = -accounts.filter { it.balanceCents < 0 }.sumOf { it.balanceCents },
        reimbursePendingCents = 5_000L,
        reimburseDoneCents = 12_000L,
        payableCents = 50_000L,
        receivableCents = 30_000L,
    )

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureAssetsScreen() {
        composeRule.setContent {
            PennedTheme { AssetsContent(state = state(), onAddAccount = { _, _ -> }) }
        }
        // 规格断言：净资产/总资产/负资产汇总、分组标题、账户余额（负数为信用卡）
        composeRule.onNodeWithText("净资产(元)").assertIsDisplayed()
        // 净资产 = Σ账户 − 应付 + 应收（M3-4：727.50 − 500.00 + 300.00）
        composeRule.onNodeWithText("527.50").assertIsDisplayed()
        // 千分位（AD-1）：总资产 1316.50 → 1,316.50；资金账户组小计同额（1284.50 + 32.00）
        composeRule.onAllNodesWithText("1,316.50").assertCountEquals(2)
        // 分组标题按账户大类推导：现金/支付宝 → 资金账户，信用卡 → 信贷账户
        composeRule.onNodeWithText("资金账户").assertIsDisplayed()
        composeRule.onNodeWithText("信贷账户").assertIsDisplayed()
        composeRule.onNodeWithText("支付宝").assertIsDisplayed()
        composeRule.onNodeWithText("现金").assertIsDisplayed()
        composeRule.onNodeWithText("招行信用卡").assertIsDisplayed()
        // 备注只在写了的时候占副标题一行
        composeRule.onNodeWithText("尾号 6688").assertIsDisplayed()
        // 信贷账户组小计与组内唯一账户同额
        composeRule.onAllNodesWithText("-589.00").assertCountEquals(2)
        // 三张汇总卡
        listOf("报销", "债务", "理财").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        // 报销卡两张数字（M3-3）：可报 50.00 / 已报 120.00——口径是 PENDING/DONE 的报销金额和
        composeRule.onNodeWithText("可报").assertIsDisplayed()
        composeRule.onNodeWithText("50.00").assertIsDisplayed()
        composeRule.onNodeWithText("已报").assertIsDisplayed()
        composeRule.onNodeWithText("120.00").assertIsDisplayed()
        // 债务卡（M3-4）：应付/应收接真数据
        composeRule.onNodeWithText("应付").assertIsDisplayed()
        composeRule.onNodeWithText("500.00").assertIsDisplayed()
        composeRule.onNodeWithText("应收").assertIsDisplayed()
        composeRule.onNodeWithText("300.00").assertIsDisplayed()
        // 眼睛（隐藏金额）
        composeRule.onNodeWithContentDescription("隐藏金额").assertIsDisplayed()
        capture("m1_assets_screen")
    }

    @Test
    fun captureAssetsEmptyState() {
        composeRule.setContent {
            PennedTheme { AssetsContent(state = AssetsUiState(), onAddAccount = { _, _ -> }) }
        }
        composeRule.onNodeWithText("你还没有任何账户").assertIsDisplayed()
        capture("m1_assets_empty")
    }

    /** 已停用段（M3-9）：单独一段、默认折叠、展开后行尾「已停用」；净资产仍把停用账户算进去。 */
    @Test
    fun captureArchivedSection() {
        val accounts = accounts + AccountWithBalance(
            "a9", "旧校园卡", AccountType.DEBIT, null, 15_000L, archived = true,
        )
        val state = AssetsUiState(
            accounts = accounts,
            // 净资产照算：727.50 + 150.00
            netCents = accounts.sumOf { it.balanceCents },
            assetCents = accounts.filter { it.balanceCents > 0 }.sumOf { it.balanceCents },
            liabilityCents = -accounts.filter { it.balanceCents < 0 }.sumOf { it.balanceCents },
        )
        composeRule.setContent {
            PennedTheme { AssetsContent(state = state, onAddAccount = { _, _ -> }) }
        }
        // 默认折叠：段标题在、行不在
        composeRule.onNodeWithText("已停用").assertIsDisplayed()
        composeRule.onNodeWithText("旧校园卡").assertDoesNotExist()
        capture("m3_assets_archived_collapsed")

        // 展开后行尾带「已停用」标记
        composeRule.onNodeWithText("已停用").performClick()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("旧校园卡").assertIsDisplayed()
        composeRule.onAllNodesWithText("已停用").onFirst().assertIsDisplayed()
        capture("m3_assets_archived_expanded")
    }

    /**
     * 账户行左滑动作区露满（UI-13）：账户行点行进编辑页，所以动作区只放「删除」一个 64dp 块。
     * `revealedRowId` 直接给值，省得在截图用例里挥手势。
     */
    @Test
    fun captureAccountRowActionRevealed() {
        composeRule.setContent {
            PennedTheme {
                AssetsContent(state = state(), onAddAccount = { _, _ -> }, revealedRowId = "a1")
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("删除").onFirst().assertIsDisplayed()
        capture("ui13_assets_row_action")
    }

    /**
     * 信用卡年费（M3-11）：副标题写年费与收费日；到期未标记时行尾「记年费」（点它 = 记一笔），
     * 今年已标记后提示消失、副标题仍在。判定日期钉死（`today` 可注入），不跟着真实时钟漂。
     */
    @Test
    fun captureCreditCardFeeRow() {
        val today = LocalDate.of(2026, 3, 12)
        val card = AccountWithBalance(
            "c1", "招行信用卡", AccountType.CREDIT, null, -58_900L,
            annualFeeCents = 48_000L, feeMonth = 3, feeDay = 12,
        )
        val cardState = androidx.compose.runtime.mutableStateOf(card)
        var recorded: AccountWithBalance? = null
        composeRule.setContent {
            PennedTheme {
                AssetsContent(
                    state = AssetsUiState(
                        accounts = listOf(cardState.value),
                        netCents = cardState.value.balanceCents,
                        liabilityCents = -cardState.value.balanceCents,
                    ),
                    onAddAccount = { _, _ -> },
                    onRecordFee = { recorded = it },
                    today = today,
                )
            }
        }
        composeRule.onNodeWithText("年费 ¥480.00 · 每年 3 月 12 日").assertIsDisplayed()
        composeRule.onNodeWithText("记年费").assertIsDisplayed()
        capture("m3_assets_fee_due")

        // 点「记年费」→ 回调带着这个账户出去（真正的落库在 AssetsViewModel.recordFee）
        composeRule.onNodeWithText("记年费").performClick()
        assertEquals("c1", recorded?.id)

        // 今年已标记 → 提示消失，副标题还在
        cardState.value = card.copy(lastFeePaidYear = 2026)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("记年费").assertDoesNotExist()
        composeRule.onNodeWithText("年费 ¥480.00 · 每年 3 月 12 日").assertIsDisplayed()
        capture("m3_assets_fee_paid")

        // 把标记清掉（换一年/清数据的情形）→ 到期提示回来
        cardState.value = card.copy(lastFeePaidYear = null)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("记年费").assertIsDisplayed()
    }

    @Test
    fun collapseGroupAndMaskAmounts() {
        composeRule.setContent {
            PennedTheme { AssetsContent(state = state(), onAddAccount = { _, _ -> }) }
        }
        // 折叠资金账户：组内两行收起，组小计与标题还在
        composeRule.onNodeWithText("资金账户").performClick()
        composeRule.onNodeWithText("支付宝").assertDoesNotExist()
        composeRule.onNodeWithText("招行信用卡").assertIsDisplayed()
        // 让点击涟漪与折叠动画跑完：Robolectric 下不推进时钟会截到半透明的中间帧
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        capture("assets_collapsed")

        // 眼睛：金额全变 ****，净资产数字消失
        composeRule.onNodeWithContentDescription("隐藏金额").performClick()
        assertTrue(composeRule.onAllNodesWithText("****").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("527.50").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("显示金额").assertIsDisplayed()
        capture("assets_masked")
    }
}
