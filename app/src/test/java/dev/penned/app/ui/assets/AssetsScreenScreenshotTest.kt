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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
