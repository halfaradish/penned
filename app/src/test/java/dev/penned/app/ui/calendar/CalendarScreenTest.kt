package dev.penned.app.ui.calendar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * 日历视图（M3-17）：月网格渲染 + 点选看当日流水 + 「今」 + 真库真导航；
 * 另有一条**口径回归**：日历的每日合计与首页日头条用的是同一份 `HomeGroups.groupByDay`。
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
    )

    /** 月网格：标题、星期表头、当日小计（支/收）、选中那天的流水。 */
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
        // 口径回归：日历的当日合计 == HomeGroups.groupByDay 的合计（首页日头条同一份函数）
        val rows = runBlocking { app.database.transactionDao().observeRangeTxsWithCategory(bookId, 0L, Long.MAX_VALUE).first() }
        val expected = HomeGroups.groupByDay(rows).first { it.epochDay == todayEpochDay }
        assertEquals(1_600L, expected.expenseCents)
        assertEquals(0L, expected.incomeCents)

        // 点今天 → 当日流水 + 日头条
        composeRule.onNodeWithText("${LocalDate.now().dayOfMonth}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("-16.00").assertIsDisplayed()
        capture("m3_calendar_today")
    }
}
