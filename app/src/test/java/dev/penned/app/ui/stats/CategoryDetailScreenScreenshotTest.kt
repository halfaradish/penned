package dev.penned.app.ui.stats

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.TxType
import dev.penned.app.ui.home.HomeGroups
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryDetailScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")

    private val octStartDay = java.time.LocalDate.of(2026, 10, 1).toEpochDay()

    private fun tx(id: String, isoDateTime: String, cents: Long, name: String, parent: String, note: String? = null) =
        TxWithCategory(
            id = id, type = TxType.EXPENSE, amountCents = cents, accountId = null, categoryId = null,
            dateEpochMinute = LocalDateTime.parse(isoDateTime).atZone(zone).toEpochSecond() / 60,
            note = note, categoryName = name, categoryParentName = parent,
            categoryIconKey = null, targetAccountName = null, excludeFromStats = false,
            refundCents = 0, discountCents = 0,
        )

    /** 一级分类页：hero 三数 + 子分类占比/数据（照参考图 3）。 */
    @Test
    fun captureParentCategoryDetail() {
        val rows = listOf(
            tx("1", "2026-10-31T19:30", 32746, "晚餐", "食品餐饮"),
            tx("2", "2026-10-30T12:00", 31260, "午餐", "食品餐饮"),
            tx("3", "2026-10-29T15:00", 14168, "休闲零食", "食品餐饮"),
            tx("4", "2026-10-28T08:10", 11860, "早餐", "食品餐饮"),
            // 直接记在一级分类上的行：聚成同名的不可点桶
            tx("5", "2026-10-27T09:00", 5310, "食品餐饮", "食品餐饮"),
        )
        composeRule.setContent {
            PennedTheme {
                CategoryDetailContent(
                    target = CategoryTarget(TxType.EXPENSE, "食品餐饮", parent = null),
                    periodLabel = "2026年10月",
                    summary = StatsMath.summary(rows, TxType.EXPENSE, dayCount = 31),
                    children = StatsMath.childTotals(rows, TxType.EXPENSE),
                    days = HomeGroups.groupByDay(rows),
                    bars = StatsMath.dailyTotals(rows, TxType.EXPENSE, octStartDay, 31),
                    isLeaf = false,
                    startEpochDay = octStartDay,
                )
            }
        }
        composeRule.onNodeWithText("共支出").assertIsDisplayed()
        composeRule.onNodeWithText("日均支出").assertIsDisplayed()
        composeRule.onNodeWithText("账单数量").assertIsDisplayed()
        composeRule.onNodeWithText("5笔").assertIsDisplayed()
        composeRule.onNodeWithText("统计范围 2026年10月").assertIsDisplayed()
        composeRule.onNodeWithText("支出统计").assertIsDisplayed()
        composeRule.onNodeWithText("支出占比").assertIsDisplayed()
        composeRule.onNodeWithText("支出数据").assertIsDisplayed()
        // 晚餐：图例一处 + 数据行一处；「食品餐饮」直挂桶不可点但也列在数据里（另一处是顶栏标题）
        composeRule.onAllNodesWithText("晚餐").assertCountEquals(2)
        composeRule.onAllNodesWithText("食品餐饮").assertCountEquals(2)
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_category_parent.png")
    }

    /** 叶子分类页：hero 三数 + 该分类的流水（照参考图 4）。 */
    @Test
    fun captureLeafCategoryDetail() {
        val rows = listOf(
            tx("1", "2026-10-31T19:30", 950, "晚餐", "食品餐饮", note = "东27"),
            tx("2", "2026-10-30T18:40", 1500, "晚餐", "食品餐饮", note = "东27"),
            tx("3", "2026-10-29T19:05", 1300, "晚餐", "食品餐饮", note = "北苑"),
        )
        composeRule.setContent {
            PennedTheme {
                CategoryDetailContent(
                    target = CategoryTarget(TxType.EXPENSE, "晚餐", parent = "食品餐饮"),
                    periodLabel = "2026年10月",
                    summary = StatsMath.summary(rows, TxType.EXPENSE, dayCount = 31),
                    children = StatsMath.childTotals(rows, TxType.EXPENSE),
                    days = HomeGroups.groupByDay(rows),
                    bars = StatsMath.dailyTotals(rows, TxType.EXPENSE, octStartDay, 31),
                    isLeaf = true,
                    startEpochDay = octStartDay,
                )
            }
        }
        composeRule.onNodeWithText("共支出").assertIsDisplayed()
        // 叶子节点也有按日柱状图（参考图 4：支出统计在流水之上）
        composeRule.onNodeWithText("支出统计").assertIsDisplayed()
        // 3 条流水按日分组（顶栏标题也叫"晚餐"，所以按行标题断言条数）
        composeRule.onAllNodesWithText("食品餐饮 · 晚餐").assertCountEquals(3)
        // 叶子节点没有"子分类数据"卡
        composeRule.onNodeWithText("支出数据").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_category_leaf.png")
    }

    /** 空窗口：hero 归零 + 空态。 */
    @Test
    fun captureEmptyCategoryDetail() {
        composeRule.setContent {
            PennedTheme {
                CategoryDetailContent(
                    target = CategoryTarget(TxType.EXPENSE, "晚餐", parent = "食品餐饮"),
                    periodLabel = "2026年10月",
                    summary = StatsMath.NodeSummary(0, 0, 0),
                    children = emptyList(),
                    days = emptyList(),
                    bars = List(31) { 0L },
                    isLeaf = true,
                    startEpochDay = octStartDay,
                )
            }
        }
        composeRule.onNodeWithText("本期没有该分类的记录").assertIsDisplayed()
        // 没有记录就不出柱状图卡
        composeRule.onNodeWithText("支出统计").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ai_stats_category_empty.png")
    }
}
