package dev.penned.app.ui.assets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.AccountType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 添加/编辑账户表单页（对标参考图：两张卡片 + 所属分组选择行）。
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
    fun captureAddAccountFromPreset() {
        composeRule.setContent {
            PennedTheme {
                AccountEditContent(
                    state = AccountEditUiState(group = AccountGroup.FUNDS, presetLabel = "现金"),
                    onBack = {},
                    onSelectGroup = {}, onNameChange = {}, onInitialChange = {}, onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("添加账户").assertIsDisplayed()
        composeRule.onNodeWithText("名称").assertIsDisplayed()
        composeRule.onNodeWithText("余额").assertIsDisplayed()
        composeRule.onNodeWithText("所属分组").assertIsDisplayed()
        composeRule.onNodeWithText("资金账户").assertIsDisplayed()
        // 金额不带单位（全 App 一致），余额行与其它行一样左对齐
        composeRule.onNodeWithText("0.00").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsDisplayed()
        // 新增页没有删除入口
        composeRule.onNodeWithText("删除账户").assertDoesNotExist()
        capture("account_add_preset")
    }

    @Test
    fun captureAddAccountCustom() {
        composeRule.setContent {
            PennedTheme {
                AccountEditContent(
                    // 自定义格进来：名称没有预设兜底
                    state = AccountEditUiState(group = AccountGroup.FUNDS, presetLabel = null),
                    onBack = {},
                    onSelectGroup = {}, onNameChange = {}, onInitialChange = {}, onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("账户名称").assertIsDisplayed()
        capture("account_add_custom")
    }

    @Test
    fun groupPickerRowChangesGroup() {
        composeRule.setContent {
            PennedTheme {
                AccountEditContent(
                    state = AccountEditUiState(group = AccountGroup.FUNDS, presetLabel = "现金"),
                    onBack = {},
                    onSelectGroup = {},
                    onNameChange = {}, onInitialChange = {}, onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("所属分组").performClick()
        // 抽屉里四个大类，选中项标「已选」
        composeRule.onNodeWithText("信贷账户").assertIsDisplayed()
        composeRule.onNodeWithText("已选").assertIsDisplayed()
        capture("account_group_picker")
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
                    onSelectGroup = {}, onNameChange = {}, onInitialChange = {}, onSave = {},
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
    fun scopeAllBooksShowsHint() {
        composeRule.setContent {
            PennedTheme {
                AccountEditContent(
                    state = AccountEditUiState(
                        accountId = "a1",
                        group = AccountGroup.FUNDS,
                        type = AccountType.DEBIT,
                        presetLabel = "微信",
                        name = "微信",
                        remark = "尾号 6688",
                        scopeAllBooks = true,
                    ),
                    onBack = {},
                    onSelectGroup = {}, onNameChange = {}, onInitialChange = {}, onSave = {},
                    onRemarkChange = {}, onScopeChange = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("备注").assertIsDisplayed()
        composeRule.onNodeWithText("尾号 6688").assertIsDisplayed()
        composeRule.onNodeWithText("生效账本").assertIsDisplayed()
        composeRule.onNodeWithText("全部账本").assertIsDisplayed()
        composeRule.onNodeWithText("所有账本都能看到这个账户，含以后新建的账本").assertIsDisplayed()
        capture("account_edit_all_books")
    }

    @Test
    fun bookSummaryListsEffectiveBooks() {
        val books = listOf(
            book("b1", "日常账本"),
            book("b2", "test"),
            book("b3", "旅行账本"),
        )
        // 默认：只有所属账本
        assertEquals("日常账本", AccountEditUiState(books = books, ownerBookId = "b1").bookSummary)
        // 勾了 test：列出生效账本
        assertEquals(
            "日常账本、test",
            AccountEditUiState(books = books, ownerBookId = "b1", extraBookIds = setOf("b2")).bookSummary,
        )
        // 全部账本：忽略勾选
        assertEquals(
            "全部账本",
            AccountEditUiState(
                books = books,
                ownerBookId = "b1",
                extraBookIds = setOf("b2"),
                scopeAllBooks = true,
            ).bookSummary,
        )
    }

    @Test
    fun scopeSheetTogglesBooksAndKeepsOwnerFixed() {
        var state by mutableStateOf(
            AccountEditUiState(
                accountId = "a1",
                group = AccountGroup.FUNDS,
                type = AccountType.DEBIT,
                presetLabel = "微信",
                name = "微信",
                books = listOf(book("b1", "日常账本"), book("b2", "test"), book("b3", "旅行账本")),
                ownerBookId = "b1",
            )
        )
        composeRule.setContent {
            PennedTheme {
                AccountEditContent(
                    state = state,
                    onBack = {},
                    onSelectGroup = {}, onNameChange = {}, onInitialChange = {}, onSave = {},
                    onRemarkChange = {},
                    onScopeChange = { state = state.copy(scopeAllBooks = it) },
                    onToggleBook = { id ->
                        state = state.copy(
                            extraBookIds = if (id in state.extraBookIds) {
                                state.extraBookIds - id
                            } else {
                                state.extraBookIds + id
                            }
                        )
                    },
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("生效账本").performClick()
        // 抽屉：全部账本开关 + 每个账本一行，所属账本带「所属」标记
        composeRule.onNodeWithText("全部账本").assertIsDisplayed()
        composeRule.onNodeWithText("test").assertIsDisplayed()
        composeRule.onNodeWithText("旅行账本").assertIsDisplayed()
        composeRule.onNodeWithText("所属").assertIsDisplayed()
        composeRule.onNodeWithText("所属账本恒定可见；勾选其它账本，这个账户在那边也能用").assertIsDisplayed()
        capture("account_scope_sheet")

        // 勾上 test：只有它进 extraBookIds（所属账本不落引用）
        composeRule.onNodeWithText("test").performClick()
        assertEquals(setOf("b2"), state.extraBookIds)
        assertEquals("日常账本、test", state.bookSummary)
        capture("account_scope_multi")
    }

    private fun book(id: String, name: String) = dev.penned.app.data.db.entity.BookEntity(
        id = id, name = name, createdAt = 0L, updatedAt = 0L, deviceId = "d",
    )

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
                    onSelectGroup = {}, onNameChange = {}, onInitialChange = {}, onSave = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("删除账户").assertIsNotEnabled()
        composeRule.onNodeWithText("该账户下有 3 笔流水，先处理流水再删除").assertIsDisplayed()
        capture("account_edit_blocked")
    }
}
