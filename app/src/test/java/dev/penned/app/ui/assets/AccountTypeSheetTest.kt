package dev.penned.app.ui.assets

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 添加账户第一步：四个大类一次铺开，每组末尾一个「自定义」格（对标参考图）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountTypeSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun showsEveryGroupWithItsTiles() {
        composeRule.setContent {
            PennedTheme { AccountTypeSheet(onPick = { _, _ -> }, onDismiss = {}) }
        }
        composeRule.onNodeWithText("选择账户类型").assertIsDisplayed()
        // 四个大类标题
        listOf("资金账户", "信贷账户", "投资理财", "负债").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        // 每组的预设格 + 每组一个「自定义」格
        listOf("现金", "储蓄卡", "支付宝", "微信钱包", "信用卡", "花呗", "理财账户", "欠款").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onAllNodesWithText("自定义").assertCountEquals(4)
        capture("account_type_sheet")
    }

    @Test
    fun pickingPresetReportsGroupAndPreset() {
        var pickedGroup: AccountGroup? = null
        var pickedPreset: AccountPreset? = null
        composeRule.setContent {
            PennedTheme {
                AccountTypeSheet(onPick = { g, p -> pickedGroup = g; pickedPreset = p }, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("支付宝").performClick()
        assertEquals(AccountGroup.FUNDS, pickedGroup)
        assertEquals("支付宝", pickedPreset?.label)
    }

    @Test
    fun pickingCustomReportsGroupWithNullPreset() {
        var pickedGroup: AccountGroup? = null
        var pickedPreset: AccountPreset? = null
        var picked = false
        composeRule.setContent {
            PennedTheme {
                AccountTypeSheet(onPick = { g, p -> picked = true; pickedGroup = g; pickedPreset = p }, onDismiss = {})
            }
        }
        // 「自定义」每类一个：第 2 个是信贷账户组的
        composeRule.onAllNodesWithText("自定义")[1].performClick()
        assertEquals(true, picked)
        assertEquals(AccountGroup.CREDIT, pickedGroup)
        assertEquals(null, pickedPreset)
    }
}
