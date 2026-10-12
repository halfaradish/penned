package dev.penned.app.ui.home

import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class HomeGroupsTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun minute(isoDateTime: String): Long =
        LocalDateTime.parse(isoDateTime).atZone(zone).toEpochSecond() / 60

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        LocalDate.of(year, month, dayOfMonth).toEpochDay()

    private fun row(
        id: String,
        minute: Long,
        type: TxType,
        cents: Long,
    ) = TxWithCategory(
        id = id, type = type, amountCents = cents, accountId = null, categoryId = null,
        dateEpochMinute = minute, note = null, categoryName = "x", categoryParentName = null,
        categoryIconKey = null, targetAccountName = null, excludeFromStats = false,
        refundCents = 0, discountCents = 0,
    )

    @Test
    fun `group by day keeps descending order and sums per day`() {
        val rows = listOf(
            row("a", minute("2026-10-04T19:30"), TxType.EXPENSE, 1600),
            row("b", minute("2026-10-04T08:00"), TxType.INCOME, 150000),
            row("c", minute("2026-10-03T12:00"), TxType.EXPENSE, 700),
            row("d", minute("2026-10-04T12:00"), TxType.EXPENSE, 700),
        )
        val groups = HomeGroups.groupByDay(rows, zone)
        assertEquals(2, groups.size)
        // 降序：10-04 在前
        val day4 = groups[0]
        assertEquals(150000L, day4.incomeCents)
        assertEquals(1600L + 700L, day4.expenseCents)
        assertEquals(3, day4.items.size)
        assertEquals(700L, groups[1].expenseCents)
    }

    @Test
    fun `transfer is not counted as income or expense`() {
        val rows = listOf(row("t", minute("2026-10-04T10:00"), TxType.TRANSFER, 10000))
        val group = HomeGroups.groupByDay(rows, zone).single()
        assertEquals(0L, group.incomeCents)
        assertEquals(0L, group.expenseCents)
    }

    @Test
    fun `month totals only include the requested month`() {
        val rows = listOf(
            row("a", minute("2026-10-04T10:00"), TxType.EXPENSE, 1600),
            row("b", minute("2026-09-30T10:00"), TxType.EXPENSE, 999),
            row("c", minute("2026-10-05T10:00"), TxType.INCOME, 5000),
        )
        val months = HomeGroups.groupByMonth(rows, zone)
        val october = months.single { it.yearMonth == YearMonth.of(2026, 10) }
        assertEquals(1600L, october.totals.expenseCents)
        assertEquals(5000L, october.totals.incomeCents)
        // 另一张卡各算各的，不会把 10 月的钱算进来
        val september = months.single { it.yearMonth == YearMonth.of(2026, 9) }
        assertEquals(999L, september.totals.expenseCents)
        assertEquals(0L, september.totals.incomeCents)
    }

    @Test
    fun `month totals are the sum of its day totals`() {
        // 月汇总块与日头条读的是同一份数：月合计 = 各日合计之和（含转账/「不计入收支」的排除口径）
        val rows = listOf(
            row("a", minute("2026-10-04T19:30"), TxType.EXPENSE, 1600),
            row("b", minute("2026-10-04T08:00"), TxType.INCOME, 150000),
            row("c", minute("2026-10-03T12:00"), TxType.EXPENSE, 700),
            row("d", minute("2026-10-03T13:00"), TxType.TRANSFER, 9999),
        )
        val month = HomeGroups.groupByMonth(rows, zone).single()
        assertEquals(month.days.sumOf { it.expenseCents }, month.totals.expenseCents)
        assertEquals(month.days.sumOf { it.incomeCents }, month.totals.incomeCents)
        assertEquals(2300L, month.totals.expenseCents)
        assertEquals(150000L, month.totals.incomeCents)
    }

    @Test
    fun `excluded rows stay listed but never count toward totals`() {
        // D020「不计入收支」：列表里看得见，日/月/分类合计都不算它
        val rows = listOf(
            row("a", minute("2026-10-04T19:30"), TxType.EXPENSE, 1600),
            row("b", minute("2026-10-04T20:00"), TxType.EXPENSE, 5000).copy(excludeFromStats = true),
            row("c", minute("2026-10-04T21:00"), TxType.INCOME, 8000).copy(excludeFromStats = true),
        )
        val day = HomeGroups.groupByDay(rows, zone).single()
        assertEquals(3, day.items.size)          // 仍然显示
        assertEquals(1600L, day.expenseCents)    // 5000 不计入
        assertEquals(0L, day.incomeCents)        // 8000 不计入

        val month = HomeGroups.groupByMonth(rows, zone).single()
        assertEquals(1600L, month.totals.expenseCents)
        assertEquals(0L, month.totals.incomeCents)
    }

    @Test
    fun `day label renders relative name weekday and year`() {
        val today = LocalDate.of(2026, 10, 6) // 周二
        assertEquals("今天 10月6日 周二", HomeGroups.dayLabel(day(2026, 10, 6), zone, today))
        assertEquals("昨天 10月5日 周一", HomeGroups.dayLabel(day(2026, 10, 5), zone, today))
        assertEquals("前天 10月4日 周日", HomeGroups.dayLabel(day(2026, 10, 4), zone, today))
        assertEquals("10月1日 周四", HomeGroups.dayLabel(day(2026, 10, 1), zone, today))
        // 非本年：带年份、不带相对词（「昨天」跨年只出现在 1 月 1 日，见下条）
        assertEquals("2025年12月31日 周三", HomeGroups.dayLabel(day(2025, 12, 31), zone, today))
        assertEquals("2027年1月3日 周日", HomeGroups.dayLabel(day(2027, 1, 3), zone, today))
    }

    @Test
    fun `day label on new year shows full previous-year date`() {
        val today = LocalDate.of(2026, 1, 1)
        assertEquals("2025年12月31日 周三", HomeGroups.dayLabel(day(2025, 12, 31), zone, today))
        assertEquals("今天 1月1日 周四", HomeGroups.dayLabel(day(2026, 1, 1), zone, today))
    }

    @Test
    fun `month label is year slash month without padding`() {
        assertEquals("2026/10", HomeGroups.monthLabel(2026, 10))
        assertEquals("2026/1", HomeGroups.monthLabel(2026, 1))
        assertEquals("2025/12", HomeGroups.monthLabel(2025, 12))
    }

    @Test
    fun `clock label is 24h zero padded`() {
        assertEquals("15:59", HomeGroups.clockLabel(minute("2026-10-06T15:59"), zone))
        assertEquals("00:05", HomeGroups.clockLabel(minute("2026-10-06T00:05"), zone))
        assertEquals("23:59", HomeGroups.clockLabel(minute("2026-10-06T23:59"), zone))
    }

    @Test
    fun `group by month keeps months and days in descending order`() {
        val rows = listOf(
            row("a", minute("2026-10-04T19:30"), TxType.EXPENSE, 1600),
            row("b", minute("2026-09-30T08:00"), TxType.EXPENSE, 999),
            row("c", minute("2026-10-01T12:00"), TxType.INCOME, 5000),
            row("d", minute("2025-12-31T23:59"), TxType.EXPENSE, 1200),
        )
        val months = HomeGroups.groupByMonth(rows, zone)
        assertEquals(
            listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9), YearMonth.of(2025, 12)),
            months.map { it.yearMonth },
        )
        // 10 月两天：日降序，且同月的行不串到别的月
        assertEquals(listOf(4, 1), months[0].days.map { LocalDate.ofEpochDay(it.epochDay).dayOfMonth })
        assertEquals(1600L, months[0].days[0].expenseCents)
        assertEquals(5000L, months[0].days[1].incomeCents)
        assertEquals(999L, months[1].days.single().expenseCents)
        assertEquals(1200L, months[2].days.single().expenseCents)
    }

    @Test
    fun `group by month keeps day total rules`() {
        // 转账不计收支；「不计入收支」的行仍在月卡里显示，但不进日合计
        val rows = listOf(
            row("a", minute("2026-10-04T10:00"), TxType.TRANSFER, 10000),
            row("b", minute("2026-10-04T11:00"), TxType.EXPENSE, 5000).copy(excludeFromStats = true),
        )
        val day = HomeGroups.groupByMonth(rows, zone).single().days.single()
        assertEquals(2, day.items.size)
        assertEquals(0L, day.incomeCents)
        assertEquals(0L, day.expenseCents)
    }

    @Test
    fun `empty rows produce no month groups`() {
        assertEquals(0, HomeGroups.groupByMonth(emptyList(), zone).size)
    }

    @Test
    fun `empty month carries no days and zero totals`() {
        // 首页把"本月"钉在最前面时用的就是它：月初还没记账也看得到月汇总与预算块
        val month = HomeGroups.emptyMonth(YearMonth.of(2026, 11))
        assertEquals(YearMonth.of(2026, 11), month.yearMonth)
        assertEquals(0, month.days.size)
        assertEquals(0L, month.totals.incomeCents)
        assertEquals(0L, month.totals.expenseCents)
    }

    @Test
    fun `month expanded defaults to current month and toggles`() {
        val current = YearMonth.of(2026, 10)
        val october = YearMonth.of(2026, 10)
        val september = YearMonth.of(2026, 9)
        val data = listOf(
            // 月份、被点过的月份、期望展开
            Triple(october, emptySet<YearMonth>(), true),        // 本月默认展开
            Triple(september, emptySet(), false),                // 其他月默认折叠
            Triple(october, setOf(october), false),              // 点一次 → 折叠
            Triple(september, setOf(september), true),           // 点一次 → 展开
            Triple(september, setOf(october), false),            // 点别的月不影响这张
            Triple(october, setOf(september), true),
        )
        data.forEach { (month, toggled, expected) ->
            assertEquals("$month toggled=$toggled", expected, HomeGroups.monthExpanded(month, current, toggled))
        }
        // 跨月：本月变成 11 月后，10 月回到"默认折叠"
        assertEquals(false, HomeGroups.monthExpanded(october, YearMonth.of(2026, 11), emptySet()))
        assertEquals(true, HomeGroups.monthExpanded(YearMonth.of(2026, 11), YearMonth.of(2026, 11), emptySet()))
    }
}
