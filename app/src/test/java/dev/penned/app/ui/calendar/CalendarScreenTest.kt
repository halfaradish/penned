package dev.penned.app.ui.calendar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.CalendarMath
import dev.penned.app.domain.TxType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.home.HomeGroups
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme
import dev.penned.app.ui.theme.ThemeAccent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * 日历视图（优化轮）：月网格渲染（**热力底 + 月小字总结**）+ 点选看当日流水 + 「今」 + 真库真导航；
 * 另有一条**口径回归**：日历的每日合计与首页日头条用的是同一份 `HomeGroups.groupByDay`，
 * 月总结用的是"只算当月"的 `HomeGroups.monthTotalsOf`（网格带出的邻月流水不许算进来）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private val today = LocalDate.of(2026, 10, 15)
    private val month = YearMonth.of(2026, 10)

    private fun day(dayOfMonth: Int) = LocalDate.of(2026, 10, dayOfMonth).toEpochDay()

    private fun row(id: String, type: TxType, cents: Long, dayOfMonth: Int, category: String?) = TxWithCategory(
        id = id, type = type, amountCents = cents, accountId = null, categoryId = null,
        dateEpochMinute = LocalDate.of(2026, 10, dayOfMonth).atTime(12, 30)
            .atZone(java.time.ZoneId.systemDefault()).toEpochSecond() / 60,
        note = null, categoryName = category, categoryParentName = null, categoryIconKey = null,
        targetAccountName = null, excludeFromStats = false, refundCents = 0, discountCents = 0,
    )

    private fun state(selected: Long, items: List<TxWithCategory>) = CalendarViewModel.UiState(
        month = month,
        selectedDay = selected,
        dayTotals = HomeGroups.groupByDay(items).associateBy { it.epochDay },
        selectedItems = items.filter {
            HomeGroups.groupByDay(items).any { g -> g.epochDay == selected && g.items.contains(it) }
        },
        monthTotals = HomeGroups.monthTotalsOf(HomeGroups.groupByDay(items), month),
    )

    /** 月网格：标题、星期表头、当日小计（支/收）、月小字总结、选中那天的流水。 */
    @Test
    fun captureCalendarMonth() {
        val items = listOf(
            row("t1", TxType.EXPENSE, 600L, 8, "食品餐饮"),
            row("t2", TxType.INCOME, 300_000L, 4, "工资"),
        )
        composeRule.setContent {
            PennedTheme { CalendarContent(state = state(day(8), items), today = today) }
        }
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
        // 星期表头（周一开头）
        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
            assertTrue("星期表头缺 $it", count(it) >= 1)
        }
        // 选中那天（10/8）的日头条 + 流水行
        composeRule.onNodeWithText("10月8日 周四").assertIsDisplayed()
        composeRule.onNodeWithText("食品餐饮").assertIsDisplayed()
        // 有账的两天在格子里给了小计（金额不带符号与千分位；支/收由颜色区分）
        assertTrue(count("6.00") >= 1)
        assertTrue(count("3000.00") >= 1)
        // 月小字总结：收/支/余三个数（带千分位；余 = 收 − 支）。
        // 金额串可能与日头条/格子重复（选中那天的小计就是"支 6.00"）→ 一律取第一个节点断言
        composeRule.onAllNodesWithText("收 3,000.00").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("支 6.00").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("余 2,994.00").onFirst().assertIsDisplayed()
        // 网格带出上/下月的日子（9/28 起、11/1 止）
        assertTrue(count("28") >= 1)
        capture("m3_calendar_month")
    }

    /** 点别的日子：换选中 + 当日流水跟着换（没有记录给空态）。 */
    @Test
    fun selectingDaySwitchesList() {
        val items = listOf(
            row("t1", TxType.EXPENSE, 600L, 8, "食品餐饮"),
            row("t2", TxType.INCOME, 300_000L, 4, "工资"),
        )
        val selected = androidx.compose.runtime.mutableStateOf(day(8))
        composeRule.setContent {
            PennedTheme {
                CalendarContent(
                    state = state(selected.value, items),
                    onSelectDay = { selected.value = it },
                    today = today,
                )
            }
        }
        composeRule.onNodeWithText("食品餐饮").assertIsDisplayed()
        // 点 10/4 → 列表换成那天（工资）
        composeRule.onNodeWithText("4").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("工资").assertIsDisplayed()
        composeRule.onNodeWithText("食品餐饮").assertDoesNotExist()
        // 点一个没账的日子（10/20）→ 空态
        composeRule.onNodeWithText("20").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("10月20日 周二 没有记录").assertIsDisplayed()
    }

    /** 「今」回到本月 + 选中今天；翻月按钮换月（标题跟着变）。 */
    @Test
    fun todayAndMonthShift() {
        val monthState = androidx.compose.runtime.mutableStateOf(month)
        val selected = androidx.compose.runtime.mutableStateOf(day(8))
        composeRule.setContent {
            PennedTheme {
                CalendarContent(
                    state = CalendarViewModel.UiState(month = monthState.value, selectedDay = selected.value),
                    onShiftMonth = { delta -> monthState.value = CalendarMath.shiftMonth(monthState.value, delta) },
                    onToday = {
                        monthState.value = YearMonth.from(today)
                        selected.value = today.toEpochDay()
                    },
                    today = today,
                )
            }
        }
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("下个月").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2026年11月").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("上个月").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
        // 「今」：本月 + 今天（今天那格带圈，标题是本月）
        composeRule.onNodeWithText("今").performClick()
        composeRule.waitForIdle()
        assertEquals(today.toEpochDay(), selected.value)
        assertEquals(YearMonth.from(today), monthState.value)
    }

    /**
     * 月小字总结的**边界**：网格会带出 9/30 与 11/1，它们的流水不许进 10 月的总结
     * （格子小计里照样有——那是格子自己的事）。
     */
    @Test
    fun monthSummaryExcludesNeighbourMonths() {
        val items = listOf(
            // 9/30 的 99.99 与 11/1 的 88.88 都在网格窗口里（网格里也会显示），但不进 10 月总结
            TxWithCategory(
                id = "n1", type = TxType.EXPENSE, amountCents = 9_999L, accountId = null, categoryId = null,
                dateEpochMinute = LocalDate.of(2026, 9, 30).atTime(20, 0)
                    .atZone(java.time.ZoneId.systemDefault()).toEpochSecond() / 60,
                note = null, categoryName = "上月的", categoryParentName = null, categoryIconKey = null,
                targetAccountName = null, excludeFromStats = false, refundCents = 0, discountCents = 0,
            ),
            TxWithCategory(
                id = "n2", type = TxType.INCOME, amountCents = 8_888L, accountId = null, categoryId = null,
                dateEpochMinute = LocalDate.of(2026, 11, 1).atTime(9, 0)
                    .atZone(java.time.ZoneId.systemDefault()).toEpochSecond() / 60,
                note = null, categoryName = "下月的", categoryParentName = null, categoryIconKey = null,
                targetAccountName = null, excludeFromStats = false, refundCents = 0, discountCents = 0,
            ),
            row("t2", TxType.INCOME, 300_000L, 4, "工资"),
            row("t1", TxType.EXPENSE, 600L, 8, "食品餐饮"),
        )
        composeRule.setContent {
            PennedTheme { CalendarContent(state = state(day(8), items), today = today) }
        }
        // 总结只算 10 月的两笔（"支 6.00" 与日头条同串 → 取第一个）
        composeRule.onAllNodesWithText("收 3,000.00").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("支 6.00").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("余 2,994.00").onFirst().assertIsDisplayed()
        // 邻月那两笔**没有**被算进去：算进去的话这两个串会出现
        assertEquals(0, count("支 105.99"))      // 6.00 + 99.99
        assertEquals(0, count("收 3,088.88"))    // 3,000.00 + 88.88
        assertEquals(0, count("余 2,982.89"))
        capture("m3_calendar_neighbour_months")
    }

    /** 空月：热力一格都没有、总结仍是 0.00 三项（行高不跳）。 */
    @Test
    fun captureCalendarEmptyMonth() {
        composeRule.setContent {
            PennedTheme { CalendarContent(state = state(day(8), emptyList()), today = today) }
        }
        composeRule.onAllNodesWithText("收 0.00").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("支 0.00").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("余 0.00").onFirst().assertIsDisplayed()
        // 空月的空态（选中那天没记录）
        composeRule.onNodeWithText("10月8日 周四 没有记录").assertIsDisplayed()
        capture("m3_calendar_empty_month")
    }

    /**
     * 叠加规则同屏：**热力 + 今天 + 选中**挤在同一格（今天就是选中那天、且那天有账）。
     * 这一格要同时看到：热力底、品牌描边（选中）、品牌实心圈（今天）。
     */
    @Test
    fun captureCalendarHeatWithTodaySelected() {
        val items = listOf(
            row("t1", TxType.EXPENSE, 600L, 15, "食品餐饮"),        // 今天 = 选中 = 有账（最低档）
            row("t2", TxType.EXPENSE, 120_000L, 20, "大额"),        // 顶档（标尺）
            row("t3", TxType.EXPENSE, 30_000L, 22, "中额"),         // 第 1 档（1/4 → 向上取整）
            row("t4", TxType.INCOME, 300_000L, 4, "工资"),          // 收入顶档
        )
        composeRule.setContent {
            PennedTheme { CalendarContent(state = state(day(15), items), today = today) }
        }
        // 日头条文案跟着**系统今天**走（mitigation：用 substring 断言日期那一段，不管前缀是今天/昨天）
        composeRule.onAllNodesWithText("10月15日 周四", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("收 3,000.00").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("支 1,506.00").onFirst().assertIsDisplayed()
        capture("m3_calendar_heat_stack")
    }

    /** 两套主题的浅/深各看一眼：热力与语义色不随主题变，选中/今天用品牌色（换色相别糊）。 */
    @Test
    fun captureCalendarSecondAccentLight() {
        val items = listOf(
            row("t1", TxType.EXPENSE, 600L, 8, "食品餐饮"),
            row("t2", TxType.INCOME, 300_000L, 4, "工资"),
        )
        composeRule.setContent {
            PennedTheme(accent = ThemeAccent.OCHRE) { CalendarContent(state = state(day(8), items), today = today) }
        }
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
        capture("m3_calendar_ochre")
    }

    @Test
    fun captureCalendarSecondAccentDark() {
        val items = listOf(
            row("t1", TxType.EXPENSE, 600L, 8, "食品餐饮"),
            row("t2", TxType.INCOME, 300_000L, 4, "工资"),
        )
        composeRule.setContent {
            PennedTheme(darkTheme = true, accent = ThemeAccent.INDIGO) {
                CalendarContent(state = state(day(8), items), today = today)
            }
        }
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
        capture("m3_calendar_indigo_dark")
    }

    /**
     * 真库真导航：日历 Tab → 网格上今天的小计 → 点今天看当日流水 → 点进详情。
     * 顺带钉住**口径**：格子上的小计 == `HomeGroups.groupByDay`（首页日头条的同一份函数）。
     */
    @Test
    fun calendarThroughNavMatchesHomeTotals() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val bookId = "book-calendar"
        val todayEpochDay = LocalDate.now().toEpochDay()
        runBlocking {
            val now = System.currentTimeMillis()
            app.database.bookDao().upsert(
                BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "t")
            )
            val minute = LocalDate.now().atTime(12, 30).atZone(java.time.ZoneId.systemDefault()).toEpochSecond() / 60
            app.database.transactionDao().upsert(
                TransactionEntity(
                    id = "c1", bookId = bookId, type = TxType.EXPENSE, amountCents = 1_600L,
                    dateEpochMinute = minute, creatorDeviceId = "t", createdAt = now, updatedAt = now, deviceId = "t",
                )
            )
            app.settings.setCurrentBook(bookId)
        }
        composeRule.setContent {
            PennedTheme {
                CompositionLocalProvider(LocalApp provides app) { PennedApp(app) }
            }
        }
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
        composeRule.onNodeWithText("日历").performClick()
        composeRule.waitUntil(5_000) { count(CalendarMath.monthTitle(YearMonth.now())) == 1 }
        // 今天那格的小计（支 16.00）
        composeRule.waitUntil(5_000) { count("16.00") >= 1 }
        // 月小字总结（本月只有这一笔 16.00）
        composeRule.waitUntil(5_000) { count("支 16.00") >= 1 }
        composeRule.onAllNodesWithText("收 0.00").onFirst().assertIsDisplayed()
        // 口径回归：日历的当日合计 == HomeGroups.groupByDay 的合计（首页日头条同一份函数）
        val rows = runBlocking { app.database.transactionDao().observeRangeTxsWithCategory(bookId, 0L, Long.MAX_VALUE).first() }
        val expected = HomeGroups.groupByDay(rows).first { it.epochDay == todayEpochDay }
        assertEquals(1_600L, expected.expenseCents)
        assertEquals(0L, expected.incomeCents)
        // 口径回归：月总结 == monthTotalsOf（只算当月）
        val totals = HomeGroups.monthTotalsOf(HomeGroups.groupByDay(rows), YearMonth.from(LocalDate.now()))
        assertEquals(1_600L, totals.expenseCents)

        // 点今天 → 当日流水 + 日头条
        composeRule.onNodeWithText("${LocalDate.now().dayOfMonth}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("-16.00").assertIsDisplayed()
        capture("m3_calendar_today")
    }
}
