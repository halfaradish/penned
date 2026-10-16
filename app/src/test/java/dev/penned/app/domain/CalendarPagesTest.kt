package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * 日历的页↔月映射（优化轮）：与统计页同口径的上下界（下界 = 最早有账的月、兜底最近 12 个月；
 * 上界 = 本月，**未来月不给**），页序**新→旧**（第 0 页恒是本月，跨度变长时页码不平移），越界的月夹到端点。
 */
class CalendarPagesTest {

    private val today = YearMonth.of(2026, 10)

    @Test
    fun `页序新到旧：第 0 页恒是本月，未来月不进条`() {
        val months = CalendarPages.months(earliest = YearMonth.of(2024, 3), today = today)
        assertEquals(today, months.first())
        assertEquals(YearMonth.of(2024, 3), months.last())
        assertEquals(32, months.size)   // 2024-03 ~ 2026-10（含两端）
        // 单调递减（页号越往后越老）
        months.zipWithNext().forEach { (newer, older) -> assertEquals(older.plusMonths(1), newer) }
        // 未来月（本月之后）一个都不在
        assertEquals(0, months.count { it > today })
        // 跨度变长（最早流水晚到）时新的一头不动——第 0 页永远是本月，pager 页码不平移
        val longer = CalendarPages.months(earliest = YearMonth.of(2023, 1), today)
        assertEquals(months.first(), longer.first())
        assertTrue(longer.size > months.size)
    }

    @Test
    fun `下界兜底：还没有账（或只有几笔）时仍能往回翻最近 12 个月`() {
        val months = CalendarPages.months(earliest = null, today = today)
        assertEquals(StatsStrip.MIN_MONTHS, months.size)
        assertEquals(today, months.first())
        assertEquals(today.minusMonths((StatsStrip.MIN_MONTHS - 1).toLong()), months.last())

        // 最早流水比兜底下限还新（如只有本月）→ 仍给足 12 个月
        val fresh = CalendarPages.months(earliest = YearMonth.of(2026, 9), today = today)
        assertEquals(StatsStrip.MIN_MONTHS, fresh.size)

        // 最早流水很老 → 以最早那月为界，不再兜底
        val old = CalendarPages.months(earliest = YearMonth.of(2023, 1), today = today)
        assertEquals(today, old.first())
        assertEquals(YearMonth.of(2023, 1), old.last())
    }

    @Test
    fun `页与月互转：端点与越界都夹到最近的端`() {
        val months = CalendarPages.months(YearMonth.of(2026, 5), today)
        // 最早流水比兜底下限新 → 条仍从"最近 12 个月"起（2025-11），第 0 页是本月
        assertEquals(YearMonth.of(2025, 11), months.last())
        assertEquals(0, CalendarPages.pageOf(today, months))
        assertEquals(today, CalendarPages.monthOf(0, months))
        // 最后一页是最老那个月
        assertEquals(months.lastIndex, CalendarPages.pageOf(months.last(), months))
        assertEquals(months.last(), CalendarPages.monthOf(months.lastIndex, months))
        // 往返一致
        months.forEachIndexed { page, month ->
            assertEquals(page, CalendarPages.pageOf(month, months))
            assertEquals(month, CalendarPages.monthOf(page, months))
        }
        // 越界（比如外部传进条外的月份）夹到端点，不崩
        assertEquals(months.lastIndex, CalendarPages.pageOf(YearMonth.of(2020, 1), months))
        assertEquals(0, CalendarPages.pageOf(YearMonth.of(2027, 1), months))
        assertNull(CalendarPages.monthOf(months.size, months))
        assertNull(CalendarPages.monthOf(-1, months))
        // 空条（理论上不会出现）：页 0 兜底、没有月
        assertEquals(0, CalendarPages.pageOf(today, emptyList()))
        assertNull(CalendarPages.monthOf(0, emptyList()))
    }

    /** 选年月弹窗的年份/月份列：条上有哪些年就给哪些年，"能翻到"之外的月份不列。 */
    @Test
    fun `弹窗年月列：只给能翻到的年与月（今年只到本月）`() {
        val months = CalendarPages.months(earliest = YearMonth.of(2024, 3), today = today)
        assertEquals(listOf(2026, 2025, 2024), CalendarPages.years(months))

        // 今年只到本月（10 月），后两个月不给
        assertEquals((1..10).toList(), CalendarPages.pickerMonths(2026, today, months))
        // 往年给满 12 个月
        assertEquals((1..12).toList(), CalendarPages.pickerMonths(2025, today, months))
        // 最早有账那年（2024-03）只从 3 月起
        assertEquals((3..12).toList(), CalendarPages.pickerMonths(2024, today, months))
        // 条外的年：一个月都不给（调用方不会列这个年，兜底也不崩）
        assertEquals(emptyList<Int>(), CalendarPages.pickerMonths(2023, today, months))
        assertEquals(emptyList<Int>(), CalendarPages.pickerMonths(2027, today, months))

        // 没有账（兜底最近 12 个月）：年份列跟着兜底走，平均 1~2 年
        val fallback = CalendarPages.months(earliest = null, today = today)
        assertEquals(listOf(2026, 2025), CalendarPages.years(fallback))
        assertEquals((1..10).toList(), CalendarPages.pickerMonths(2026, today, fallback))
        assertEquals((11..12).toList(), CalendarPages.pickerMonths(2025, today, fallback))
    }
}
