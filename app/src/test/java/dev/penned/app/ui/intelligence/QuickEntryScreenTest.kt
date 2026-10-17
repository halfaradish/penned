package dev.penned.app.ui.intelligence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.QuickDraft
import dev.penned.app.domain.QuickParseResult
import dev.penned.app.domain.TxType
import dev.penned.app.ui.add.CategoryUi
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 一句话记账页（M4-3）的三态截图与关键接线：输入态 / 结果卡（含"来自关键词规则"标记）/ 失败态。
 *
 * 交互链（输入→解析→改分类→保存→首页）在 `QuickEntryFlowTest` 里用真库真导航跑；这里只钉渲染。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickEntryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val draft = QuickDraft(
        type = TxType.EXPENSE,
        amountCents = 3250,
        note = "打车",
        epochDay = LocalDate.now().minusDays(1).toEpochDay(),
        dayWord = "昨天",
    )

    private fun render(
        text: String = "",
        outcome: QuickParseOutcome? = null,
        type: TxType = TxType.EXPENSE,
        amountCents: Long = 0,
        categoryName: String? = null,
        categoryFromRule: Boolean = false,
        dayLabel: String = "今天",
        note: String? = null,
    ) {
        composeRule.setContent {
            PennedTheme {
                QuickEntryContent(
                    text = text,
                    outcome = outcome,
                    type = type,
                    amountCents = amountCents,
                    categoryName = categoryName,
                    categoryFromRule = categoryFromRule,
                    dayLabel = dayLabel,
                    note = note,
                    saving = false,
                    onTextChange = {}, onPaste = {}, onParse = {}, onToggleType = {},
                    onPickCategory = {}, onPickDate = {}, onEditNote = {}, onSave = {}, onBack = {},
                )
            }
        }
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureQuickInput() {
        render()
        composeRule.onNodeWithText("说话记账？").assertDoesNotExist() // 占位是这句长文案
        composeRule.onNodeWithText("说说这笔账，比如「昨天打车 32.5」").assertIsDisplayed()
        capture("m4_quick_input")
    }

    @Test
    fun captureQuickResult() {
        render(
            text = "昨天打车 32.5",
            outcome = QuickParseOutcome(QuickParseResult.Ok(draft), categoryId = "c1", fromRule = true),
            type = TxType.EXPENSE,
            amountCents = 3250,
            categoryName = "出行交通",
            categoryFromRule = true,
            dayLabel = "10月16日",
            note = "打车",
        )
        composeRule.onNodeWithTag(QUICK_ROW_TYPE).assertTextContains("支出")
        composeRule.onNodeWithTag(QUICK_ROW_AMOUNT).assertTextContains("32.50")
        composeRule.onNodeWithTag(QUICK_ROW_CATEGORY).assertTextContains("来自关键词规则")
        composeRule.onNodeWithTag(QUICK_ROW_DATE).assertTextContains("10月16日")
        composeRule.onNodeWithTag(QUICK_ROW_NOTE).assertTextContains("打车")
        capture("m4_quick_result")
    }

    @Test
    fun captureQuickResultWithoutRuleCategory() {
        render(
            text = "肯打鸡花了15",
            outcome = QuickParseOutcome(
                QuickParseResult.Ok(draft.copy(amountCents = 1500, note = "肯打鸡花了", epochDay = null, dayWord = null)),
                categoryId = null,
                fromRule = false,
            ),
            type = TxType.EXPENSE,
            amountCents = 1500,
            categoryName = null,
            categoryFromRule = false,
            dayLabel = "今天 10月17日",
            note = "肯打鸡花了",
        )
        composeRule.onNodeWithTag(QUICK_ROW_CATEGORY).assertTextContains("未分类")
        capture("m4_quick_result_uncategorized")
    }

    @Test
    fun captureQuickNoAmount() {
        render(text = "今天心情不错", outcome = QuickParseOutcome(QuickParseResult.NoAmount, null, false))
        composeRule.onNodeWithText("没找到金额——把数字也说出来，比如「午饭 12」").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertDoesNotExist()
        capture("m4_quick_no_amount")
    }

    @Test
    fun typeToggles() {
        // 类型行点一下在支出/收入之间切（回调在流程测试里逐步跑）
        render(
            text = "收到红包 200",
            outcome = QuickParseOutcome(
                QuickParseResult.Ok(draft.copy(type = TxType.INCOME, amountCents = 20_000, note = "收到红包")),
                null, false,
            ),
            type = TxType.INCOME,
            amountCents = 20_000,
            dayLabel = "今天 10月17日",
            note = "收到红包",
        )
        composeRule.onNodeWithTag(QUICK_ROW_TYPE).performClick()
        composeRule.onNodeWithTag(QUICK_ROW_TYPE).assertTextContains("收入")
        composeRule.onNodeWithTag(QUICK_ROW_AMOUNT).assertTextContains("200.00")
        capture("m4_quick_result_income")
    }

    @Test
    fun inputFieldAcceptsText() {
        composeRule.setContent {
            PennedTheme {
                var value = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                QuickEntryContent(
                    text = value.value,
                    outcome = null, type = TxType.EXPENSE, amountCents = 0,
                    categoryName = null, categoryFromRule = false, dayLabel = "今天",
                    note = null, saving = false,
                    onTextChange = { value.value = it },
                    onPaste = {}, onParse = {}, onToggleType = {}, onPickCategory = {},
                    onPickDate = {}, onEditNote = {}, onSave = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(QUICK_TEXT_FIELD_TAG).performTextInput("昨天打车 32.5")
        composeRule.onNodeWithTag(QUICK_TEXT_FIELD_TAG).assertTextContains("昨天打车 32.5")
    }
}
