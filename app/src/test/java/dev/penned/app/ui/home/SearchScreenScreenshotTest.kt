package dev.penned.app.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.SearchQuery
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** 首页搜索（备注/分类/金额）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun row(id: String, day: Int, cents: Long, note: String?, category: String?, type: TxType = TxType.EXPENSE) =
        TxWithCategory(
            id = id, type = type, amountCents = cents, accountId = null, categoryId = null,
            dateEpochMinute = LocalDate.of(2024, 9, day).atTime(12, 30).atZone(zone).toEpochSecond() / 60,
            note = note, categoryName = category, categoryParentName = null, categoryIconKey = null,
            targetAccountName = null, excludeFromStats = false,
        )

    private val all = listOf(
        row("t1", 3, 1600, "北苑餐厅", "晚餐"),
        row("t2", 2, 1454, "遮光布", "日常家居"),
        row("t3", 1, 350000, "工资", "工资", TxType.INCOME),
    )

    @Test
    fun captureSearchResults() {
        composeRule.setContent {
            PennedTheme {
                SearchContent(
                    query = "遮光布",
                    results = all.filter { it.note!!.contains("遮光布") },
                    zone = zone,
                )
            }
        }
        composeRule.onNodeWithText("找到 1 条").assertIsDisplayed()
        // 输入框里是关键词、结果行里是备注 → 两处都出现
        composeRule.onAllNodesWithText("遮光布").assertCountEquals(2)
        composeRule.onNodeWithText("-14.54").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_search_results.png")
    }

    @Test
    fun captureSearchEmptyQuery() {
        composeRule.setContent { PennedTheme { SearchContent(query = "", results = emptyList(), zone = zone) } }
        composeRule.onNodeWithText("输入备注、分类名或金额开始搜索").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_search_idle.png")
    }

    @Test
    fun captureSearchNoMatch() {
        composeRule.setContent { PennedTheme { SearchContent(query = "不存在", results = emptyList(), zone = zone) } }
        composeRule.onNodeWithText("没有匹配的流水").assertIsDisplayed()
    }

    /** 输入 → 过滤 → 结果列表跟着变（真实键盘输入，覆盖 UI→查询链路）。 */
    @Test
    fun typingFiltersResults() {
        val query = mutableStateOf("")
        composeRule.setContent {
            PennedTheme {
                SearchContent(
                    query = query.value,
                    // 用真实解析函数决定匹配结果，等价于 DAO 的 LIKE 行为
                    results = all.filter { r ->
                        val q = query.value.trim()
                        SearchQuery.isSearchable(q) &&
                            (r.note?.contains(q) == true || r.categoryName?.contains(q) == true ||
                                r.amountCents == SearchQuery.amountCentsOrNull(q))
                    },
                    onQueryChange = { query.value = it },
                    zone = zone,
                )
            }
        }
        composeRule.onNodeWithText("输入备注、分类名或金额开始搜索").assertIsDisplayed()

        composeRule.onNodeWithText("搜索备注 / 分类 / 金额").performTextInput("工资")
        composeRule.onNodeWithText("找到 1 条").assertIsDisplayed()
        composeRule.onNodeWithText("+3500.00").assertIsDisplayed()

        // 清除 → 回到初始提示
        composeRule.onNodeWithText("清除").performClick()
        composeRule.onNodeWithText("输入备注、分类名或金额开始搜索").assertIsDisplayed()
    }
}
