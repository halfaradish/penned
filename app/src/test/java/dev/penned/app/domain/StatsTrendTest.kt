package dev.penned.app.domain

import dev.penned.app.data.db.dao.TxWithCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * 统计页趋势卡的口径（AL 轮）：横轴类型与柱高数据。
 * 今天固定 2026-10-07，窗口按 Asia/Shanghai 算（与 StatsMathTest 同一套 fixture 写法）。
 */
class StatsTrendTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun minute(isoDateTime: String): Long =
        LocalDateTime.parse(isoDateTime).atZone(zone).toEpochSecond() / 60

    private fun row(
        id: String,
        isoDateTime: String,
        cents: Long,
        type: TxType = TxType.EXPENSE,
        excluded: Boolean = false,
    ) = TxWithCategory(
        id = id, type = type, amountCents = cents, accountId = null, categoryId = null,
        dateEpochMinute = minute(isoDateTime), note = null, categoryName = null,
        categoryParentName = null, categoryIconKey = null, targetAccountName = null,
        excludeFromStats = excluded,
    )

    private fun windowOf(period: StatsPeriod) =
        StatsPeriods.window(period, zone, today = LocalDate.of(2026, 10, 7))

    @Test
    fun `axis is daily for the rolling week, month and custom range`() {
        // 近七日：含今天的滚动 7 天
        assertEquals(
            TrendAxis.Days(startEpochDay = LocalDate.of(2026, 10, 1).toEpochDay(), count = 7),
            StatsTrend.axisOf(StatsPeriod.Last7Days, windowOf(StatsPeriod.Last7Days)),
        )
        // 月统计：当月自然日数；横轴不写周几（稀疏标签下周几是噪声）
        val month = StatsPeriod.Month(YearMonth.of(2026, 10))
        assertEquals(
            TrendAxis.Days(LocalDate.of(2026, 10, 1).toEpochDay(), 31, showWeekday = false),
            StatsTrend.axisOf(month, windowOf(month)),
        )
        // 自定义：区间天数（两端都含）
        val custom = StatsPeriods.custom(
            LocalDate.of(2026, 9, 10).toEpochDay(),
            LocalDate.of(2026, 9, 30).toEpochDay(),
        )
        assertEquals(TrendAxis.Days(LocalDate.of(2026, 9, 10).toEpochDay(), 21), StatsTrend.axisOf(custom, windowOf(custom)))
    }

    @Test
    fun `axis is monthly for the year view`() {
        assertEquals(
            TrendAxis.Months(2026),
            StatsTrend.axisOf(StatsPeriod.Year(2026), windowOf(StatsPeriod.Year(2026))),
        )
    }

    @Test
    fun `daily bars are zero filled and drop flagged rows and transfers`() {
        val period = StatsPeriod.Last7Days
        val rows = listOf(
            row("a", "2026-10-01T10:00", 1600),
            row("b", "2026-10-01T11:00", 5000, excluded = true),
            row("c", "2026-10-04T12:00", 3000),
            row("d", "2026-10-04T13:00", 9900, type = TxType.TRANSFER),
            row("e", "2026-10-07T09:00", 250),
            row("f", "2026-09-30T23:00", 7777), // 窗口外：不计
        )
        assertEquals(
            listOf(1600L, 0L, 0L, 3000L, 0L, 0L, 250L),
            StatsTrend.bars(rows, TxType.EXPENSE, period, windowOf(period), zone),
        )
    }

    @Test
    fun `yearly bars are twelve months and follow the type`() {
        val period = StatsPeriod.Year(2026)
        val rows = listOf(
            row("a", "2026-03-05T10:00", 420000),
            row("b", "2026-10-04T12:00", 32300),
            row("c", "2026-10-05T12:00", 150000, type = TxType.INCOME),
        )
        val expense = StatsTrend.bars(rows, TxType.EXPENSE, period, windowOf(period), zone)
        assertEquals(12, expense.size)
        assertEquals(420000L, expense[2])
        assertEquals(32300L, expense[9])

        val income = StatsTrend.bars(rows, TxType.INCOME, period, windowOf(period), zone)
        assertEquals(12, income.size)
        assertEquals(150000L, income[9])
        // 支出那笔不进收入侧
        assertEquals(150000L, income.sum())
    }

    @Test
    fun `title follows the switched side`() {
        assertEquals("支出趋势", StatsTrend.title(TxType.EXPENSE))
        assertEquals("收入趋势", StatsTrend.title(TxType.INCOME))
    }
}
