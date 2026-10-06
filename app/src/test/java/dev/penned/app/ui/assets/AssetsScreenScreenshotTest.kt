package dev.penned.app.ui.assets

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
        AccountWithBalance("a1", "支付宝", AccountType.DEBIT, null, 128_450L),
        AccountWithBalance("a2", "现金", AccountType.CASH, null, 3_200L),
        AccountWithBalance("a3", "招行信用卡", AccountType.CREDIT, null, -58_900L),
    )

    private fun state() = AssetsUiState(
        accounts = accounts,
        netCents = accounts.sumOf { it.balanceCents },
        assetCents = accounts.filter { it.balanceCents > 0 }.sumOf { it.balanceCents },
        liabilityCents = -accounts.filter { it.balanceCents < 0 }.sumOf { it.balanceCents },
        reimbursePendingCents = 5_000L,
        reimburseDoneCents = 12_000L,
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
        composeRule.onNodeWithText("727.50").assertIsDisplayed()
        // 千分位（AD-1）：总资产 1316.50 → 1,316.50；资金账户组小计同额（1284.50 + 32.00）
        composeRule.onAllNodesWithText("1,316.50").assertCountEquals(2)
        // 分组标题按账户大类推导：现金/支付宝 → 资金账户，信用卡 → 信贷账户
        composeRule.onNodeWithText("资金账户").assertIsDisplayed()
        composeRule.onNodeWithText("信贷账户").assertIsDisplayed()
        composeRule.onNodeWithText("支付宝").assertIsDisplayed()
        composeRule.onNodeWithText("现金").assertIsDisplayed()
        composeRule.onNodeWithText("招行信用卡").assertIsDisplayed()
        // 信贷账户组小计与组内唯一账户同额
        composeRule.onAllNodesWithText("-589.00").assertCountEquals(2)
        // 三张汇总卡
        listOf("报销", "债务", "理财").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
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
        composeRule.onNodeWithText("727.50").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("显示金额").assertIsDisplayed()
        capture("assets_masked")
    }
}
