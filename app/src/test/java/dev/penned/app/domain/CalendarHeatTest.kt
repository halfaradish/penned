package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历热力分档（优化轮）表驱动：分档边界（0 / 极小 / 极大 / 正好等于标尺）、
 * 标尺按当月（邻月不进标尺）、两面都有账时支出优先、无账不出格。
 */
class CalendarHeatTest {

    private fun day(month: Int, dayOfMonth: Int, expense: Long = 0L, income: Long = 0L) =
        CalendarHeat.Day(
            epochDay = LocalDate.of(2026, month, dayOfMonth).toEpochDay(),
            expenseCents = expense,
            incomeCents = income,
        )

    @Test
    fun `单日分档：向上取整、有账至少一档、等于标尺即顶档`() {
        val cases = listOf(
            // 额、标尺、期望档
            Triple(1L, 4L, 1),          // 极小：1/4 正好是第 1 档
            Triple(2L, 4L, 2),
            Triple(3L, 4L, 3),
            Triple(4L, 4L, 4),          // 等于标尺 = 顶档
            Triple(5L, 4L, 4),          // 超过标尺（跨月带出的日子用别月标尺时会遇到）= 封顶
            Triple(1L, 100_000L, 1),    // 极小
            Triple(25_000L, 100_000L, 1),   // 正好 1/4 → 1
            Triple(25_001L, 100_000L, 2),   // 刚过 1/4 → 2（向上取整）
            Triple(50_000L, 100_000L, 2),
            Triple(75_000L, 100_000L, 3),
            Triple(99_999L, 100_000L, 4),
            Triple(300_000L, 300_000L, 4),
            Triple(1L, 1L, 4),          // 当月只有这一笔 1 分：它就是标尺 → 顶档
            Triple(0L, 300_000L, 1),    // 无账：调用方不该进来，兜底给 1 档而不是崩
        )
        cases.forEach { (cents, max, expected) ->
            assertEquals("$cents / $max", expected, CalendarHeat.level(cents, max))
        }
        assertEquals(4, CalendarHeat.LEVELS)
    }

    @Test
    fun `标尺按当月最大额：小月也有对比度、大月不被邻月拉爆`() {
        // 一个月都很小（最大 6.00）：仍然给出 4 档与 1 档
        val small = listOf(
            day(10, 2, expense = 600L),
            day(10, 8, expense = 150L),
            day(10, 9, expense = 300L),
            day(10, 10, expense = 450L),
        )
        val smallHeat = CalendarHeat.monthHeat(small, YearMonth.of(2026, 10))
        assertEquals(4, smallHeat[LocalDate.of(2026, 10, 2).toEpochDay()]!!.level)
        assertEquals(1, smallHeat[LocalDate.of(2026, 10, 8).toEpochDay()]!!.level)
        assertEquals(2, smallHeat[LocalDate.of(2026, 10, 9).toEpochDay()]!!.level)
        assertEquals(3, smallHeat[LocalDate.of(2026, 10, 10).toEpochDay()]!!.level)

        // 邻月的大额不进当月标尺：9/30 花 99999 不影响 10/2 的 6.00 仍是顶档
        val withNeighbour = small + listOf(day(9, 30, expense = 99_999L))
        val heat = CalendarHeat.monthHeat(withNeighbour, YearMonth.of(2026, 10))
        assertEquals(4, heat[LocalDate.of(2026, 10, 2).toEpochDay()]!!.level)
        assertNull("邻月的日子不出当月热力表", heat[LocalDate.of(2026, 9, 30).toEpochDay()])

        // 支出/收入各用各的尺：一笔工资不会把支出全压到第 1 档
        val mixed = listOf(
            day(10, 1, income = 3_000_000L),
            day(10, 2, expense = 600L),
            day(10, 3, expense = 300L),
        )
        val mixedHeat = CalendarHeat.monthHeat(mixed, YearMonth.of(2026, 10))
        assertEquals(4, mixedHeat[LocalDate.of(2026, 10, 1).toEpochDay()]!!.level)
        assertEquals(4, mixedHeat[LocalDate.of(2026, 10, 2).toEpochDay()]!!.level)
        assertEquals(2, mixedHeat[LocalDate.of(2026, 10, 3).toEpochDay()]!!.level)
    }

    @Test
    fun `两面都有账的日子：热力按支出走（支出优先）`() {
        val days = listOf(
            day(10, 4, expense = 1600L, income = 300_000L),   // 工资日也消费
            day(10, 5, expense = 800L),
            day(10, 6, income = 100_000L),
        )
        val heat = CalendarHeat.monthHeat(days, YearMonth.of(2026, 10))
        val salaryDay = heat[LocalDate.of(2026, 10, 4).toEpochDay()]!!
        assertTrue("两面都有账：色相按支出", salaryDay.expense)
        assertEquals("按支出尺分档（不是按收入的 10 万）", 4, salaryDay.level)
        assertTrue(heat[LocalDate.of(2026, 10, 5).toEpochDay()]!!.expense)
        assertFalse(heat[LocalDate.of(2026, 10, 6).toEpochDay()]!!.expense)
    }

    @Test
    fun `无账的日子不进热力表`() {
        val days = listOf(day(10, 8, expense = 600L))
        val heat = CalendarHeat.monthHeat(days, YearMonth.of(2026, 10))
        assertEquals(1, heat.size)
        assertNull(heat[LocalDate.of(2026, 10, 7).toEpochDay()])
        // 一整月都没账 → 空表（格子全是白底）
        assertTrue(CalendarHeat.monthHeat(emptyList(), YearMonth.of(2026, 10)).isEmpty())
    }
}
