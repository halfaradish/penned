package dev.penned.app.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.SearchQuery
import dev.penned.app.domain.TxFilter
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
            refundCents = 0, discountCents = 0,
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
                    filter = TxFilter(keyword = "遮光布"),
                    results = all.filter { it.note!!.contains("遮光布") },
                    summary = FilterSummary(1, 0, 1454),
                    zone = zone,
                )
            }
        }
        composeRule.onNodeWithText("1 笔 · 收入 0.00 · 支出 14.54").assertIsDisplayed()
        // 输入框里是关键词、结果行里是备注 → 两处都出现
        composeRule.onAllNodesWithText("遮光布").assertCountEquals(2)
        composeRule.onNodeWithText("-14.54").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_search_results.png")
    }

    @Test
    fun captureSearchEmptyQuery() {
        composeRule.setContent {
            PennedTheme { SearchContent(filter = TxFilter(), results = emptyList(), summary = FilterSummary(0, 0, 0), zone = zone) }
        }
        composeRule.onNodeWithText("输入关键词，或点上面的条件开始筛选").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_search_idle.png")
    }

    @Test
    fun captureSearchNoMatch() {
        composeRule.setContent {
            PennedTheme {
                SearchContent(filter = TxFilter(keyword = "不存在"), results = emptyList(), summary = FilterSummary(0, 0, 0), zone = zone)
            }
        }
        composeRule.onNodeWithText("没有匹配的流水").assertIsDisplayed()
    }

    /** 组合筛选态（M3-7）：条件 chip 回显值 + 汇总行 + 导出入口。 */
    @Test
    fun captureFilterWithConditions() {
        composeRule.setContent {
            PennedTheme {
                SearchContent(
                    filter = TxFilter(
                        type = TxType.EXPENSE,
                        fromEpochDay = LocalDate.now().minusDays(6).toEpochDay(),
                        toEpochDay = LocalDate.now().toEpochDay(),
                        categoryIds = listOf("c1"),
                        tagIds = listOf("g1", "g2"),
                        amountMinCents = 1000,
                    ),
                    results = all.filter { it.type == TxType.EXPENSE },
                    summary = FilterSummary(2, 0, 3054),
                    zone = zone,
                )
            }
        }
        // chip 回显：类型 / 近 7 天 / 分类 · 1 / 标签 · 2 / ≥ 10.00
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("近 7 天").assertIsDisplayed()
        composeRule.onNodeWithText("分类 · 1").assertIsDisplayed()
        composeRule.onNodeWithText("标签 · 2").assertIsDisplayed()
        composeRule.onNodeWithText("≥ 10.00").assertIsDisplayed()
        composeRule.onNodeWithText("清空").assertIsDisplayed()
        composeRule.onNodeWithText("2 笔 · 收入 0.00 · 支出 30.54").assertIsDisplayed()
        composeRule.onNodeWithText("导出").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_filter_conditions.png")
    }

    /** 条件抽屉（分类多选）：就地勾选、底部「选好了」。 */
    @Test
    fun captureFilterCategorySheet() {
        composeRule.setContent {
            PennedTheme {
                SearchContent(
                    filter = TxFilter(),
                    results = emptyList(),
                    summary = FilterSummary(0, 0, 0),
                    categories = listOf(
                        dev.penned.app.data.db.entity.CategoryEntity(
                            id = "c1", bookId = "b1", name = "食品餐饮",
                            type = dev.penned.app.domain.CategoryType.EXPENSE,
                            createdAt = 0, updatedAt = 0, deviceId = "d",
                        ),
                        dev.penned.app.data.db.entity.CategoryEntity(
                            id = "c2", bookId = "b1", parentId = "c1", name = "午餐",
                            type = dev.penned.app.domain.CategoryType.EXPENSE, sortOrder = 1,
                            createdAt = 0, updatedAt = 0, deviceId = "d",
                        ),
                    ),
                    autofocus = false,
                    zone = zone,
                )
            }
        }
        composeRule.onNodeWithText("分类").performClick()
        composeRule.onNodeWithText("食品餐饮").assertIsDisplayed()
        composeRule.onNodeWithText("食品餐饮 · 午餐").assertIsDisplayed()
        composeRule.onNodeWithText("选好了").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_filter_category_sheet.png")
    }

    /** 更多条件抽屉（三态）：不限 → 是 → 否。 */
    @Test
    fun captureFilterMoreSheet() {
        composeRule.setContent {
            PennedTheme {
                SearchContent(
                    filter = TxFilter(),
                    results = emptyList(),
                    summary = FilterSummary(0, 0, 0),
                    autofocus = false,
                    zone = zone,
                )
            }
        }
        composeRule.onNodeWithText("更多").performClick()
        composeRule.onNodeWithText("不计入收支").assertIsDisplayed()
        composeRule.onNodeWithText("不计入预算").assertIsDisplayed()
        composeRule.onNodeWithText("结果含转账").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_filter_more_sheet.png")
    }

    /** 输入 → 过滤 → 结果列表跟着变（真实键盘输入，覆盖 UI→查询链路）。 */
    @Test
    fun typingFiltersResults() {
        val query = mutableStateOf("")
        composeRule.setContent {
            PennedTheme {
                val f = TxFilter(keyword = query.value)
                SearchContent(
                    filter = f,
                    // 用真实解析函数决定匹配结果，等价于 DAO 的 LIKE 行为
                    results = all.filter { r ->
                        val q = query.value.trim()
                        SearchQuery.isSearchable(q) &&
                            (r.note?.contains(q) == true || r.categoryName?.contains(q) == true ||
                                r.amountCents == SearchQuery.amountCentsOrNull(q))
                    },
                    summary = FilterSummary(1, 350_000, 0),
                    onKeywordChange = { query.value = it },
                    zone = zone,
                )
            }
        }
        composeRule.onNodeWithText("输入关键词，或点上面的条件开始筛选").assertIsDisplayed()

        composeRule.onNodeWithText("搜索备注 / 分类 / 金额").performTextInput("工资")
        composeRule.onNodeWithText("1 笔 · 收入 3,500.00 · 支出 0.00").assertIsDisplayed()
        composeRule.onNodeWithText("+3,500.00").assertIsDisplayed()

        // 清除（图标按钮，按 contentDescription 定位）→ 回到初始提示
        composeRule.onNodeWithContentDescription("清除").performClick()
        composeRule.onNodeWithText("输入关键词，或点上面的条件开始筛选").assertIsDisplayed()
    }
}
