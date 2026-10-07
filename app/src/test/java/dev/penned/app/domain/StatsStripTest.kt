package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * 统计页时间切换条（AK 轮）：跨度规则与文案——纯函数，表驱动。
 * 今天固定 2026-10，方便断言"至少最近 12 个月 / 5 年"的下限。
 */
class StatsStripTest {

    private val today = YearMonth.of(2026, 10)

    @Test
    fun `months span from the current month back to the earliest data month`() {
        val months = StatsStrip.months(earliest = YearMonth.of(2022, 12), today = today)

        assertEquals(YearMonth.of(2026, 10), months.first())
        assertEquals(YearMonth.of(2022, 12), months.last())
        assertEquals(47, months.size)
        // 新→旧，且一格不多不少
        assertEquals(months.sortedDescending(), months)
        assertTrue(months.none { it > today })
    }

    @Test
    fun `months fall back to the last 12 months when there is no data yet`() {
        val months = StatsStrip.months(earliest = null, today = today)

        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2026, 10), months.first())
        assertEquals(YearMonth.of(2025, 11), months.last())
    }

    @Test
    fun `months fall back to 12 when the data is newer than the floor`() {
        // 只记了两个月：条仍要够滑（下限 12 个月），但不会长过数据本身
        val months = StatsStrip.months(earliest = YearMonth.of(2026, 8), today = today)

        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2025, 11), months.last())
    }

    @Test
    fun `months always contain the selected month, even outside the span`() {
        // 自定义区间锚到 2023-01（比最早流水还老）→ 补进条里，否则条上没有选中项
        val months = StatsStrip.months(
            earliest = YearMonth.of(2024, 5),
            today = today,
            include = YearMonth.of(2023, 1),
        )

        assertTrue(YearMonth.of(2023, 1) in months)
        assertEquals(YearMonth.of(2023, 1), months.last())
        assertEquals(months.sortedDescending(), months)
    }

    @Test
    fun `years span from this year back to the earliest data year with a 5 year floor`() {
        assertEquals(
            listOf(2026, 2025, 2024, 2023, 2022),
            StatsStrip.years(earliest = YearMonth.of(2022, 12), today = today),
        )
        // 下限 5 年（数据更短就更短不了）
        assertEquals(
            listOf(2026, 2025, 2024, 2023, 2022),
            StatsStrip.years(earliest = null, today = today),
        )
        assertEquals(
            listOf(2026, 2025, 2024, 2023, 2022),
            StatsStrip.years(earliest = YearMonth.of(2026, 3), today = today),
        )
        // 比数据更老的选中项也要在条上
        assertTrue(2020 in StatsStrip.years(YearMonth.of(2024, 5), today, include = 2020))
    }

    @Test
    fun `month labels use 本月 and 上月 only for the current and previous month`() {
        assertEquals("本月", StatsStrip.monthLabel(YearMonth.of(2026, 10), today))
        assertEquals("上月", StatsStrip.monthLabel(YearMonth.of(2026, 9), today))
        assertEquals("8月", StatsStrip.monthLabel(YearMonth.of(2026, 8), today))
        assertEquals("12月", StatsStrip.monthLabel(YearMonth.of(2025, 12), today))
        // 跨年：1 月的"上月"是去年 12 月
        assertEquals(
            "上月",
            StatsStrip.monthLabel(YearMonth.of(2025, 12), YearMonth.of(2026, 1)),
        )
    }

    @Test
    fun `picker month column stops at the current month for this year`() {
        // 今年只到本月（未来月份没有数据可看），往年整年
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), StatsStrip.pickerMonths(2026, today))
        assertEquals((1..12).toList(), StatsStrip.pickerMonths(2025, today))
    }

    @Test
    fun `clamp month pulls the selection back into the year's range`() {
        // 先选了去年 12 月，再滚回今年 → 只能落到 10 月
        assertEquals(10, StatsStrip.clampMonth(12, 2026, today))
        assertEquals(12, StatsStrip.clampMonth(12, 2025, today))
        assertEquals(3, StatsStrip.clampMonth(3, 2026, today))
    }
}
