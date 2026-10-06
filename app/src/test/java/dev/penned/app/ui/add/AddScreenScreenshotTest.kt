package dev.penned.app.ui.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.Money
import dev.penned.app.domain.MoneyExpression
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
// qualifiers 指定真机尺寸：默认 320x480 会把 weight(1f) 的宫格区压成 0 高
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @androidx.compose.runtime.Composable
    private fun content(
        dateEpochDay: Long? = null,
        onPickDate: (Long?) -> Unit = {},
        onAddCategory: (String, String?) -> Unit = { _, _ -> },
        onEdit: () -> Unit = {},
    ) = AddTransactionContent(
        type = TxType.EXPENSE,
        amountText = "16.5",
        note = "",
        selectedId = "p0",
        expandedId = "p0",
        canSave = true,
        showEquals = false,
        accountId = null,
        fromId = null,
        toId = null,
        feeText = "",
        dateEpochDay = dateEpochDay,
        accounts = emptyList(),
        parents = listOf(
            CategoryUi("p0", "其他", hasSubs = true),
            CategoryUi("p1", "购物消费", hasSubs = true),
            CategoryUi("p2", "食品餐饮", hasSubs = true),
            CategoryUi("p3", "出行交通", hasSubs = false),
            CategoryUi("p4", "休闲娱乐", hasSubs = false),
            CategoryUi("p5", "居家生活", hasSubs = true),
            CategoryUi("p6", "文化教育", hasSubs = false),
        ),
        expandedSubs = listOf(
            CategoryUi("s0", "罚款赔偿", hasSubs = false),
            CategoryUi("s1", "理财支出", hasSubs = false),
            CategoryUi("s2", "慈善捐助", hasSubs = false),
        ),
        onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
        onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
        onSwap = {}, onFeeChange = {}, onPickDate = onPickDate, onAddCategory = onAddCategory, onEvaluate = {},
        onSave = {}, onSaveAgain = {}, onBack = {}, onEdit = onEdit,
    )

    @Test
    fun editActionOpensCategoryManagement() {
        // 原 disabled 的"编辑"死 UI 已接通分类管理（board 待办）
        var opened = false
        composeRule.setContent { PennedTheme { content(onEdit = { opened = true }) } }
        composeRule.onNodeWithContentDescription("管理分类").performClick()
        org.junit.Assert.assertTrue(opened)
    }

    @Test
    fun captureAddScreen() {
        composeRule.setContent { PennedTheme { content() } }
        // 规格断言：返回途径存在、子分类行内展开可见、键盘动作键存在
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        composeRule.onNodeWithText("罚款赔偿").assertIsDisplayed()
        composeRule.onNodeWithText("完成").assertExists()
        composeRule.onNodeWithText("再记").assertExists()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_add_screen.png")
    }

    @Test
    fun captureAddScreenWithPickedDate() {
        val yesterday = LocalDate.now().toEpochDay() - 1
        composeRule.setContent { PennedTheme { content(dateEpochDay = yesterday) } }
        // 非今天日期：chip 显示该日期且无"今天"前缀
        val d = LocalDate.ofEpochDay(yesterday)
        composeRule.onNodeWithText("${d.monthValue}月${d.dayOfMonth}日").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_add_screen_picked_date.png")
    }

    @Test
    fun captureDatePickerDialog() {
        var picked: Long? = null
        composeRule.setContent { PennedTheme { content(onPickDate = { picked = it }) } }
        composeRule.onNodeWithText("今天", substring = true).performClick()
        composeRule.onNodeWithText("选择记账日期").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_add_date_picker.png")
        // 确定：回传日期
        composeRule.onNodeWithText("确定").performClick()
        org.junit.Assert.assertNotNull(picked)
    }

    @Test
    fun captureAddCategoryEntry() {
        composeRule.setContent { PennedTheme { content() } }
        // 规格断言：一级宫格尾部有「新增」入口（弹窗交互在模拟机人工验证——
        // Robolectric 下点击后 AlertDialog 内无法 idle，见 board 遗留问题）
        composeRule.onNodeWithText("新增").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_add_category_entry.png")
    }

    // ---- M1 第一批验收缺项：转账 Tab ----

    private val transferAccounts = listOf(
        AccountEntity("a1", "b1", "支付宝", AccountType.DEBIT, createdAt = 0, updatedAt = 0, deviceId = "d"),
        AccountEntity("a2", "b1", "储蓄卡", AccountType.DEBIT, createdAt = 0, updatedAt = 0, deviceId = "d"),
    )

    @androidx.compose.runtime.Composable
    private fun transferContent(fromId: String? = null, toId: String? = null, feeText: String = "") =
        AddTransactionContent(
            type = TxType.TRANSFER,
            amountText = "8200",
            note = "还信用卡",
            selectedId = null,
            expandedId = null,
            canSave = true,
            showEquals = false,
            accountId = null,
            fromId = fromId,
            toId = toId,
            feeText = feeText,
            accounts = transferAccounts,
            parents = emptyList(),
            expandedSubs = emptyList(),
            onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
            onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
            onSwap = {}, onFeeChange = {}, onEvaluate = {}, onSave = {}, onSaveAgain = {},
            onBack = {}, onEdit = {},
        )

    @Test
    fun captureTransferTabEmpty() {
        composeRule.setContent { PennedTheme { transferContent() } }
        // 规格断言：转账面板两个账户位 + 互换键 + 手续费 chip
        composeRule.onNodeWithText("转出账户").assertIsDisplayed()
        composeRule.onNodeWithText("转入账户").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("互换账户").assertIsDisplayed()
        composeRule.onNodeWithText("手续费").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_add_transfer_empty.png")
    }

    @Test
    fun captureTransferTabWithAccounts() {
        composeRule.setContent { PennedTheme { transferContent(fromId = "a1", toId = "a2", feeText = "2.00") } }
        composeRule.onNodeWithText("支付宝").assertIsDisplayed()
        composeRule.onNodeWithText("储蓄卡").assertIsDisplayed()
        composeRule.onNodeWithText("手续费 2.00").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_add_transfer.png")
    }

    @Test
    fun captureTransferAccountPicker() {
        var from: String? = null
        composeRule.setContent {
            PennedTheme {
                AddTransactionContent(
                    type = TxType.TRANSFER,
                    amountText = "8200",
                    note = "",
                    selectedId = null,
                    expandedId = null,
                    canSave = false,
                    showEquals = false,
                    accountId = null,
                    fromId = from,
                    toId = null,
                    feeText = "",
                    accounts = transferAccounts,
                    parents = emptyList(),
                    expandedSubs = emptyList(),
                    onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
                    onNoteChange = {}, onPickAccount = {}, onPickFrom = { from = it }, onPickTo = {},
                    onSwap = {}, onFeeChange = {}, onEvaluate = {}, onSave = {}, onSaveAgain = {},
                    onBack = {}, onEdit = {},
                )
            }
        }
        composeRule.onNodeWithText("转出账户").performClick()
        composeRule.onNodeWithText("选择账户").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_add_transfer_picker.png")
        composeRule.onNodeWithText("储蓄卡").performClick()
        org.junit.Assert.assertEquals("a2", from)
    }

    // ---- 加减乘除：键盘 → MoneyExpression 真求值（S2 引擎的键位补齐）----

    /** 把真实键盘与真实求值引擎接起来的最小宿主（等价于 AddViewModel 的接线）。 */
    @androidx.compose.runtime.Composable
    private fun calculatorContent(amount: MutableState<String>) =
        AddTransactionContent(
            type = TxType.EXPENSE,
            amountText = amount.value,
            note = "",
            selectedId = "p0",
            expandedId = null,
            canSave = true,
            showEquals = MoneyExpression.hasPendingOperator(amount.value),
            accountId = null,
            fromId = null,
            toId = null,
            feeText = "",
            accounts = emptyList(),
            parents = listOf(CategoryUi("p0", "其他", hasSubs = false)),
            expandedSubs = emptyList(),
            onSelectType = {}, onKey = { amount.value = MoneyExpression.append(amount.value, it) },
            onSelectParent = {}, onSelectSub = {}, onNoteChange = {}, onPickAccount = {},
            onPickFrom = {}, onPickTo = {}, onSwap = {}, onFeeChange = {},
            onEvaluate = { MoneyExpression.evaluate(amount.value)?.let { amount.value = Money.format(it) } },
            onSave = {}, onSaveAgain = {}, onBack = {}, onEdit = {},
        )

    /** 按键盘键（按 tag 定位，避开与分类宫格重名的文本）。 */
    private fun press(key: String) = composeRule.onNodeWithTag("amount-key-$key").performClick()

    @Test
    fun captureCalculatorMultiplyDivide() {
        val amount = mutableStateOf("12")
        composeRule.setContent { PennedTheme { calculatorContent(amount) } }
        // 四个运算符键都在（一木：右侧运算块 + − × ÷）
        listOf("+", "-", "×", "÷").forEach { composeRule.onNodeWithTag("amount-key-$it").assertIsDisplayed() }

        // 12×3÷4：表达式原样显示，右下键变 "="
        listOf("×", "3", "÷", "4").forEach { press(it) }
        composeRule.onNodeWithText("12×3÷4").assertIsDisplayed()
        composeRule.onNodeWithText("=").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_add_calculator.png")

        // "=" → 9.00（乘除优先、按分结算），右下键回 "完成"
        press("完成")
        composeRule.onNodeWithText("9.00").assertIsDisplayed()
        composeRule.onNodeWithText("完成").assertIsDisplayed()
    }

    @Test
    fun calculatorPrecedenceMultiplyFirst() {
        // 2+3×4 = 14 元（乘除优先，不是 20）
        val amount = mutableStateOf("2")
        composeRule.setContent { PennedTheme { calculatorContent(amount) } }
        listOf("+", "3", "×", "4").forEach { press(it) }
        press("完成")
        composeRule.onNodeWithText("14.00").assertIsDisplayed()
    }

    @Test
    fun calculatorDivideByZeroKeepsExpression() {
        // 除零：求值返回 null → 表达式保持、仍显示 "="（不落库、不崩）
        val amount = mutableStateOf("8÷0")
        composeRule.setContent { PennedTheme { calculatorContent(amount) } }
        press("完成")
        composeRule.onNodeWithText("8÷0").assertIsDisplayed()
        composeRule.onNodeWithText("=").assertIsDisplayed()
    }
}

