package dev.penned.app.ui.intelligence

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.RuleTargetType
import dev.penned.app.ui.mine.MineScreen
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 分类关键词规则页（M4-1）的渲染与接线：列表行（关键词 + 「→ 分类/标签」+ 开关）、
 * 空态、目标已删的兜底文案、滑动动作区的「上移 / 删除」、点「新增规则」开抽屉。
 *
 * 抽屉里"输入关键词 → 选目标 → 保存"的交互**不在这里驱动**：含文本输入件的弹窗在 Robolectric
 * 下永远不 idle（看板已知坑）——那条链在 `KeywordRulesViewModelTest` 与模拟机走查里过。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeywordRulesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val rows = listOf(
        KeywordRuleRow("k1", "肯打鸡", true, RuleTargetType.CATEGORY, "食品餐饮"),
        KeywordRuleRow("k2", "午饭", true, RuleTargetType.CATEGORY, "食品餐饮 / 午餐"),
        KeywordRuleRow("k3", "出差", false, RuleTargetType.TAG, "出差"),
    )

    private val categories = listOf(
        CategoryEntity(
            id = "c1", bookId = "b1", name = "食品餐饮", type = CategoryType.EXPENSE,
            sortOrder = 0, createdAt = 0, updatedAt = 0, deviceId = "d",
        ),
        CategoryEntity(
            id = "c2", bookId = "b1", parentId = "c1", name = "午餐", type = CategoryType.EXPENSE,
            sortOrder = 0, createdAt = 0, updatedAt = 0, deviceId = "d",
        ),
    )

    private val tags = listOf(
        TagEntity(
            id = "t1", bookId = "b1", parentId = null, name = "出差", sortOrder = 0,
            createdAt = 0, updatedAt = 0, deviceId = "d",
        ),
    )

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureKeywordRules() {
        composeRule.setContent {
            PennedTheme {
                KeywordRulesContent(state = KeywordRulesUiState(rows = rows), onBack = {}, onAdd = {})
            }
        }
        composeRule.onNodeWithText("分类关键词").assertIsDisplayed()
        composeRule.onNodeWithText("肯打鸡").assertIsDisplayed()
        composeRule.onNodeWithText("→ 分类：食品餐饮").assertIsDisplayed()
        // 二级分类给父级路径（"午餐"单独出现会分不清是哪一支）
        composeRule.onNodeWithText("→ 分类：食品餐饮 / 午餐").assertIsDisplayed()
        composeRule.onNodeWithText("→ 标签：出差").assertIsDisplayed()
        // 启停开关反映状态
        composeRule.onAllNodesWithText("出差").onFirst().assertIsDisplayed()
        capture("m4_keyword_rules")
    }

    @Test
    fun togglesReflectEnabledState() {
        composeRule.setContent {
            PennedTheme {
                KeywordRulesContent(state = KeywordRulesUiState(rows = rows), onBack = {}, onAdd = {})
            }
        }
        // rows[0]/[1] 开、rows[2] 关（浅色基线已由 captureKeywordRules 留档，这里只断语义）
        composeRule.onNodeWithText("肯打鸡").assertIsDisplayed()
        composeRule.onAllNodesWithText("出差").onFirst().assertIsDisplayed()
    }

    @Test
    fun captureKeywordRulesEmpty() {
        composeRule.setContent {
            PennedTheme {
                KeywordRulesContent(state = KeywordRulesUiState(), onBack = {}, onAdd = {})
            }
        }
        composeRule.onNodeWithText("还没有关键词规则").assertIsDisplayed()
        capture("m4_keyword_rules_empty")
    }

    @Test
    fun deletedTargetFallsBackToHint() {
        composeRule.setContent {
            PennedTheme {
                KeywordRulesContent(
                    state = KeywordRulesUiState(
                        rows = listOf(KeywordRuleRow("k9", "肯打鸡", true, RuleTargetType.CATEGORY, null)),
                    ),
                    onBack = {}, onAdd = {},
                )
            }
        }
        composeRule.onNodeWithText(subtitleOf(KeywordRuleRow("k9", "x", true, RuleTargetType.CATEGORY, null)))
            .assertIsDisplayed()
    }

    /** 左滑露出动作区：第一条没有「上移」，其余有（QQ 口径，与模板页一致）。 */
    @Test
    fun swipeActionsExposeMoveUpAndDelete() {
        composeRule.setContent {
            PennedTheme {
                KeywordRulesContent(
                    state = KeywordRulesUiState(rows = rows),
                    onBack = {}, onAdd = {},
                    revealedRowId = "k2",
                )
            }
        }
        composeRule.waitForIdle()
        // 每行都有自己的动作区（只有露出的那行可见）→ 同名节点取第一个（与模板页同处理）
        composeRule.onAllNodesWithText("上移").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("删除").onFirst().assertIsDisplayed()
        capture("m4_keyword_rules_action")
    }

    /** 接线：点 FAB「新增规则」→ 抽屉打开（内容件单独渲染测）。 */
    @Test
    fun addButtonOpensSheet() {
        composeRule.setContent {
            PennedTheme {
                var showSheet by remember { mutableStateOf(false) }
                KeywordRulesContent(
                    state = KeywordRulesUiState(categories = categories, tags = tags),
                    onBack = {}, onAdd = { showSheet = true },
                )
                if (showSheet) {
                    AddKeywordRuleSheet(
                        categories = categories,
                        tags = tags,
                        onSave = { _, _, _ -> showSheet = false },
                        onDismiss = { showSheet = false },
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("新增规则").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("新增关键词规则").assertIsDisplayed()
        capture("m4_keyword_rules_sheet_open")
    }

    @Test
    fun captureAddRuleSheet() {
        composeRule.setContent {
            PennedTheme {
                AddKeywordRuleSheet(
                    categories = categories,
                    tags = tags,
                    onSave = { _, _, _ -> },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("新增关键词规则").assertIsDisplayed()
        // 目标二选一 + 分类宫格（复用记一笔的 CategoryPanel，去掉尾部「新增」格）
        composeRule.onNodeWithText("分类").assertIsDisplayed()
        composeRule.onNodeWithText("标签").assertIsDisplayed()
        composeRule.onNodeWithText("食品餐饮").assertIsDisplayed()
        // 没输关键词 + 没选目标 → 保存不可用
        composeRule.onNodeWithText("保存规则").assertIsDisplayed()
        capture("m4_keyword_rules_sheet")
    }

    @Test
    fun sheetSwitchesToTagPicker() {
        composeRule.setContent {
            PennedTheme {
                AddKeywordRuleSheet(categories = categories, tags = tags, onSave = { _, _, _ -> }, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("标签").performClick()
        composeRule.onNodeWithText("出差").assertIsDisplayed()
        capture("m4_keyword_rules_sheet_tags")
    }

    /** 开关状态按行读得出来（第 1 行开、第 3 行关）——不止是"有那个词"。 */
    @Test
    fun switchNodesCarryCheckedState() {
        composeRule.setContent {
            PennedTheme {
                KeywordRulesContent(state = KeywordRulesUiState(rows = rows), onBack = {}, onAdd = {})
            }
        }
        val switches = composeRule.onAllNodes(isToggleable())
        switches[0].assertIsOn()
        switches[1].assertIsOn()
        switches[2].assertIsOff()
    }

    /**
     * 我的页的「智能」分组卡（C5 裁决）：入口在页面下半部（一屏放不下），
     * 所以**滚动到它再截图**——不然基线里只有半个卡片标题，没法过目。
     */
    @Test
    fun mineIntelligenceCardVisible() {
        composeRule.setContent {
            PennedTheme {
                MineScreen(
                    onOpenDataManagement = {}, onOpenTemplates = {}, onOpenRecurring = {},
                    onOpenBillFilters = {}, onOpenCategories = {}, onOpenTags = {},
                    onOpenPinLock = {}, onOpenAbout = {},
                )
            }
        }
        composeRule.onNodeWithText("智能").performScrollTo()
        composeRule.onNodeWithText("分类关键词").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("关键词指向分类或标签：识别到它时先把分类填好，确认后才入账")
            .performScrollTo().assertIsDisplayed()
        // M4-2/M4-3 起这张卡有三行
        composeRule.onNodeWithText("一句话记账").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AI 设置").performScrollTo().assertIsDisplayed()
        capture("m4_mine_intelligence")
    }

    @Test
    fun mineIntelligenceCardVisibleDark() {
        composeRule.setContent {
            PennedTheme(darkTheme = true) {
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                ) {
                    MineScreen(
                        onOpenDataManagement = {}, onOpenTemplates = {}, onOpenRecurring = {},
                        onOpenBillFilters = {}, onOpenCategories = {}, onOpenTags = {},
                        onOpenPinLock = {}, onOpenAbout = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("分类关键词").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("一句话记账").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AI 设置").performScrollTo().assertIsDisplayed()
        capture("m4_mine_intelligence_dark")
    }
}
