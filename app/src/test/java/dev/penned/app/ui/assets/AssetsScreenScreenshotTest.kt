package dev.penned.app.ui.assets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.AccountWithBalance
import dev.penned.app.domain.AccountType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M1 第一批验收缺项：资产页 Roborazzi 截图。 */
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

    @Test
    fun captureAssetsScreen() {
        composeRule.setContent {
            PennedTheme { AssetsContent(state = state(), onAddAccount = {}) }
        }
        // 规格断言：净资产/总资产/负资产汇总、分组标题、账户余额（负数为信用卡）
        composeRule.onNodeWithText("净资产(元)").assertIsDisplayed()
        composeRule.onNodeWithText("727.50").assertIsDisplayed()
        // 分组标题按账户大类推导：现金/支付宝 → 资金账户，信用卡 → 信贷账户
        composeRule.onNodeWithText("资金账户").assertIsDisplayed()
        composeRule.onNodeWithText("信贷账户").assertIsDisplayed()
        composeRule.onNodeWithText("支付宝").assertIsDisplayed()
        composeRule.onNodeWithText("现金").assertIsDisplayed()
        composeRule.onNodeWithText("招行信用卡").assertIsDisplayed()
        composeRule.onNodeWithText("-589.00").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_assets_screen.png")
    }

    @Test
    fun captureAssetsEmptyState() {
        composeRule.setContent {
            PennedTheme { AssetsContent(state = AssetsUiState(), onAddAccount = {}) }
        }
        composeRule.onNodeWithText("你还没有任何账户").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_assets_empty.png")
    }
}
