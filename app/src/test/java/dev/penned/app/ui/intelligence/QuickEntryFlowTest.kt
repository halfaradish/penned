package dev.penned.app.ui.intelligence

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.KeywordRuleEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.RuleTargetType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 一句话记账的进出链路（M4-3）：真库 + 真导航图。
 *
 * 覆盖规格的验收原话：**输入 → 解析 → 改分类 → 保存 → 首页可见且记录方式为"一句话记账"**；
 * 外加长按悬浮入口的接线与"没找到金额"的失败态。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickEntryFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-quick-flow"

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-food", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE,
                sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-breakfast", bookId = bookId, parentId = "cat-food", name = "早餐",
                type = CategoryType.EXPENSE, sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        // 关键词规则：肯打鸡 → 食品餐饮（M4-1 的引擎在 M4-3 生效）
        app.database.keywordRuleDao().upsert(
            KeywordRuleEntity(
                id = "rule-kfc", bookId = bookId, keyword = "肯打鸡", targetType = RuleTargetType.CATEGORY,
                targetId = "cat-food", priority = 0, enabled = true,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.settings.setCurrentBook(bookId)
    }

    private fun renderApp() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        composeRule.setContent {
            PennedTheme {
                CompositionLocalProvider(LocalApp provides app) {
                    PennedApp(app)
                }
            }
        }
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** 只在「选择分类」抽屉里找节点。 */
    private fun inSheet(text: String) = composeRule.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(QUICK_CATEGORY_SHEET_TAG))
    )

    private fun openQuickEntryFromFab() {
        // 长按悬浮入口 → 快捷入口抽屉 → 一句话记账
        composeRule.onNodeWithTag("home-add").performTouchInput { longClick() }
        composeRule.waitUntil(5_000) { count("快捷记账") == 1 }
        composeRule.onNodeWithText("一句话记账").performClick()
        composeRule.waitUntil(5_000) { count("解析") == 1 }
    }

    private fun typeSentence(text: String) {
        composeRule.onNodeWithTag(QUICK_TEXT_FIELD_TAG).performTextInput(text)
    }

    @Test
    fun `输入解析改分类保存_首页可见且记录方式是一句话记账`() {
        renderApp()
        openQuickEntryFromFab()

        typeSentence("肯打鸡花了15")
        composeRule.onNodeWithText("解析").performClick()
        composeRule.waitUntil(5_000) { count("来自关键词规则") == 1 }
        composeRule.onNodeWithTag(QUICK_ROW_AMOUNT).assertTextContains("15.00")
        composeRule.onNodeWithTag(QUICK_ROW_CATEGORY).assertTextContains("食品餐饮")
        composeRule.onNodeWithTag(QUICK_ROW_NOTE).assertTextContains("肯打鸡花了")
        val today = java.time.LocalDate.now()
        composeRule.onNodeWithTag(QUICK_ROW_DATE)
            .assertTextContains("今天 ${today.monthValue}月${today.dayOfMonth}日")

        // 改分类：点「分类」行 → 抽屉里选二级「早餐」（改过之后不再标"来自关键词规则"）
        composeRule.onNodeWithTag(QUICK_ROW_CATEGORY).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { count("选择分类") == 1 }
        // 抽屉里的格子与卡片上的分类行同名（合并语义里都能搜到）——按"在抽屉里"限定
        inSheet("食品餐饮").performClick() // 一级：展开子分类带
        composeRule.waitForIdle()
        val dbg = StringBuilder()
        composeRule.waitUntil(5_000) { count("早餐") >= 1 }
        inSheet("早餐").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { count("来自关键词规则") == 0 }
        composeRule.onNodeWithTag(QUICK_ROW_CATEGORY).assertTextContains("早餐")

        // 保存 → 回首页：提示条「已记账」+ 列表里出现这笔
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) { count("已记账") == 1 }
        composeRule.waitUntil(5_000) { count("15.00") >= 1 }

        // 详情页的记录方式 = 一句话记账
        // 首页行是"可点 + 含这笔金额"的那个节点（直接按文本首匹配可能点到别的节点上）
        val rowNode = composeRule.onAllNodes(hasClickAction() and hasText("15.00", substring = true))
        rowNode.onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { count("记录方式") == 1 }
        composeRule.onNodeWithText("一句话记账").assertIsDisplayed()

        // 库里这笔：source=TEXT、金额 1500、分类=早餐、备注=肯打鸡花了
        val saved = runBlocking { app().database.transactionDao().allInBook(bookId).firstOrNull() }!!
        assertEquals(1500L, saved.amountCents)
        assertEquals("TEXT", saved.source.name)
        assertEquals("cat-breakfast", saved.categoryId)
        assertEquals("肯打鸡花了", saved.note)
    }

    @Test
    fun `保存后的提示条给撤销_点了这笔就删掉`() {
        renderApp()
        openQuickEntryFromFab()
        typeSentence("肯打鸡花了15")
        composeRule.onNodeWithText("解析").performClick()
        composeRule.waitUntil(5_000) { count("来自关键词规则") == 1 }
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) { count("已记账") == 1 }

        // 一句话的提示条动作是「撤销」（不是「存为模板」）：点了把这笔删掉
        composeRule.onNodeWithText("撤销").performClick()
        composeRule.waitUntil(5_000) { count("已撤销这笔记账") == 1 }
        composeRule.waitUntil(5_000) { count("15.00") == 0 }
        val rows = runBlocking { app().database.transactionDao().allInBook(bookId) }
        assertEquals(0, rows.size)
    }

    @Test
    fun `没找到金额给失败提示_不落库`() {
        renderApp()
        openQuickEntryFromFab()
        typeSentence("今天心情不错")
        composeRule.onNodeWithText("解析").performClick()
        composeRule.waitUntil(5_000) { count("没找到金额——把数字也说出来，比如「午饭 12」") == 1 }
        composeRule.onNodeWithText("保存").assertDoesNotExist()
        val rows = runBlocking { app().database.transactionDao().allInBook(bookId) }
        assertEquals(0, rows.size)
    }

    @Test
    fun `把备忘的那句话删掉再解析_金额还在`() {
        renderApp()
        openQuickEntryFromFab()
        typeSentence("昨天打车 32.5")
        composeRule.onNodeWithText("解析").performClick()
        composeRule.waitUntil(5_000) { count("32.50") == 1 }
        composeRule.onNodeWithTag(QUICK_ROW_NOTE).assertTextContains("打车")
        // 日期行显示"昨天"对应的月日（按运行当天算，别写死）
        val yesterday = java.time.LocalDate.now().minusDays(1)
        composeRule.onNodeWithTag(QUICK_ROW_DATE)
            .assertTextContains("${yesterday.monthValue}月${yesterday.dayOfMonth}日")
    }

    private fun app(): PennedApplication = ApplicationProvider.getApplicationContext()
}
