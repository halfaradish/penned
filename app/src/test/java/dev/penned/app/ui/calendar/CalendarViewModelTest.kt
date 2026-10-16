package dev.penned.app.ui.calendar

import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历优化轮 ViewModel 的三条口径（真库、真状态）：翻月**选中日跟着走**（同号、该月没有就夹到月末）、
 * **不给未来月**（上下界与统计页同口径）、「今」回本月选今天。
 *
 * 直接读 `month` / `selected` / `months` 三个状态（不收集 `state`）：它是 `WhileSubscribed` 的 stateIn，
 * 不订阅就只有初值——对着它断言是**假通过**（M3 的老坑）；UI 侧的接线另有 `CalendarPagerTest`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class CalendarViewModelTest {

    private val app: PennedApplication = ApplicationProvider.getApplicationContext()

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun `翻月跟随选中日：同号，该月没有这天就夹到月末`() {
        val vm = CalendarViewModel(app)
        // 先在 1 月点 31 号（那个月有 31 号；条里有 2026-01）
        vm.selectDay(day(2026, 1, 31))
        assertEquals(YearMonth.of(2026, 1), vm.month.value)
        assertEquals(day(2026, 1, 31), vm.selected.value)

        // 翻到 2 月：31 号没有 → 夹到 2/28（平年）
        vm.selectMonth(YearMonth.of(2026, 2))
        assertEquals(YearMonth.of(2026, 2), vm.month.value)
        assertEquals(day(2026, 2, 28), vm.selected.value)

        // 再翻到 3 月：号数已经是被夹过的 28（不是原来的 31）
        vm.selectMonth(YearMonth.of(2026, 3))
        assertEquals(day(2026, 3, 28), vm.selected.value)

        // 另一处月末夹取：3 月 31 号 → 4 月（30 天）夹到 4/30
        // （闰年 2 月那档用固定日期测：日历**不给未来月**，2028 年从"今天"翻不过去 → 纯函数测里覆盖）
        vm.selectDay(day(2026, 3, 31))
        vm.selectMonth(YearMonth.of(2026, 4))
        assertEquals(day(2026, 4, 30), vm.selected.value)

        // 同号能留住的照旧：4 月 15 号 → 翻到 6 月还是 15 号
        vm.selectDay(day(2026, 4, 15))
        vm.selectMonth(YearMonth.of(2026, 6))
        assertEquals(day(2026, 6, 15), vm.selected.value)
    }

    @Test
    fun `上下界：不给未来月，往回最多到条的头（兜底最近 12 个月）`() {
        val today = YearMonth.now()
        val vm = CalendarViewModel(app)
        val months = vm.months.value
        assertEquals("第 0 页恒是本月", today, months.first())
        assertEquals("没有账时兜底最近 12 个月", 12, months.size)

        // 往未来：夹回本月（一个未来月都不给）
        vm.selectMonth(today.plusMonths(1))
        assertEquals(today, vm.month.value)
        vm.selectMonth(today.plusMonths(30))
        assertEquals(today, vm.month.value)

        // 往回：上一月正常翻
        vm.selectMonth(today.minusMonths(1))
        assertEquals(today.minusMonths(1), vm.month.value)

        // 再往回 99 个月：夹到条的头（= 兜底下限），选中日也在那个月里
        vm.selectMonth(today.minusMonths(99))
        assertEquals(months.last(), vm.month.value)
        assertEquals(today.minusMonths(11), vm.month.value)
        assertEquals(vm.month.value, YearMonth.from(LocalDate.ofEpochDay(vm.selected.value)))
    }

    @Test
    fun `点到邻月的日子：跟着翻月并选中它（不按同号改写）`() {
        val vm = CalendarViewModel(app)
        val today = YearMonth.now()
        val lastDayOfPreviousMonth = today.minusMonths(1).atEndOfMonth()
        vm.selectDay(lastDayOfPreviousMonth.toEpochDay())
        assertEquals(today.minusMonths(1), vm.month.value)
        assertEquals("就是用户点的那一天", lastDayOfPreviousMonth.toEpochDay(), vm.selected.value)
    }

    @Test
    fun `今：回本月 + 选中今天`() {
        val today = LocalDate.now()
        val vm = CalendarViewModel(app)
        vm.selectMonth(YearMonth.from(today).minusMonths(2))
        assertNotEquals(today.toEpochDay(), vm.selected.value)

        vm.goToday()
        assertEquals(YearMonth.from(today), vm.month.value)
        assertEquals(today.toEpochDay(), vm.selected.value)
    }
}
