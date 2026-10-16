package dev.penned.app.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.CalendarPages
import dev.penned.app.domain.TxType
import dev.penned.app.ui.home.HomeGroups
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
import java.time.YearMonth

/**
 * 日历优化轮的**交互**：左右滑切月、点标题选年月跳转、上滑收起成周视图 / 回顶展开。
 * 手势一律 `performTouchInput`（与 `StatsPagerTest` 同款）；收起/展开的判定逻辑本身另有纯函数单测。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarPagerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val month = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 15)

    private fun day(dayOfMonth: Int, m: YearMonth = month) =
        LocalDate.of(m.year, m.monthValue, dayOfMonth).toEpochDay()

    private fun row(id: String, cents: Long, dayOfMonth: Int, m: YearMonth = month) = TxWithCategory(
        id = id, type = TxType.EXPENSE, amountCents = cents, accountId = null, categoryId = null,
        dateEpochMinute = LocalDate.of(m.year, m.monthValue, dayOfMonth).atTime(12, 30)
            .atZone(java.time.ZoneId.systemDefault()).toEpochSecond() / 60,
        note = null, categoryName = "食品餐饮", categoryParentName = null, categoryIconKey = null,
        targetAccountName = null, excludeFromStats = false, refundCents = 0, discountCents = 0,
    )

    /** 12 条流水：列表够长才滚得动（收起后要验"列表还能滚、还能点进详情"）。 */
    private fun manyRows(): List<TxWithCategory> = (1..12).map { row("t$it", it * 100L, 8) }

    private fun state(m: YearMonth, selected: Long, items: List<TxWithCategory>) = CalendarViewModel.UiState(
        month = m,
        selectedDay = selected,
        dayTotals = HomeGroups.groupByDay(items).associateBy { it.epochDay },
        selectedItems = items,
        monthTotals = HomeGroups.monthTotalsOf(HomeGroups.groupByDay(items), m),
        months = CalendarPages.months(earliest = null, today = month),
    )

    /** 托管一棵树：外部只持"月份 + 选中日"两个状态，模拟 ViewModel（滑完停稳回写、外部改了再翻页）。 */
    private fun host(
        items: List<TxWithCategory> = emptyList(),
        onDetail: (String) -> Unit = {},
        onSelectMonth: (YearMonth) -> Unit = {},
    ) {
        val m = mutableStateOf(month)
        val selected = mutableStateOf(day(8))
        composeRule.setContent {
            PennedTheme {
                var current by m
                var day by selected
                CalendarContent(
                    state = state(current, day, items),
                    onSelectMonth = { current = it; onSelectMonth(it) },
                    onSelectDay = { day = it },
                    onOpenDetail = onDetail,
                    today = today,
                )
            }
        }
    }

    /** 往右拉 = 看更早的月（页序新→旧 + reverseLayout；邻页也带数据——网格窗口按上月~下月取）。 */
    @Test
    fun swipeRightGoesToOlderMonth() {
        // 9 月也放一笔：滑过去那页要有热力与小字（不能是一屏白格）
        host(items = listOf(row("a", 600L, 8), row("b", 300L, 12, YearMonth.of(2026, 9))))
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).performTouchInput { swipeRight() }
        composeRule.waitUntil(5_000) { countOf("2026年9月") >= 1 }
        composeRule.onNodeWithText("2026年9月").assertIsDisplayed()
        // 换月后：小字总结跟着换成 9 月（那笔 3.00）
        composeRule.onAllNodesWithText("支 3.00").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("收 0.00").onFirst().assertIsDisplayed()
        // 选中日仍停在 10/8（滑动不改选中——停稳后由 ViewModel 跟随，这里只验 UI 层的滑月）
        capture("m3_calendar_swipe")
    }

    /** 往左拉（朝未来）= 停在原地：**不给未来月**（账号里没有未来月的数据，翻过去只会是一屏空网格）。 */
    @Test
    fun swipeLeftAtTodayMonthStays() {
        host()
        repeat(2) {
            composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
        assertEquals("未来月不该进条", 0, countOf("2026年11月"))
    }

    /**
     * 点月份标题（尾部有可点暗示箭头）= 打开选年月弹窗。
     * 入口只做**接线**断言（弹窗窗口在 Robolectric 里不便交互/截图）：取消不改期、确定把当前年月回传；
     * 滚轮本身的改选与上下顺序由 `YearMonthPickerTest`（共用件）与 `CalendarPagesTest`（年月列口径）覆盖。
     */
    @Test
    fun tappingMonthTitleOpensPickerAndWiresConfirm() {
        val picked = mutableListOf<YearMonth>()
        host(onSelectMonth = { picked += it })
        composeRule.waitForIdle()
        // 挂载后 pager 会先把"停稳的那一页"回写一次（就是本月，ViewModel 里直接返回）——清掉再看用户动作
        picked.clear()
        composeRule.onNodeWithText("2026年10月").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选择年月").assertIsDisplayed()
        composeRule.onNodeWithText("确定").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertIsDisplayed()
        // 取消：原样退回，不回调
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()
        assertTrue("取消不该改期", picked.isEmpty())
        // 确定：把当前选中的年月回传（滚轮没动 → 就是当前月）
        composeRule.onNodeWithText("2026年10月").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(month), picked)
    }

    /** 上滑收起成一周；收起后有可点的展开入口（无障碍：手势不是唯一入口）+ 列表仍可滚、可点进详情。 */
    @Test
    fun swipeUpCollapsesToWeekThenListKeepsWorking() {
        var opened = ""
        host(items = manyRows(), onDetail = { opened = it })
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).assertExists()
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).performTouchInput { swipeUp() }
        composeRule.waitUntil(5_000) { countOfTag(CALENDAR_WEEK_STRIP_TAG) >= 1 }
        // 收起：周条在、月网格没了；入口文案翻成"展开日历"
        composeRule.onNodeWithTag(CALENDAR_WEEK_STRIP_TAG).assertExists()
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("展开日历").assertIsDisplayed()
        // 周条 = 选中日（10/8，周四）所在的一周：10/5 ~ 10/11（周一开头）
        listOf("5", "6", "7", "8", "9", "10", "11").forEach { assertTrue("周条缺 $it", countOf(it) >= 1) }
        capture("m3_calendar_week")

        // 收起后当日列表仍可滚动（日头条被滚上去）
        val before = composeRule.onAllNodesWithText("食品餐饮").onFirst().fetchSemanticsNode().boundsInRoot.top
        composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val after = composeRule.onAllNodesWithText("食品餐饮").onFirst().fetchSemanticsNode().boundsInRoot.top
        assertTrue("收起态才轮到列表滚（收起前位移归日历）", after < before)
        assertTrue("滚列表不会把日历弹开", countOfTag(CALENDAR_WEEK_STRIP_TAG) >= 1)

        // 点一行 → 进详情
        composeRule.onAllNodesWithText("食品餐饮").onFirst().performClick()
        composeRule.waitForIdle()
        assertTrue("收起后行仍可点进详情", opened.isNotEmpty())
    }

    /** 回顶才展开（UI-10 的口径）：列表滚下去时往回滚不弹开，回到顶才复原。 */
    @Test
    fun expandsBackOnlyAtListTop() {
        host(items = manyRows())
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).performTouchInput { swipeUp() }
        composeRule.waitUntil(5_000) { countOfTag(CALENDAR_WEEK_STRIP_TAG) >= 1 }
        // 收起后把列表滚下去
        composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        // 往回滚一点点（离顶还远）→ 不弹开：只清"往下看"的累计
        val listHeight = composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG).fetchSemanticsNode().size.height.toFloat()
        composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG)
            .performTouchInput { swipeDown(startY = listHeight * 0.3f, endY = listHeight * 0.5f) }
        composeRule.waitForIdle()
        assertEquals("没到顶不该展开", 1, countOfTag(CALENDAR_WEEK_STRIP_TAG))
        // 一直滚回顶部 → 展开（周条消失、月网格回来）
        repeat(4) {
            composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG).performTouchInput { swipeDown() }
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(5_000) { countOfTag(CALENDAR_WEEK_STRIP_TAG) == 0 }
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).assertExists()
    }

    /** 收起态在**周条上往下滑**也能展开（列表为空时列表不可滚，只能靠周条这枚手势）。 */
    @Test
    fun swipeDownOnWeekStripExpands() {
        host()
        composeRule.onNodeWithContentDescription("收起日历").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CALENDAR_WEEK_STRIP_TAG).assertExists()
        composeRule.onNodeWithTag(CALENDAR_WEEK_STRIP_TAG).performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CALENDAR_WEEK_STRIP_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).assertExists()
    }

    /** 收起态横滑照样**切月**（不是按周翻页）：周条跟着换成含选中日的那一周。 */
    @Test
    fun swipeOnCollapsedWeekStripChangesMonth() {
        host()
        composeRule.onNodeWithContentDescription("收起日历").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CALENDAR_WEEK_STRIP_TAG).assertExists()
        // 往右拉 = 上一月
        composeRule.onNodeWithTag(CALENDAR_WEEK_STRIP_TAG).performTouchInput { swipeRight() }
        composeRule.waitUntil(5_000) { countOf("2026年9月") >= 1 }
        composeRule.onNodeWithText("2026年9月").assertIsDisplayed()
        assertTrue("收起态没被弹开", countOfTag(CALENDAR_WEEK_STRIP_TAG) >= 1)
        // 往左拉 = 回本月（"未来月不给"由 ViewModel 的 selectMonth 夹住——CalendarViewModelTest.上下界 覆盖；
        //  这里的 host 是个笨 fixture，原样应用回调，不重复实现夹取）
        composeRule.onNodeWithTag(CALENDAR_WEEK_STRIP_TAG).performTouchInput { swipeLeft() }
        composeRule.waitUntil(5_000) { countOf("2026年10月") >= 1 }
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
    }

    /** 可点入口也能收起/展开（手势之外的第二条路）。 */
    @Test
    fun toggleButtonCollapsesAndExpands() {
        host()
        composeRule.onNodeWithContentDescription("收起日历").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CALENDAR_WEEK_STRIP_TAG).assertExists()
        composeRule.onNodeWithContentDescription("展开日历").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CALENDAR_WEEK_STRIP_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).assertExists()
    }

    /** 收起态周条的小字/热力仍按本色（跨月周里邻月日子变淡）。 */
    @Test
    fun weekStripKeepsCellLanguage() {
        // 选中 10/1（周四）→ 周条 = 9/28 ~ 10/4（6 天是 9 月，变淡）
        val selected = mutableStateOf(day(1))
        composeRule.setContent {
            PennedTheme {
                CalendarContent(
                    state = state(month, selected.value, listOf(row("a", 600L, 8))),
                    onSelectDay = { selected.value = it },
                    today = today,
                )
            }
        }
        composeRule.onNodeWithContentDescription("收起日历").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CALENDAR_WEEK_STRIP_TAG).assertExists()
        // 跨月那一周：9/28 ~ 10/4（两端都在）
        listOf("28", "29", "30", "1", "2", "3", "4").forEach { assertTrue("周条缺 $it", countOf(it) >= 1) }
        capture("m3_calendar_week_cross_month")
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    private fun countOf(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun countOfTag(tag: String): Int =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size
}
