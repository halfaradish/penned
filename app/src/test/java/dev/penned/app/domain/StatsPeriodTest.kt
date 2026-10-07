package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class StatsPeriodTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun minute(date: String, time: String = "00:00"): Long =
        LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(zone).toEpochSecond() / 60

    @Test
    fun `last seven days rolls to today and includes it`() {
        val today = LocalDate.of(2026, 10, 7)
        val window = StatsPeriods.window(StatsPeriod.Last7Days, zone, today)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), window.startEpochDay)
        assertEquals(today.toEpochDay(), window.endEpochDay)
        assertEquals(7, window.dayCount)
        assertEquals(minute("2026-10-01"), window.startMinute)
        assertEquals(minute("2026-10-08"), window.endMinuteExclusive)
        assertEquals(minute("2026-10-07", "23:59"), window.lastMinute)
        assertEquals("10月1日–10月7日", window.label)
    }

    @Test
    fun `last seven days across new year carries both years`() {
        val today = LocalDate.of(2026, 1, 3)
        val window = StatsPeriods.window(StatsPeriod.Last7Days, zone, today)
        assertEquals(LocalDate.of(2025, 12, 28).toEpochDay(), window.startEpochDay)
        assertEquals("2025年12月28日–2026年1月3日", window.label)
    }

    @Test
    fun `month window covers the whole natural month`() {
        val window = StatsPeriods.window(StatsPeriod.Month(YearMonth.of(2026, 10)), zone)
        assertEquals(minute("2026-10-01"), window.startMinute)
        assertEquals(minute("2026-11-01"), window.endMinuteExclusive)
        assertEquals(31, window.dayCount)
        assertEquals("2026年10月", window.label)

        // 闰年 2 月：29 天（日均的分母跟着走）
        assertEquals(29, StatsPeriods.window(StatsPeriod.Month(YearMonth.of(2028, 2)), zone).dayCount)
        assertEquals(28, StatsPeriods.window(StatsPeriod.Month(YearMonth.of(2026, 2)), zone).dayCount)
    }

    @Test
    fun `year window covers the whole natural year`() {
        val window = StatsPeriods.window(StatsPeriod.Year(2026), zone)
        assertEquals(minute("2026-01-01"), window.startMinute)
        assertEquals(minute("2027-01-01"), window.endMinuteExclusive)
        assertEquals(365, window.dayCount)
        assertEquals("2026年", window.label)
        assertEquals(366, StatsPeriods.window(StatsPeriod.Year(2028), zone).dayCount)
    }

    @Test
    fun `custom range includes both ends and fixes reversed input`() {
        val window = StatsPeriods.window(
            StatsPeriods.custom(
                LocalDate.of(2026, 10, 15).toEpochDay(),
                LocalDate.of(2026, 10, 1).toEpochDay(),
            ),
            zone,
            today = LocalDate.of(2026, 10, 7),
        )
        assertEquals(minute("2026-10-01"), window.startMinute)
        assertEquals(minute("2026-10-16"), window.endMinuteExclusive)
        assertEquals(15, window.dayCount)
        assertEquals("10月1日–10月15日", window.label)

        // 单日区间也不会除零
        val single = StatsPeriods.window(
            StatsPeriods.custom(LocalDate.of(2026, 10, 5).toEpochDay(), LocalDate.of(2026, 10, 5).toEpochDay()),
            zone,
            today = LocalDate.of(2026, 10, 7),
        )
        assertEquals(1, single.dayCount)

        // 非本年：起点补年份（终点与起点同年，省一次）
        val lastYear = StatsPeriods.window(
            StatsPeriods.custom(
                LocalDate.of(2025, 10, 1).toEpochDay(),
                LocalDate.of(2025, 10, 15).toEpochDay(),
            ),
            zone,
            today = LocalDate.of(2026, 10, 7),
        )
        assertEquals("2025年10月1日–10月15日", lastYear.label)
    }

    @Test
    fun `of derives the initial period from the anchor day`() {
        val asOf = LocalDate.of(2026, 10, 7)
        assertEquals(StatsPeriod.Last7Days, StatsPeriods.of(StatsView.LAST_7_DAYS, asOf))
        assertEquals(StatsPeriod.Month(YearMonth.of(2026, 10)), StatsPeriods.of(StatsView.MONTH, asOf))
        assertEquals(StatsPeriod.Year(2026), StatsPeriods.of(StatsView.YEAR, asOf))
        assertEquals(
            StatsPeriod.Custom(
                LocalDate.of(2026, 10, 1).toEpochDay(),
                LocalDate.of(2026, 10, 7).toEpochDay(),
            ),
            StatsPeriods.of(StatsView.CUSTOM, asOf),
        )
    }

    @Test
    fun `last day is the window end used as the anchor`() {
        assertEquals(LocalDate.of(2026, 10, 31), StatsPeriods.lastDay(StatsPeriod.Month(YearMonth.of(2026, 10)), zone))
        assertEquals(LocalDate.of(2026, 2, 28), StatsPeriods.lastDay(StatsPeriod.Month(YearMonth.of(2026, 2)), zone))
        assertEquals(LocalDate.of(2026, 12, 31), StatsPeriods.lastDay(StatsPeriod.Year(2026), zone))
        assertEquals(
            LocalDate.of(2026, 10, 15),
            StatsPeriods.lastDay(StatsPeriods.custom(LocalDate.of(2026, 10, 1).toEpochDay(), LocalDate.of(2026, 10, 15).toEpochDay()), zone),
        )
    }

    @Test
    fun `empty title follows the period and the current date`() {
        val today = LocalDate.of(2026, 10, 7)
        assertEquals(
            "近七日没有收支记录",
            StatsPeriods.emptyTitle(StatsPeriod.Last7Days, zone, today),
        )
        assertEquals(
            "本月没有收支记录",
            StatsPeriods.emptyTitle(StatsPeriod.Month(YearMonth.of(2026, 10)), zone, today),
        )
        assertEquals(
            "该月没有收支记录",
            StatsPeriods.emptyTitle(StatsPeriod.Month(YearMonth.of(2026, 9)), zone, today),
        )
        assertEquals("本年没有收支记录", StatsPeriods.emptyTitle(StatsPeriod.Year(2026), zone, today))
        assertEquals("该年没有收支记录", StatsPeriods.emptyTitle(StatsPeriod.Year(2025), zone, today))
        assertEquals(
            "该时间段没有收支记录",
            StatsPeriods.emptyTitle(StatsPeriods.custom(1L, 2L), zone, today),
        )
    }

    @Test
    fun `initial map puts every view on its own default`() {
        val today = LocalDate.of(2026, 10, 7)
        val initial = StatsPeriods.initial(today)
        assertEquals(StatsView.entries.size, initial.size)
        assertEquals(StatsPeriod.Last7Days, initial.getValue(StatsView.LAST_7_DAYS))
        assertEquals(StatsPeriod.Month(YearMonth.of(2026, 10)), initial.getValue(StatsView.MONTH))
        assertEquals(StatsPeriod.Year(2026), initial.getValue(StatsView.YEAR))
        // 自定义默认"本月 1 日…今天"
        assertEquals(
            StatsPeriods.custom(
                LocalDate.of(2026, 10, 1).toEpochDay(),
                LocalDate.of(2026, 10, 7).toEpochDay(),
            ),
            initial.getValue(StatsView.CUSTOM),
        )
    }

    @Test
    fun `seed anchors the first visit on the current period`() {
        val today = LocalDate.of(2026, 10, 7)
        val month = StatsPeriod.Month(YearMonth.of(2025, 3))
        // 月 → 年：锚在当期最后一天（2025-03-31）→ 2025 年
        assertEquals(StatsPeriod.Year(2025), StatsPeriods.seed(StatsView.YEAR, month, today = today))
        // 年 → 月：锚在 2026-12-31 → 2026 年 12 月
        assertEquals(
            StatsPeriod.Month(YearMonth.of(2026, 12)),
            StatsPeriods.seed(StatsView.MONTH, StatsPeriod.Year(2026), today = today),
        )
        // 自定义：直接把"正在看的那一段"装进去（不再落到莫名的 12 月 1–31 日）
        assertEquals(
            StatsPeriods.custom(LocalDate.of(2025, 3, 1).toEpochDay(), LocalDate.of(2025, 3, 31).toEpochDay()),
            StatsPeriods.seed(StatsView.CUSTOM, month, today = today),
        )
        assertEquals(
            StatsPeriods.custom(LocalDate.of(2026, 1, 1).toEpochDay(), LocalDate.of(2026, 12, 31).toEpochDay()),
            StatsPeriods.seed(StatsView.CUSTOM, StatsPeriod.Year(2026), today = today),
        )
        assertEquals(
            StatsPeriods.custom(LocalDate.of(2026, 10, 1).toEpochDay(), LocalDate.of(2026, 10, 7).toEpochDay()),
            StatsPeriods.seed(StatsView.CUSTOM, StatsPeriod.Last7Days, today = today),
        )
    }
}
