package dev.penned.app.ui.assets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.AccountType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 添加/编辑账户页截图与版面断言。
 *
 * 页面化之前这里是 AlertDialog + 文本输入，Robolectric 下不 idle，只能做"入口存在"断言；
 * 改成独立页后可以正常截图（看板待办里那条的资产页部分随之关闭）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountEditScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureAddAccountFundsGroup() {
        composeRule.setContent {
            PennedTheme {
                AccountEditContent(
                    state = AccountEditUiState(),
                    onBack = {},
                    onSelectGroup = {}, onSelectPreset = {}, onSelectCustom = {},
                    onNameChange = {}, onInitialChange = {}, onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("添加账户").assertIsDisplayed()
        // 四个大类分段 + 资金组预设格 + 自定义格
        listOf("资金", "信贷", "理财", "负债").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        listOf("储蓄卡", "支付宝", "微信钱包", "自定义").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        // 预设名既在格子上也在名称占位里
        composeRule.onAllNodesWithText("现金").assertCountEquals(2)
        composeRule.onNodeWithText("初始余额").assertIsDisplayed()
        composeRule.onNodeWithText("0.00").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsDisplayed()
        // 新增页没有删除入口
        composeRule.onNodeWithText("删除账户").assertDoesNotExist()
        capture("account_add_funds")
    }

    @Test
    fun switchGroupAndPickCustom() {
        var state by mutableStateOf(AccountEditUiState())
        composeRule.setContent {
            PennedTheme {
                AccountEditContent(
                    state = state,
                    onBack = {},
                    onSelectGroup = { group ->
                        val first = ACCOUNT_PRESETS[group].orEmpty().firstOrNull()
                        state = state.copy(
                            group = group,
                            type = first?.type ?: group.defaultType,
                            presetLabel = first?.label,
                        )
                    },
                    onSelectPreset = { state = state.copy(type = it.type, presetLabel = it.label) },
                    onSelectCustom = { state = state.copy(type = state.group.defaultType, presetLabel = null) },
                    onNameChange = { state = state.copy(name = it) },
                    onInitialChange = { state = state.copy(initialText = it) },
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("信贷").performClick()
        // 切到信贷组：信用卡同时出现在格子和名称占位里
        composeRule.onAllNodesWithText("信用卡").assertCountEquals(2)
        composeRule.onNodeWithText("花呗").assertIsDisplayed()
        composeRule.onNodeWithText("自定义").assertIsDisplayed()
        capture("account_add_credit")

        // 自定义格：名称不再有预设兜底（占位改回通用提示），且本组默认类型生效
        composeRule.onNodeWithText("自定义").performClick()
        composeRule.onNodeWithText("账户名称").assertIsDisplayed()
        capture("account_add_custom")
    }

    @Test
    fun captureEditAccount() {
        composeRule.setContent {
            PennedTheme {
                AccountEditContent(
                    state = AccountEditUiState(
                        accountId = "a1",
                        group = AccountGroup.FUNDS,
                        type = AccountType.DEBIT,
                        presetLabel = "支付宝",
                        name = "支付宝",
                        initialText = "1284.50",
                    ),
                    onBack = {},
                    onSelectGroup = {}, onSelectPreset = {}, onSelectCustom = {},
                    onNameChange = {}, onInitialChange = {}, onSave = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("编辑账户").assertIsDisplayed()
        composeRule.onNodeWithText("1284.50").assertIsDisplayed()
        composeRule.onNodeWithText("删除账户").assertIsDisplayed()
        capture("account_edit")
    }

    @Test
    fun editAccountWithTransactionsBlocksDelete() {
        composeRule.setContent {
            PennedTheme {
                AccountEditContent(
                    state = AccountEditUiState(
                        accountId = "a1",
                        type = AccountType.CASH,
                        presetLabel = "现金",
                        name = "现金",
                        pendingTxCount = 3,
                    ),
                    onBack = {},
                    onSelectGroup = {}, onSelectPreset = {}, onSelectCustom = {},
                    onNameChange = {}, onInitialChange = {}, onSave = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("删除账户").assertIsNotEnabled()
        composeRule.onNodeWithText("该账户下有 3 笔流水，先处理流水再删除").assertIsDisplayed()
        capture("account_edit_blocked")
    }
}
