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
}
