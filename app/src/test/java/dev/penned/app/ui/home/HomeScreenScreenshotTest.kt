package dev.penned.app.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首页（AQ 轮）：月汇总/预算搬进月卡头部、月卡可折叠（本月默认展开）、
 * 「添加一条新记账」改成 Tab 栏上方的悬浮入口并在往下看时收成右下角「＋」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.systemDefault()

    /** 测试窗口 400dp 宽：悬浮入口展开 = 400-32 = 368dp，收起 = 56dp。 */
    private val expandedEntryWidth = 368f
    private val compactEntryWidth = AddEntryMotion.collapsedSize.value

    /** 跨 7 天 14 行，保证列表可滚动。 */
    private val rows: List<TxWithCategory> = buildList {
        var id = 0
        for (dayOffset in 0 until 7) {
            val day = LocalDate.now(zone).minusDays(dayOffset.toLong())
            repeat(2) { i ->
                id++
                add(
                    TxWithCategory(
                        id = "tx-$id",
                        type = if (i == 0) TxType.EXPENSE else TxType.INCOME,
                        amountCents = if (i == 0) 1600L + dayOffset * 100 else 150000L,
                        accountId = null,
                        categoryId = null,
                        dateEpochMinute = day.atTime(12, 30).atZone(zone).toEpochSecond() / 60,
                        note = "第 ${dayOffset + 1} 天第 ${i + 1} 笔",
                        categoryName = if (i == 0) "晚餐" else "工资",
                        categoryParentName = if (i == 0) "食品餐饮" else null,
                        categoryIconKey = null,
                        targetAccountName = null,
                        excludeFromStats = false,
                    )
                )
            }
        }
    }

    private val budget = HomeViewModel.BudgetSummary(totalCents = 300000, spentCents = 2200)

    @androidx.compose.runtime.Composable
    private fun content(onAdd: () -> Unit = {}) = HomeContent(
        bookName = "日常账本",
        rows = rows,
        onAdd = onAdd,
        budgetSummary = budget,
    )

    private fun tx(
        id: String,
        date: LocalDate,
        type: TxType,
        cents: Long,
        name: String?,
        parent: String?,
        note: String?,
        hour: Int = 12,
        minute: Int = 30,
    ) = TxWithCategory(
        id = id,
        type = type,
        amountCents = cents,
        accountId = null,
        categoryId = null,
        dateEpochMinute = date.atTime(hour, minute).atZone(zone).toEpochSecond() / 60,
        note = note,
        categoryName = name,
        categoryParentName = parent,
        categoryIconKey = null,
        targetAccountName = null,
        excludeFromStats = false,
    )

    /** 本月一号 / 今天 / 上月一号 / 去年同一天：一张月卡一个月份，最后一张要带年份。 */
    private fun monthRows(): List<TxWithCategory> {
        val today = LocalDate.now(zone)
        val firstOfMonth = today.withDayOfMonth(1)
        return listOf(
            tx("cur-1", firstOfMonth, TxType.EXPENSE, 1200, "晚餐", "食品餐饮", "本月一号", hour = 19, minute = 30),
            tx("cur-2", today, TxType.INCOME, 300000, "工资", null, null, hour = 8, minute = 5),
            tx("prev-1", firstOfMonth.minusMonths(1), TxType.EXPENSE, 4500, "打车", "出行交通", null),
            tx("old-1", firstOfMonth.minusYears(1), TxType.EXPENSE, 9900, "夜宵", "食品餐饮", "去年的账"),
        )
    }

    private fun monthLabel(date: LocalDate): String = "${date.year}/${date.monthValue}"

    /** 悬浮入口的实测宽度（dp）；展开 = 通栏胶囊、收起 = 56dp 正圆。 */
    private fun entryWidth(): Float =
        composeRule.onNodeWithTag(HOME_ADD_TAG).getUnclippedBoundsInRoot().width.value

    @Test
    fun captureHomeTop() {
        composeRule.setContent { PennedTheme { content() } }
        // 本月卡头部：月汇总三列 + 预算块（预算/已用/剩余 + 进度条）
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("收入").assertIsDisplayed()
        composeRule.onNodeWithText("结余").assertIsDisplayed()
        composeRule.onNodeWithText("预算").assertIsDisplayed()
        composeRule.onNodeWithText("已用").assertIsDisplayed()
        composeRule.onNodeWithText("剩余").assertIsDisplayed()
        // 悬浮入口：通栏胶囊、贴右下（右 16 / 下 16）
        composeRule.onNodeWithText("添加一条新记账").assertIsDisplayed()
        assertEquals(expandedEntryWidth, entryWidth(), 0.5f)
        val bounds = composeRule.onNodeWithTag(HOME_ADD_TAG).getUnclippedBoundsInRoot()
        assertEquals(384f, bounds.right.value, 0.5f)
        assertEquals(784f, bounds.bottom.value, 0.5f)
        // 当天日头条：相对词在最前（参考图口径）；行内时段
        composeRule.onNodeWithText("今天", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("12:30").onFirst().assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_home_top.png")
    }

    @Test
    fun captureMonthCards() {
        composeRule.setContent { PennedTheme { HomeContent(bookName = "日常账本", rows = monthRows(), onAdd = {}) } }
        val today = LocalDate.now(zone)
        // 月标题行：只显示 yyyy/m，点它折叠/展开
        composeRule.onNodeWithText(monthLabel(today)).assertIsDisplayed()
        // 年只在月标题里以 yyyy/m 出现——日头条的日期不带年份（本年）
        composeRule.onNodeWithText("${today.year}年", substring = true).assertDoesNotExist()
        // 当日收入不为 0 → 日头条带「收」
        composeRule.onNodeWithText("收 3,000.00", substring = true).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/ag_home_month_cards.png")
    }

    @Test
    fun monthCardCollapsesAndExpands() {
        composeRule.setContent { PennedTheme { HomeContent(bookName = "日常账本", rows = monthRows(), onAdd = {}) } }
        val today = LocalDate.now(zone)
        // 默认：本月展开（有汇总三列），上月折叠（它的合计不在界面上）
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("45.00").assertDoesNotExist()

        // 点本月标题 → 折叠：汇总与预算块整块收起
        composeRule.onNodeWithText(monthLabel(today)).performClick()
        composeRule.onNodeWithText("支出").assertDoesNotExist()
        composeRule.onNodeWithText("收入").assertDoesNotExist()
        // 流水行与日头条照旧（折叠只收起卡片头部那一块）
        composeRule.onNodeWithText("今天", substring = true).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/aq_home_months_collapsed.png")

        // 点上月标题 → 展开，显示的是**那个月**的合计（支 45.00 / 收 0）
        val previous = today.withDayOfMonth(1).minusMonths(1)
        composeRule.onNodeWithText(monthLabel(previous)).performClick()
        composeRule.onNodeWithText("45.00").assertIsDisplayed()
        composeRule.onNodeWithText("支出").assertIsDisplayed()
    }

    @Test
    fun currentMonthCardShowsBudgetEvenWithoutRows() {
        // 本月还没记账：本月卡仍然在（月汇总 + 预算块 = 预算管理页的唯一入口）
        val today = LocalDate.now(zone)
        val previous = today.withDayOfMonth(1).minusMonths(1)
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = listOf(tx("prev-1", previous, TxType.EXPENSE, 4500, "打车", "出行交通", null)),
                    onAdd = {},
                    budgetSummary = budget,
                )
            }
        }
        composeRule.onNodeWithText(monthLabel(today)).assertIsDisplayed()
        composeRule.onNodeWithText("预算").assertIsDisplayed()
        composeRule.onNodeWithText("已用").assertIsDisplayed()
        composeRule.onNodeWithText("剩余").assertIsDisplayed()
    }

    @Test
    fun crossYearDayHeaderCarriesYear() {
        composeRule.setContent { PennedTheme { HomeContent(bookName = "日常账本", rows = monthRows(), onAdd = {}) } }
        val lastYearDay = LocalDate.now(zone).withDayOfMonth(1).minusYears(1)
        // 去年的那张卡：日头条带 yyyy年（截图基线见 captureMonthCards）
        composeRule.onNodeWithTag("home-list")
            .performScrollToNode(hasText("${lastYearDay.year}年", substring = true))
        composeRule.onNodeWithText(
            "${lastYearDay.year}年${lastYearDay.monthValue}月${lastYearDay.dayOfMonth}日",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun dayHeaderHidesZeroIncome() {
        val day = LocalDate.now(zone)
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = listOf(tx("only-expense", day, TxType.EXPENSE, 1800, "晚餐", "食品餐饮", null)),
                    onAdd = {},
                )
            }
        }
        // 只有支出的一天：整条日头条就是「支 18.00」——精确匹配即证明没有「收」那一段
        composeRule.onNodeWithText("支 18.00").assertIsDisplayed()
    }

    @Test
    fun addEntryCollapsesOnScrollAndRestoresOnlyAtTop() {
        var added = 0
        composeRule.setContent { PennedTheme { content(onAdd = { added++ }) } }
        assertEquals(expandedEntryWidth, entryWidth(), 0.5f)

        // 往下看：收成右下角「＋」
        composeRule.onNodeWithTag("home-list").performTouchInput { swipeUp() }
        assertEquals(compactEntryWidth, entryWidth(), 0.5f)
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/aq_home_scrolled.png")

        // 往回滚一小段（离顶部还远）：保持「＋」——UI-10 修的就是这里（原来会弹回通栏胶囊）
        composeRule.onNodeWithTag("home-list").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 200f, durationMillis = 400)
        }
        assertEquals(compactEntryWidth, entryWidth(), 0.5f)

        // 回到列表顶部：才复原成通栏胶囊
        composeRule.onNodeWithTag("home-list").performTouchInput { swipeDown() }
        composeRule.onNodeWithTag("home-list").performTouchInput { swipeDown() }
        assertEquals(expandedEntryWidth, entryWidth(), 0.5f)

        // 两种形态都是同一个节点，点它就是记一笔
        composeRule.onNodeWithTag(HOME_ADD_TAG).performClick()
        assertEquals(1, added)
    }

    @Test
    fun captureBudgetOverspent() {
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = rows,
                    onAdd = {},
                    // 已用 > 预算：剩余显示红色负数、进度条转红
                    budgetSummary = HomeViewModel.BudgetSummary(totalCents = 100000, spentCents = 123456),
                )
            }
        }
        composeRule.onNodeWithText("-234.56").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_home_budget_overspent.png")
    }

    /**
     * 回归（AQ 轮用户实测报的 bug）：预算块曾经带 16dp 圆角裁剪，而进度条压在块的底边上，
     * 16dp 底角把条的两端连同"已用 > 0"的那点绿色一起裁掉——0.73% 的填充全落在被裁的 23px 里，
     * 看着就像一分钱没花。断言：进度条区域里**有绿色像素**，且**紧贴条的左端**。
     *
     * 取像素走 Roborazzi 落盘再由宿主 JVM 读回来——`captureToImage()` 在 Robolectric 下会卡在
     * 窗口重绘（PixelCopy 永远等不到 idle）。
     */
    @Test
    fun budgetBarDrawsGreenAtTinyUsage() {
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = rows,
                    onAdd = {},
                    // 22.00 / 3,000.00 = 0.73%
                    budgetSummary = HomeViewModel.BudgetSummary(totalCents = 300000, spentCents = 2200),
                )
            }
        }
        val scale = composeRule.density.density
        val bar = composeRule.onNodeWithTag(HOME_BUDGET_BAR_TAG).getUnclippedBoundsInRoot()
        val left = (bar.left.value * scale).toInt()
        val right = (bar.right.value * scale).toInt()
        val top = (bar.top.value * scale).toInt()
        val bottom = (bar.bottom.value * scale).toInt()

        val path = "build/test-results/roborazzi/aq_budget_bar_probe.png"
        composeRule.onRoot().captureRoboImage(path)
        val bitmap = requireNotNull(android.graphics.BitmapFactory.decodeFile(path)) { "读不到 $path" }

        // 主色（青绿）的判据：绿通道明显高于红通道；浅青日头条与灰轨道都过不了这一关
        fun isGreen(x: Int, y: Int): Boolean {
            val rgb = bitmap.getPixel(x, y)
            return ((rgb shr 8) and 0xFF) - ((rgb shr 16) and 0xFF) > 38
        }
        val greenColumns = (left until right).filter { x -> (top until bottom).any { y -> isGreen(x, y) } }
        assertTrue("进度条里一个绿像素都没有（条 $left..$right × $top..$bottom）", greenColumns.isNotEmpty())
        assertTrue(
            "绿色没贴着条的左端（最左绿 x=${greenColumns.first()}，条左 x=$left）",
            greenColumns.first() - left <= 2,
        )
    }

    @Test
    fun addEntryHiddenInSelectionMode() {
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = rows,
                    onAdd = {},
                    budgetSummary = budget,
                    selectionMode = true,
                    selectedIds = setOf("tx-1"),
                )
            }
        }
        // 多选模式：悬浮入口整块不渲染
        composeRule.onNodeWithText("已选 1 条").assertIsDisplayed()
        composeRule.onNodeWithTag(HOME_ADD_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("添加一条新记账").assertDoesNotExist()
    }
}
