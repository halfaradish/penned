package dev.penned.app.ui.templates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.TemplateWithTags
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 模板三屏（M3-5）：列表 / 新建编辑 / 记一笔的模板抽屉。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TemplateScreensScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val templates = listOf(
        TemplateWithTags(
            id = "t1", name = "午饭", type = TxType.EXPENSE, amountCents = 1650,
            categoryId = "c1", accountId = "a1", targetAccountId = null, note = "公司楼下",
            feeCents = 0, sortOrder = 0, categoryName = "食品餐饮", accountName = "微信",
            targetAccountName = null, tagNames = "报销中",
        ),
        TemplateWithTags(
            id = "t2", name = "还信用卡", type = TxType.TRANSFER, amountCents = 100_000,
            categoryId = null, accountId = "a1", targetAccountId = "a2", note = null,
            feeCents = 0, sortOrder = 1, categoryName = null, accountName = "招行信用卡",
            targetAccountName = "储蓄卡", tagNames = null,
        ),
    )

    private val categories = listOf(
        CategoryEntity(
            id = "c1", bookId = "b1", name = "食品餐饮", type = CategoryType.EXPENSE,
            createdAt = 0, updatedAt = 0, deviceId = "d",
        ) to "",
    )

    private val accounts = listOf(
        AccountEntity(
            id = "a1", bookId = "b1", name = "微信", type = AccountType.DEBIT,
            createdAt = 0, updatedAt = 0, deviceId = "d",
        ),
        AccountEntity(
            id = "a2", bookId = "b1", name = "储蓄卡", type = AccountType.DEBIT,
            createdAt = 0, updatedAt = 0, deviceId = "d",
        ),
    )

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureTemplateList() {
        composeRule.setContent {
            PennedTheme {
                TemplatesContent(templates = templates, onBack = {}, onOpenTemplate = {}, onNewTemplate = {})
            }
        }
        composeRule.onNodeWithText("模板记账").assertIsDisplayed()
        // 行 = 名称 + 摘要（类型 · 金额 · 分类 · 账户 [+ 标签]）；转账行写两端
        composeRule.onNodeWithText("午饭").assertIsDisplayed()
        composeRule.onNodeWithText("支出 · 16.50 · 食品餐饮 · 微信 · 报销中").assertIsDisplayed()
        composeRule.onNodeWithText("转账 · 1,000.00 · 招行信用卡 → 储蓄卡").assertIsDisplayed()
        capture("m3_template_list")
    }

    @Test
    fun captureTemplateListEmpty() {
        composeRule.setContent {
            PennedTheme { TemplatesContent(templates = emptyList(), onBack = {}, onOpenTemplate = {}, onNewTemplate = {}) }
        }
        composeRule.onNodeWithText("还没有模板").assertIsDisplayed()
        capture("m3_template_list_empty")
    }

    /** 第一条没有「上移」块（已经在最前），第二条有；左滑露出动作区（QQ 口径）。 */
    @Test
    fun firstRowHasNoMoveUpAction() {
        composeRule.setContent {
            PennedTheme {
                TemplatesContent(
                    templates = templates,
                    onBack = {}, onOpenTemplate = {}, onNewTemplate = {},
                    revealedRowId = "t2",
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("上移").assertIsDisplayed()
        composeRule.onAllNodesWithText("删除").onFirst().assertIsDisplayed()
        capture("m3_template_list_action")
    }

    @Test
    fun captureNewTemplate() {
        composeRule.setContent {
            PennedTheme {
                TemplateEditContent(
                    state = TemplateEditUiState(
                        name = "午饭",
                        amountText = "16.50",
                        categoryId = "c1",
                        accountId = "a1",
                        note = "公司楼下",
                        loaded = true,
                    ),
                    categories = categories,
                    accounts = accounts,
                    tags = emptyList(),
                    onBack = {},
                    onNameChange = {}, onPickType = {}, onAmountChange = {}, onPickCategory = {},
                    onPickAccount = {}, onPickTargetAccount = {}, onNoteChange = {}, onFeeChange = {},
                    onToggleTag = {}, onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("新建模板").assertIsDisplayed()
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("食品餐饮").assertIsDisplayed()
        composeRule.onNodeWithText("微信").assertIsDisplayed()
        composeRule.onNodeWithText("不选").assertIsDisplayed()
        composeRule.onNodeWithText("删除模板").assertDoesNotExist()
        capture("m3_template_new")
    }

    @Test
    fun saveDisabledWithoutNameOrAmount() {
        composeRule.setContent {
            PennedTheme {
                TemplateEditContent(
                    state = TemplateEditUiState(loaded = true),
                    categories = categories,
                    accounts = accounts,
                    tags = emptyList(),
                    onBack = {},
                    onNameChange = {}, onPickType = {}, onAmountChange = {}, onPickCategory = {},
                    onPickAccount = {}, onPickTargetAccount = {}, onNoteChange = {}, onFeeChange = {},
                    onToggleTag = {}, onSave = {},
                )
            }
        }
        // 名称/金额/分类缺一都存不下（不弹错，也不许存半张模板）
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
    }

    @Test
    fun captureEditTransferTemplate() {
        composeRule.setContent {
            PennedTheme {
                TemplateEditContent(
                    state = TemplateEditUiState(
                        templateId = "t2",
                        name = "还信用卡",
                        type = TxType.TRANSFER,
                        amountText = "1000.00",
                        accountId = "a1",
                        targetAccountId = "a2",
                        loaded = true,
                    ),
                    categories = categories,
                    accounts = accounts,
                    tags = emptyList(),
                    onBack = {},
                    onNameChange = {}, onPickType = {}, onAmountChange = {}, onPickCategory = {},
                    onPickAccount = {}, onPickTargetAccount = {}, onNoteChange = {}, onFeeChange = {},
                    onToggleTag = {}, onSave = {}, onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("编辑模板").assertIsDisplayed()
        // 转账面板：两端 + 手续费，没有分类行
        composeRule.onNodeWithText("转出账户").assertIsDisplayed()
        composeRule.onNodeWithText("转入账户").assertIsDisplayed()
        composeRule.onNodeWithText("手续费").assertIsDisplayed()
        composeRule.onNodeWithText("删除模板").assertIsDisplayed()
        capture("m3_template_edit")
    }

    @Test
    fun captureTemplatePicker() {
        composeRule.setContent {
            PennedTheme {
                dev.penned.app.ui.add.AddTransactionContent(
                    type = TxType.EXPENSE,
                    amountText = "16.5",
                    note = "",
                    selectedId = "c1",
                    expandedId = null,
                    canSave = true,
                    showEquals = false,
                    accountId = "a1",
                    fromId = null,
                    toId = null,
                    feeText = "",
                    accounts = accounts,
                    parents = listOf(dev.penned.app.ui.add.CategoryUi("c1", "食品餐饮", hasSubs = false)),
                    expandedSubs = emptyList(),
                    templates = templates,
                    onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
                    onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
                    onSwap = {}, onFeeChange = {}, onEvaluate = {}, onSave = {}, onSaveAgain = {},
                    onBack = {}, onEdit = {},
                )
            }
        }
        // chip 在信息条上（M3-5）；点开是模板抽屉
        composeRule.onNodeWithText("模板").performClick()
        composeRule.onNodeWithText("选择模板").assertIsDisplayed()
        composeRule.onNodeWithText("午饭").assertIsDisplayed()
        composeRule.onNodeWithText("管理模板").assertIsDisplayed()
        capture("m3_add_template_picker")
    }
}
