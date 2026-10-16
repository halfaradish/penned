package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

/**
 * 日历的页↔月映射（优化轮）：与统计页同口径的上下界（下界 = 最早有账的月、兜底最近 12 个月；
 * 上界 = 本月，**未来月不给**），页序旧→新，越界的月夹到端点。
 */
class CalendarPagesTest {

    private val today = YearMonth.of(2026, 10)

    @Test
    fun `页序旧到新：最后一页恒是本月，未来月不进条`() {
        val months = CalendarPages.months(earliest = YearMonth.of(2024, 3), today = today)
        assertEquals(YearMonth.of(2024, 3), months.first())
        assertEquals(today, months.last())
        assertEquals(32, months.size)   // 2024-03 ~ 2026-10（含两端）
        // 单调递增（页号越往后越新）
        months.zipWithNext().forEach { (a, b) -> assertEquals(a.plusMonths(1), b) }
        // 未来月（本月之后）一个都不在
        assertEquals(0, months.count { it > today })
    }

    @Test
    fun `下界兜底：还没有账（或只有几笔）时仍能往回翻最近 12 个月`() {
        val months = CalendarPages.months(earliest = null, today = today)
        assertEquals(StatsStrip.MIN_MONTHS, months.size)
        assertEquals(today.minusMonths((StatsStrip.MIN_MONTHS - 1).toLong()), months.first())
        assertEquals(today, months.last())

        // 最早流水比兜底下限还新（如只有本月）→ 仍给足 12 个月
        val fresh = CalendarPages.months(earliest = YearMonth.of(2026, 9), today = today)
        assertEquals(StatsStrip.MIN_MONTHS, fresh.size)

        // 最早流水很老 → 以最早那月为界，不再兜底
        val old = CalendarPages.months(earliest = YearMonth.of(2023, 1), today = today)
        assertEquals(YearMonth.of(2023, 1), old.first())
        assertEquals(today, old.last())
    }

    @Test
    fun `页与月互转：端点与越界都夹到最近的端`() {
        val months = CalendarPages.months(YearMonth.of(2026, 5), today)
        // 最早流水比兜底下限新 → 条仍从"最近 12 个月"起（2025-11），不是 2026-05
        assertEquals(YearMonth.of(2025, 11), months.first())
        // 本月在最后一页
        assertEquals(months.lastIndex, CalendarPages.pageOf(today, months))
        assertEquals(today, CalendarPages.monthOf(months.size - 1, months))
        // 第一页是最老那个月
        assertEquals(0, CalendarPages.pageOf(months.first(), months))
        assertEquals(months.first(), CalendarPages.monthOf(0, months))
        // 往返一致
        months.forEachIndexed { page, month ->
            assertEquals(page, CalendarPages.pageOf(month, months))
            assertEquals(month, CalendarPages.monthOf(page, months))
        }
        // 越界（比如外部传进条外的月份）夹到端点，不崩
        assertEquals(0, CalendarPages.pageOf(YearMonth.of(2020, 1), months))
        assertEquals(months.lastIndex, CalendarPages.pageOf(YearMonth.of(2027, 1), months))
        assertNull(CalendarPages.monthOf(months.size, months))
        assertNull(CalendarPages.monthOf(-1, months))
        // 空条（理论上不会出现）：页 0 兜底、没有月
        assertEquals(0, CalendarPages.pageOf(today, emptyList()))
        assertNull(CalendarPages.monthOf(0, emptyList()))
    }
}
