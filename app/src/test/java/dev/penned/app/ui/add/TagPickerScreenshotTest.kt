package dev.penned.app.ui.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 记一笔的「标签」chip 与标签抽屉（M3-1）：信息条回显数量，抽屉两级 + 多选 + 新建入口。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagPickerScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tags = listOf(
        TagEntity("t1", "b1", null, "出差", 0, false, 0, 0, false, "d"),
        TagEntity("t2", "b1", "t1", "交通", 1, false, 0, 0, false, "d"),
        TagEntity("t3", "b1", "t1", "住宿", 2, false, 0, 0, false, "d"),
        TagEntity("t4", "b1", null, "报销中", 3, false, 0, 0, false, "d"),
    )

    @androidx.compose.runtime.Composable
    private fun content(selected: Set<String>) = AddTransactionContent(
        type = TxType.EXPENSE,
        amountText = "16.5",
        note = "",
        selectedId = "p0",
        expandedId = null,
        canSave = true,
        showEquals = false,
        accountId = null,
        fromId = null,
        toId = null,
        feeText = "",
        accounts = emptyList(),
        parents = listOf(CategoryUi("p0", "食品餐饮", hasSubs = false)),
        expandedSubs = emptyList(),
        tags = tags,
        selectedTagIds = selected,
        onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
        onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
        onSwap = {}, onFeeChange = {}, onEvaluate = {}, onSave = {}, onSaveAgain = {},
        onBack = {}, onEdit = {},
    )

    /** 信息条 chips 单行横滑（优化轮 3）：把目标滚进视口再点/断（LazyRow 视口外的 chip 还没 compose）。 */
    private fun revealChip(text: String) =
        composeRule.onNodeWithTag(ADD_CHIP_ROW_TAG).performScrollToNode(hasText(text))

    @Test
    fun captureChipWithTagsSelected() {
        composeRule.setContent { PennedTheme { content(setOf("t1", "t2")) } }
        // 信息条只回显数量（明细在抽屉里）
        revealChip("标签 · 2")
        composeRule.onNodeWithText("标签 · 2").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_tag_chip.png")
    }

    @Test
    fun captureTagPickerSheet() {
        composeRule.setContent { PennedTheme { content(emptySet()) } }
        revealChip("标签")
        composeRule.onNodeWithText("标签").performClick()
        composeRule.onNodeWithText("选择标签").assertIsDisplayed()
        // 两级：一级「出差」与它的二级（交通/住宿）都在；多选抽屉有明确的收尾动作
        composeRule.onNodeWithText("出差").assertIsDisplayed()
        composeRule.onNodeWithText("交通").assertIsDisplayed()
        composeRule.onNodeWithText("住宿").assertIsDisplayed()
        composeRule.onNodeWithText("新建标签").assertIsDisplayed()
        composeRule.onNodeWithText("选好了").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_tag_picker.png")
    }

    @Test
    fun captureEmptyTagPicker() {
        composeRule.setContent {
            PennedTheme {
                AddTransactionContent(
                    type = TxType.EXPENSE,
                    amountText = "16.5",
                    note = "",
                    selectedId = "p0",
                    expandedId = null,
                    canSave = true,
                    showEquals = false,
                    accountId = null,
                    fromId = null,
                    toId = null,
                    feeText = "",
                    accounts = emptyList(),
                    parents = listOf(CategoryUi("p0", "食品餐饮", hasSubs = false)),
                    expandedSubs = emptyList(),
                    onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
                    onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
                    onSwap = {}, onFeeChange = {}, onEvaluate = {}, onSave = {}, onSaveAgain = {},
                    onBack = {}, onEdit = {},
                )
            }
        }
        revealChip("标签")
        composeRule.onNodeWithText("标签").performClick()
        // 空态给出路（D044 六态审查）：告诉用户去哪儿建
        composeRule.onNodeWithText("还没有标签——下面「新建标签」建一个").assertIsDisplayed()
        composeRule.onNodeWithText("新建标签").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_add_tag_picker_empty.png")
    }
}
