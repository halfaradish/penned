package dev.penned.app.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import dev.penned.app.ui.theme.PennedSpacing
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
import kotlin.math.abs

/**
 * 日历优化轮 2 的**交互**：左右滑切月、点标题选年月跳转、**日流水跟手覆盖**（拖动 1:1 收缩、
 * 松手吸附、回顶才展开）。
 *
 * 收起态不再有"周条"这个第二形态（pager 常驻），所以"收满了没有"按 **几何** 断言：
 * 网格视口的高度 == 一行高（`CalendarStyle.rowHeight` 那条公式，测试里不另写一个比例）。
 * 手势一律 `performTouchInput`（与 `StatsPagerTest` 同款）；进度换算/吸附/几何本身另有纯函数单测。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarPagerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val month = YearMonth.of(2026, 10)   // 2026-10：5 行网格（9/28 ~ 11/1）
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

    /** 更长的列表：用来验"往回滚一点点不弹开"——12 条一划就见顶，验不出"离顶还远"。 */
    private fun longRows(): List<TxWithCategory> = (1..30).map { row("t$it", it * 100L, 8) }

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
        initialProgress: Float = 0f,
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
                    initialProgress = initialProgress,
                )
            }
        }
    }

    // —— 几何断言的小工具：一行高（px）用生产那条公式算，测试里不另写一个比例 ——

    private fun rowHeightPx(): Float = with(composeRule.density) {
        val pageWidth = composeRule.onRoot().fetchSemanticsNode().size.width.toDp()
        CalendarStyle.rowHeight((pageWidth - PennedSpacing.sm * 2) / 7).toPx()
    }

    private fun gridHeightPx(): Float =
        composeRule.onNodeWithTag(CALENDAR_GRID_TAG).fetchSemanticsNode().size.height.toFloat()

    private fun gridTopPx(): Float =
        composeRule.onNodeWithTag(CALENDAR_GRID_TAG).fetchSemanticsNode().boundsInRoot.top

    private fun topOf(dayOfMonth: String): Float =
        composeRule.onAllNodesWithText(dayOfMonth).onFirst().fetchSemanticsNode().boundsInRoot.top

    private fun bottomOf(dayOfMonth: String): Float =
        composeRule.onAllNodesWithText(dayOfMonth).onFirst().fetchSemanticsNode().boundsInRoot.bottom

    private fun gridRows(): Float = gridHeightPx() / rowHeightPx()

    /** 等视口稳定到 rows 行（±2px：布局取整）——"收满了 / 弹回了"都按它断言。 */
    private fun waitGridRows(rows: Int) {
        val want = rowHeightPx() * rows
        composeRule.waitUntil(5_000) { abs(gridHeightPx() - want) < 2f }
    }

    private fun assertGridRows(rows: Int, label: String) {
        val want = rowHeightPx() * rows
        assertTrue(
            "$label：视口该约 $rows 行（%.1f px），实际 %.1f px".format(want, gridHeightPx()),
            abs(gridHeightPx() - want) < 2f,
        )
    }

    private fun collapsed(): Boolean = countOfDesc("展开日历") >= 1

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

    /** 上滑收起成一周；收满后列表才轮到滚动——列表仍可滚、可点进详情。 */
    @Test
    fun swipeUpCollapsesThenListKeepsWorking() {
        var opened = ""
        host(items = manyRows(), onDetail = { opened = it })
        assertGridRows(5, "展开态")
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).performTouchInput { swipeUp() }
        waitGridRows(1)
        // 可点入口的文案翻成"展开日历"
        composeRule.onNodeWithContentDescription("展开日历").assertIsDisplayed()
        // 收起态显示的正是**选中日（10/8，周四）所在的那一周**（10/5 ~ 10/11）：
        // 那一行贴住视口顶、上一行（9/28 那行）被裁到视口外
        assertTrue("本周行该落在视口第一行里", topOf("5") < gridTopPx() + rowHeightPx())
        assertTrue("上一行该被裁掉（%.1f vs %.1f）".format(bottomOf("28"), gridTopPx()), bottomOf("28") <= gridTopPx() + 1f)
        listOf("5", "6", "7", "8", "9", "10", "11").forEach { assertTrue("收起那一行缺 $it", countOf(it) >= 1) }
        capture("m3_calendar_week")

        // 收起后当日列表仍可滚动（日头条被滚上去）
        val before = composeRule.onAllNodesWithText("食品餐饮").onFirst().fetchSemanticsNode().boundsInRoot.top
        composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val after = composeRule.onAllNodesWithText("食品餐饮").onFirst().fetchSemanticsNode().boundsInRoot.top
        assertTrue("收起态才轮到列表滚（收起前位移归日历）", after < before)
        assertTrue("滚列表不会把日历弹开", collapsed())

        // 点一行 → 进详情
        composeRule.onAllNodesWithText("食品餐饮").onFirst().performClick()
        composeRule.waitForIdle()
        assertTrue("收起后行仍可点进详情", opened.isNotEmpty())
    }

    /** 回顶才展开（UI-10 的口径）：列表滚下去时往回滚不弹开，回到顶才复原。 */
    @Test
    fun expandsBackOnlyAtListTop() {
        host(items = longRows())
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).performTouchInput { swipeUp() }
        waitGridRows(1)
        // 收起后把列表滚下去（30 条够长，一划不会见顶）
        composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        // 往回滚一点点（离顶还远）→ 不弹开：面板照旧一行高
        val listHeight = composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG).fetchSemanticsNode().size.height.toFloat()
        composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG)
            .performTouchInput { swipeDown(startY = listHeight * 0.3f, endY = listHeight * 0.5f) }
        composeRule.waitForIdle()
        assertGridRows(1, "没到顶不该展开")
        // 一直滚回顶部再继续下拉 → 展开（视口回到整月）
        repeat(6) {
            composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG).performTouchInput { swipeDown() }
            composeRule.waitForIdle()
        }
        waitGridRows(5)
        assertTrue("展开后入口文案回到「收起日历」", countOfDesc("收起日历") >= 1)
    }

    /**
     * 收起后在**网格上**往下滑也能展开（列表为空时列表不可滚，只能靠这一枚手势）。
     * 起点在那一行里、但手指要划够半个行程——收起的网格只有一行高，所以终点落到网格外面
     * （真实手指本来就能划出节点，`performTouchInput` 的终点不必受节点高度限制）。
     */
    @Test
    fun swipeDownOnGridExpands() {
        host()
        composeRule.onNodeWithContentDescription("收起日历").performClick()
        waitGridRows(1)
        val span = rowHeightPx() * 4f
        composeRule.onNodeWithTag(CALENDAR_GRID_TAG).performTouchInput {
            down(Offset(center.x, 4f))
            moveBy(Offset(0f, span * 0.75f))
            up()
        }
        waitGridRows(5)
    }

    /** 当天没记录（列表整块是空态）时，白卡里那块空态也能上滑收起 / 在顶下滑展开。 */
    @Test
    fun emptyDayAreaAlsoCollapsesAndExpands() {
        host(items = emptyList())
        // 空态区域上滑 → 收起（在整块空态上划，不是在那一行小字上）
        composeRule.onNodeWithTag(CALENDAR_EMPTY_TAG).performTouchInput { swipeUp() }
        waitGridRows(1)
        // 在顶下滑 → 展开
        composeRule.onNodeWithTag(CALENDAR_EMPTY_TAG).performTouchInput { swipeDown() }
        waitGridRows(5)
    }

    /** 短列表（真实数据一天只有 1~3 条、列表根本滚不动）：往列表上滑也要能跟手收起。 */
    @Test
    fun shortListStillCollapsesByFollow() {
        host(items = listOf(row("t1", 600L, 8)))
        assertGridRows(5, "展开态")
        composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG).performTouchInput { swipeUp() }
        waitGridRows(1)
        assertTrue("收满后入口文案是「展开日历」", collapsed())
    }

    /** 收起态横滑照样**切月**（pager 常驻，不是"按周翻页"那套第二语义）。 */
    @Test
    fun swipeOnCollapsedPagerChangesMonth() {
        host()
        composeRule.onNodeWithContentDescription("收起日历").performClick()
        waitGridRows(1)
        // 往右拉 = 上一月
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).performTouchInput { swipeRight() }
        composeRule.waitUntil(5_000) { countOf("2026年9月") >= 1 }
        composeRule.onNodeWithText("2026年9月").assertIsDisplayed()
        assertTrue("收起态没被弹开", collapsed())
        // 往左拉 = 回本月（"未来月不给"由 ViewModel 的 selectMonth 夹住——CalendarViewModelTest.上下界 覆盖；
        //  这里的 host 是个笨 fixture，原样应用回调，不重复实现夹取）
        composeRule.onNodeWithTag(CALENDAR_PAGER_TAG).performTouchInput { swipeLeft() }
        composeRule.waitUntil(5_000) { countOf("2026年10月") >= 1 }
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
    }

    /** 可点入口也能收起/展开（手势之外的第二条路），走的是同一根进度。 */
    @Test
    fun toggleButtonCollapsesAndExpands() {
        host()
        composeRule.onNodeWithContentDescription("收起日历").performClick()
        waitGridRows(1)
        composeRule.onNodeWithContentDescription("展开日历").performClick()
        waitGridRows(5)
    }

    /**
     * 拖到中途**不松手**：视口高度就停在"拖了多少"那一档（跟手，不是等松手才跳）。
     * 手指划掉半个行程 → 视口缩掉半个行程（±半行：滑动起手要过 touchSlop，那一小段不算位移）。
     */
    @Test
    fun halfwayDragFollowsFingerWithoutSnap() {
        host(items = manyRows())
        val span = rowHeightPx() * 4f            // 展开高（5 行）− 一行 = 4 行
        composeRule.onNodeWithTag(CALENDAR_GRID_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, -span / 2f))       // 还没松手
        }
        composeRule.waitForIdle()
        val rows = gridRows()
        assertTrue("划一半该停在 3 行附近，实际 %.2f 行".format(rows), rows > 2.5f && rows < 3.6f)
    }

    /** 慢慢拖到 1/4 松手 → 没过半：按位置吸附，弹回展开。 */
    @Test
    fun slowPartialDragSnapsBackToExpanded() {
        host(items = manyRows())
        val span = rowHeightPx() * 4f
        composeRule.onNodeWithTag(CALENDAR_GRID_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, -span * 0.25f), delayMillis = 200)   // 慢拖（速度不到阈值）
            up()
        }
        waitGridRows(5)
    }

    /**
     * 快速上甩：位置没过半，但**速度方向明确** → 收满（"甩一下"不该被位置拖住）。
     *
     * 注意：`VelocityTracker` 要**至少 3 个按下的采样点**才算得出速度（2 个直接回 0）——
     * 真机上手指一动就是一串 move 事件，测试里得自己凑够（所以拆成两次 moveBy）。
     */
    @Test
    fun quickFlickCollapsesEvenUnderHalf() {
        host(items = manyRows())
        val span = rowHeightPx() * 4f
        composeRule.onNodeWithTag(CALENDAR_GRID_TAG).performTouchInput {
            down(center)
            // 一小段（但要过 touchSlop，不然手势压根没起）、很快：位置没过半、速度很明确
            moveBy(Offset(0f, -span * 0.20f), delayMillis = 8)
            moveBy(Offset(0f, -span * 0.15f), delayMillis = 8)
            up()
        }
        waitGridRows(1)
    }

    /**
     * 动画中间帧（留档"逐渐覆盖"的观感）：给无状态入口一个初始进度 0.5——
     * 面板与网格都停在"一半"（拖动过程见 [halfwayDragFollowsFingerWithoutSnap]）。
     */
    @Test
    fun captureHalfwayProgress() {
        host(items = manyRows(), initialProgress = 0.5f)
        // 5 行 − p×4 行 = 3 行
        assertGridRows(3, "p = 0.5")
        // 本周行（第 1 行，10/5~10/11）随内容上移：还落在视口第一行里（上移了半行）
        assertTrue("本周行该落进视口第一行（%.1f vs %.1f）".format(topOf("5"), gridTopPx()), topOf("5") < gridTopPx() + rowHeightPx())
        capture("m3_calendar_halfway")
    }

    /**
     * 白框面板贴住网格视口下缘；**p < 1 期间面板内容不滚**（这一路上的位移全归收起，收满才轮到列表）。
     *
     * 所以这里拖到"还没收满"就不松手——一旦收满，剩下的位移就该给列表滚（另一条口径，见下面一条断言）。
     */
    @Test
    fun panelHugsGridAndDoesNotScrollWhileCollapsing() {
        host(items = manyRows(), initialProgress = 0.4f)
        val panelTop = composeRule.onNodeWithTag(CALENDAR_PANEL_TAG).fetchSemanticsNode().boundsInRoot.top
        val gridBottom = composeRule.onNodeWithTag(CALENDAR_GRID_TAG).fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("面板该贴住网格下缘（%.1f vs %.1f）".format(panelTop, gridBottom), abs(panelTop - gridBottom) < 2f)
        val offsetBefore = composeRule.onAllNodesWithText("食品餐饮").onFirst()
            .fetchSemanticsNode().boundsInRoot.top - panelTop
        // 拖掉行程的 30%（p 从 0.4 → 0.7，还不到 1）不松手：面板跟着顶上来，列表一格没滚
        val span = rowHeightPx() * 4f
        composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG).performTouchInput {
            down(Offset(center.x, 20f))
            moveBy(Offset(0f, -span * 0.3f))
        }
        composeRule.waitForIdle()
        assertTrue("这一拖该只收到 0.7 左右（视口 %.2f 行）".format(gridRows()), gridRows() > 1.5f)
        val panelTopAfter = composeRule.onNodeWithTag(CALENDAR_PANEL_TAG).fetchSemanticsNode().boundsInRoot.top
        assertTrue("面板该跟着视口上移（%.1f → %.1f）".format(panelTop, panelTopAfter), panelTopAfter < panelTop - 2f)
        val offsetAfter = composeRule.onAllNodesWithText("食品餐饮").onFirst()
            .fetchSemanticsNode().boundsInRoot.top - panelTopAfter
        assertEquals("收完之前列表内容不该滚动", offsetBefore, offsetAfter, 1f)
        // 同一枚手势继续拖：收满之后**剩下**的位移才轮到列表滚（"先收起、再滚动"）
        composeRule.onNodeWithTag(CALENDAR_DAY_LIST_TAG).performTouchInput {
            moveBy(Offset(0f, -span * 1.2f))
        }
        composeRule.waitForIdle()
        assertGridRows(1, "这一拖该收满")
        val panelTopFull = composeRule.onNodeWithTag(CALENDAR_PANEL_TAG).fetchSemanticsNode().boundsInRoot.top
        val offsetFull = composeRule.onAllNodesWithText("食品餐饮").onFirst()
            .fetchSemanticsNode().boundsInRoot.top - panelTopFull
        assertTrue("收满之后剩余的位移该给列表滚（%.1f → %.1f）".format(offsetAfter, offsetFull), offsetFull < offsetAfter - 5f)
    }

    /** 空月也一样：视口收缩只跟行数与进度有关，不依赖有没有账。 */
    @Test
    fun emptyMonthCollapsesSameWay() {        host(items = emptyList())
        composeRule.onNodeWithContentDescription("收起日历").performClick()
        waitGridRows(1)
        assertGridRows(1, "空月收起")
    }

    /** 收起那一行仍是**同一套格子语言**（跨月周里邻月日子照常显示、按灰字去强调）。 */
    @Test
    fun collapsedRowKeepsCellLanguage() {
        // 选中 10/1（周四）→ 收起后那一行 = 9/28 ~ 10/4（6 天是 9 月，灰字）
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
        waitGridRows(1)
        // 跨月那一周：9/28 ~ 10/4（两端都在）
        listOf("28", "29", "30", "1", "2", "3", "4").forEach { assertTrue("收起那一行缺 $it", countOf(it) >= 1) }
        capture("m3_calendar_week_cross_month")
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    private fun countOf(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun countOfDesc(desc: String): Int =
        composeRule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().size
}
