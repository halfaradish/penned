package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历的日期计算（M3-17）表驱动：首日偏移（一周七天各来一个）、月天数（闰年 2 月）、
 * 网格行数（5/6 行都出现）、跨年翻月、上/下月日子的归属判断、窗口边界。
 */
class CalendarMathTest {

    @Test
    fun `首日偏移：周一到周日七种都能算对`() {
        // 实测：2026 年 6/9/7/4/1/5/2 月的 1 号分别是 周一/二/三/三/四/五/日
        val cases = listOf(
            YearMonth.of(2026, 6) to 0,
            YearMonth.of(2026, 9) to 1,
            YearMonth.of(2026, 7) to 2,
            YearMonth.of(2026, 4) to 2,
            YearMonth.of(2026, 1) to 3,
            YearMonth.of(2026, 5) to 4,
            YearMonth.of(2026, 2) to 6,
        )
        cases.forEach { (month, expected) ->
            assertEquals("$month", expected, CalendarMath.firstDayOffset(month))
            // 交叉验证：偏移 = (1 号的 ISO 星期 - 1)
            assertEquals(month.atDay(1).dayOfWeek.value - 1, CalendarMath.firstDayOffset(month))
        }
    }

    @Test
    fun `月天数与网格行数：闰年 2 月、5 行与 6 行都出现`() {
        assertEquals(29, CalendarMath.daysInMonth(YearMonth.of(2028, 2)))
        assertEquals(28, CalendarMath.daysInMonth(YearMonth.of(2026, 2)))
        assertEquals(31, CalendarMath.daysInMonth(YearMonth.of(2026, 10)))

        // 2026-02：1 号是周日（偏移 6）+ 28 天 = 34 → 5 行
        assertEquals(5, CalendarMath.rowCount(YearMonth.of(2026, 2)))
        assertEquals(35, CalendarMath.cellCount(YearMonth.of(2026, 2)))
        // 2026-08：1 号是周六（偏移 5）+ 31 天 = 36 → 6 行
        assertEquals(6, CalendarMath.rowCount(YearMonth.of(2026, 8)))
        assertEquals(42, CalendarMath.cellCount(YearMonth.of(2026, 8)))
        // 网格永远装得下当月所有天
        listOf(YearMonth.of(2026, 2), YearMonth.of(2028, 2), YearMonth.of(2026, 8), YearMonth.of(2026, 10))
            .forEach { month ->
                assertTrue("$month", CalendarMath.cellCount(month) >= CalendarMath.firstDayOffset(month) + CalendarMath.daysInMonth(month))
            }
    }

    @Test
    fun `网格：首格是当月 1 号所在周的周一、末格是周日、当月天一个不少`() {
        val month = YearMonth.of(2026, 10)   // 2026-10-01 是周四 → 首格 9/28（周一）
        val days = CalendarMath.gridDays(month)
        assertEquals(LocalDate.of(2026, 9, 28).toEpochDay(), days.first())
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), days.last())
        assertEquals(0, LocalDate.ofEpochDay(days.first()).dayOfWeek.value - 1)
        assertEquals(6, LocalDate.ofEpochDay(days.last()).dayOfWeek.value - 1)
        // 当月天全在网格里，且网格里恰好 31 天属于 10 月
        assertEquals(31, days.count { CalendarMath.inMonth(it, month) })
        assertEquals(1, days.count { it == LocalDate.of(2026, 10, 1).toEpochDay() })
    }

    @Test
    fun `归属判断与翻月：跨年、上下月日子`() {
        val month = YearMonth.of(2026, 1)
        assertTrue(CalendarMath.inMonth(LocalDate.of(2026, 1, 1).toEpochDay(), month))
        assertFalse(CalendarMath.inMonth(LocalDate.of(2025, 12, 31).toEpochDay(), month))
        assertFalse(CalendarMath.inMonth(LocalDate.of(2026, 2, 1).toEpochDay(), month))
        // 1 月的网格会带出 2025 年 12 月与 2026 年 2 月
        val days = CalendarMath.gridDays(month)
        assertTrue(days.any { !CalendarMath.inMonth(it, month) })

        assertEquals(YearMonth.of(2025, 12), CalendarMath.shiftMonth(month, -1))
        assertEquals(YearMonth.of(2026, 2), CalendarMath.shiftMonth(month, 1))
        assertEquals(YearMonth.of(2027, 1), CalendarMath.shiftMonth(month, 12))
        assertEquals(YearMonth.of(2025, 1), CalendarMath.shiftMonth(month, -12))
        assertEquals("2026年10月", CalendarMath.monthTitle(YearMonth.of(2026, 10)))
    }

    @Test
    fun `窗口：当月窗口两端都含、网格窗口包住带出的日子`() {
        val month = YearMonth.of(2026, 10)
        val (from, to) = CalendarMath.monthWindow(month)
        val fromDate = java.time.Instant.ofEpochSecond(from * 60).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val toDate = java.time.Instant.ofEpochSecond(to * 60).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        assertEquals(LocalDate.of(2026, 10, 1), fromDate)
        assertEquals(LocalDate.of(2026, 10, 31), toDate)

        val (gFrom, gTo) = CalendarMath.gridWindow(month)
        assertTrue("网格窗口比当月窗口宽", gFrom < from && gTo > to)
        val gFromDate = java.time.Instant.ofEpochSecond(gFrom * 60).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 28), gFromDate)
    }

    /** 翻月时选中日跟着走：同号；该月没有这天就夹到月末（含闰年 2 月）。 */
    @Test
    fun `翻月跟随选中日：同号、月末夹取、闰年 2 月`() {
        fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
        fun expect(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

        val cases = listOf(
            // 选中日、目标月、期望
            Triple(day(2026, 10, 8), YearMonth.of(2026, 11), expect(2026, 11, 8)),     // 普通跨月
            Triple(day(2026, 10, 31), YearMonth.of(2026, 11), expect(2026, 11, 30)),   // 31 天 → 30 天
            Triple(day(2026, 1, 31), YearMonth.of(2026, 2), expect(2026, 2, 28)),      // 平年 2 月
            Triple(day(2028, 1, 31), YearMonth.of(2028, 2), expect(2028, 2, 29)),      // 闰年 2 月
            Triple(day(2026, 3, 31), YearMonth.of(2026, 4), expect(2026, 4, 30)),      // 30 天的月
            Triple(day(2026, 12, 31), YearMonth.of(2027, 1), expect(2027, 1, 31)),     // 跨年
            Triple(day(2026, 10, 8), YearMonth.of(2026, 10), expect(2026, 10, 8)),     // 同月不动
        )
        cases.forEach { (selected, target, want) ->
            assertEquals("$selected → $target", want, CalendarMath.followDay(selected, target))
        }

        // 点到网格里上/下月的日子：目标月就是那天所在的月，跟随不该改写它
        assertEquals(
            day(2026, 11, 1),
            CalendarMath.followDay(day(2026, 11, 1), YearMonth.of(2026, 11)),
        )
        assertEquals(
            day(2026, 9, 28),
            CalendarMath.followDay(day(2026, 9, 28), YearMonth.of(2026, 9)),
        )
        // 夹取之后再往回翻：号数已经变成被夹过的那天（2/28 → 3/28，不是 3/31）
        val feb28 = CalendarMath.followDay(day(2026, 1, 31), YearMonth.of(2026, 2))
        assertEquals(expect(2026, 3, 28), CalendarMath.followDay(feb28, YearMonth.of(2026, 3)))
    }

    /** 收起态的周视图：周一开头的七天，跨月/跨年照常排下去。 */
    @Test
    fun `周视图七天：周一起、跨月跨年不断`() {
        fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
        // 2026-10-15 是周四 → 本周 = 10/12（周一）…10/18（周日）
        val week = CalendarMath.weekDays(day(2026, 10, 15))
        assertEquals(7, week.size)
        assertEquals(day(2026, 10, 12), week.first())
        assertEquals(day(2026, 10, 18), week.last())
        // 依次是 周一…周日（与 WEEKDAY_LABELS 同口径）
        week.forEachIndexed { index, epochDay ->
            assertEquals(index, LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1)
        }
        // 周一那天自己就是首格
        assertEquals(day(2026, 10, 12), CalendarMath.weekStart(day(2026, 10, 12)))
        // 周日那天仍属于本周（最后一个）
        assertEquals(day(2026, 10, 12), CalendarMath.weekStart(day(2026, 10, 18)))
        // 跨月：10/31（周六）所在周 = 10/26…11/1
        val monthEnd = CalendarMath.weekDays(day(2026, 10, 31))
        assertEquals(day(2026, 10, 26), monthEnd.first())
        assertEquals(day(2026, 11, 1), monthEnd.last())
        // 跨年：2027-01-01（周五）所在周 = 2026-12-28…2027-01-03
        val newYear = CalendarMath.weekDays(day(2027, 1, 1))
        assertEquals(day(2026, 12, 28), newYear.first())
        assertEquals(day(2027, 1, 3), newYear.last())
        assertEquals(7, newYear.toSet().size)
    }
}
