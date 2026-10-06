package dev.penned.app.ui.home

import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class HomeGroupsTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun minute(isoDateTime: String): Long =
        java.time.LocalDateTime.parse(isoDateTime).atZone(zone).toEpochSecond() / 60

    private fun row(
        id: String,
        minute: Long,
        type: TxType,
        cents: Long,
    ) = TxWithCategory(
        id = id, type = type, amountCents = cents, accountId = null, categoryId = null,
        dateEpochMinute = minute, note = null, categoryName = "x", categoryParentName = null,
        categoryIconKey = null, targetAccountName = null, excludeFromStats = false,
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
        val totals = HomeGroups.monthTotals(rows, year = 2026, month = 10, zone = zone)
        assertEquals(1600L, totals.expenseCents)
        assertEquals(5000L, totals.incomeCents)
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

        val totals = HomeGroups.monthTotals(rows, year = 2026, month = 10, zone = zone)
        assertEquals(1600L, totals.expenseCents)
        assertEquals(0L, totals.incomeCents)

        assertEquals(1600L, HomeGroups.categoryTotals(rows, TxType.EXPENSE).sumOf { it.cents })
    }

    @Test
    fun `day label renders relative names`() {
        val today = java.time.LocalDate.now(zone).toEpochDay()
        assertEquals("${java.time.LocalDate.now(zone).monthValue}月${java.time.LocalDate.now(zone).dayOfMonth}日 今天", HomeGroups.dayLabel(today, zone))
        assertEquals("${java.time.LocalDate.ofEpochDay(today - 1).monthValue}月${java.time.LocalDate.ofEpochDay(today - 1).dayOfMonth}日 昨天", HomeGroups.dayLabel(today - 1, zone))
    }

    @Test
    fun `category totals groups by name merges tail and computes fractions`() {
        fun namedRow(id: String, cents: Long, name: String?, type: TxType = TxType.EXPENSE) =
            row(id, minute("2026-10-0${(id.first().code % 5) + 1}T10:00"), type, cents)
                .copy(categoryName = name)
        val rows = listOf(
            namedRow("1", 5000, "食品餐饮"),
            namedRow("2", 3000, "购物消费"),
            namedRow("3", 2000, "出行交通"),
            namedRow("4", 1000, "文化教育"),
            namedRow("5", 99000, "工资", TxType.INCOME),
        )
        val totals = HomeGroups.categoryTotals(rows, TxType.EXPENSE, topN = 2)
        // 按金额降序，topN=2 后其余合并为"其他"
        assertEquals(listOf("食品餐饮", "购物消费", "其他"), totals.map { it.name })
        assertEquals(listOf(5000L, 3000L, 3000L), totals.map { it.cents })
        val sum = totals.sumOf { it.cents }
        assertEquals(11000L, sum)
        // fraction 加总为 1
        assertEquals(1.0f, totals.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
        // 收入域单独聚合
        assertEquals(1, HomeGroups.categoryTotals(rows, TxType.INCOME).size)
        // 无数据返回空
        assertEquals(0, HomeGroups.categoryTotals(emptyList(), TxType.EXPENSE).size)
    }
}
