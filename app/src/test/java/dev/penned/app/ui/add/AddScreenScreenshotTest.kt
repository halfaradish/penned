package dev.penned.app.ui.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
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
        onSwap = {}, onFeeChange = {}, onPickDate = onPickDate, onEvaluate = {},
        onSave = {}, onSaveAgain = {}, onBack = {}, onEdit = {},
    )

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
}

