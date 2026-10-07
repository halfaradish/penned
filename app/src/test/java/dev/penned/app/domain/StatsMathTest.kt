package dev.penned.app.domain

import dev.penned.app.data.db.dao.TxWithCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class StatsMathTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun minute(isoDateTime: String): Long =
        LocalDateTime.parse(isoDateTime).atZone(zone).toEpochSecond() / 60

    private fun row(
        id: String,
        isoDateTime: String,
        cents: Long,
        type: TxType = TxType.EXPENSE,
        name: String? = null,
        parent: String? = null,
        excluded: Boolean = false,
    ) = TxWithCategory(
        id = id, type = type, amountCents = cents, accountId = null, categoryId = null,
        dateEpochMinute = minute(isoDateTime), note = null, categoryName = name,
        categoryParentName = parent, categoryIconKey = null, targetAccountName = null,
        excludeFromStats = excluded,
    )

    private val october = StatsPeriods.window(StatsPeriod.Month(YearMonth.of(2026, 10)), zone)

    // —— 合计口径 ——

    @Test
    fun `totals exclude transfers and stats-flagged rows`() {
        val rows = listOf(
            row("a", "2026-10-04T10:00", 1600),
            row("b", "2026-10-04T11:00", 5000, excluded = true),
            row("c", "2026-10-04T12:00", 8000, type = TxType.INCOME, excluded = true),
            row("d", "2026-10-04T13:00", 9900, type = TxType.INCOME),
            row("e", "2026-10-04T14:00", 7000, type = TxType.TRANSFER),
        )
        val totals = StatsMath.totals(rows)
        assertEquals(1600L, totals.expenseCents)
        assertEquals(9900L, totals.incomeCents)
    }

    // —— 分类占比 ——

    @Test
    fun `category totals groups by name merges tail and counts rows`() {
        val rows = listOf(
            row("1", "2026-10-01T10:00", 5000, name = "食品餐饮"),
            row("2", "2026-10-02T10:00", 1000, name = "食品餐饮"),
            row("3", "2026-10-03T10:00", 3000, name = "购物消费"),
            row("4", "2026-10-04T10:00", 2000, name = "出行交通"),
            row("5", "2026-10-05T10:00", 1000, name = "文化教育"),
            row("6", "2026-10-06T10:00", 99000, type = TxType.INCOME, name = "工资"),
            // 无分类的支出归到「未分类」，不是「其他」
            row("7", "2026-10-07T10:00", 500, name = null),
        )
        val totals = StatsMath.categoryTotals(rows, TxType.EXPENSE, topN = 3)
        // 降序：食品餐饮 6000 > 购物消费 3000 > 出行交通 2000，其余（文化教育 + 未分类）合并「其他」
        assertEquals(listOf("食品餐饮", "购物消费", "出行交通", "其他"), totals.map { it.name })
        assertEquals(listOf(6000L, 3000L, 2000L, 1500L), totals.map { it.cents })
        assertEquals(listOf(2, 1, 1, 2), totals.map { it.count })
        assertEquals(12500L, totals.sumOf { it.cents })
        assertEquals(1.0f, totals.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)

        // 收入域单独聚合、无数据返回空
        assertEquals(listOf("工资"), StatsMath.categoryTotals(rows, TxType.INCOME).map { it.name })
        assertEquals(0, StatsMath.categoryTotals(emptyList(), TxType.EXPENSE).size)
    }

    @Test
    fun `category totals put uncategorized rows into their own bucket`() {
        val rows = listOf(
            row("1", "2026-10-01T10:00", 700, name = null),
            row("2", "2026-10-02T10:00", 300, name = "食品餐饮"),
        )
        val totals = StatsMath.categoryTotals(rows, TxType.EXPENSE)
        assertEquals(2, totals.size)
        assertEquals(listOf("未分类", "食品餐饮"), totals.map { it.name })
        assertEquals(700L, totals.first { it.name == "未分类" }.cents)
    }

    // —— 下钻节点筛选 ——

    @Test
    fun `node rows for a top level category include its own rows and children`() {
        val rows = listOf(
            row("1", "2026-10-01T10:00", 5000, name = "食品餐饮"),
            row("2", "2026-10-02T10:00", 3000, name = "晚餐", parent = "食品餐饮"),
            row("3", "2026-10-03T10:00", 2000, name = "午餐", parent = "食品餐饮"),
            // 同名但属于别的父分类：不该混进来
            row("4", "2026-10-04T10:00", 999, name = "晚餐", parent = "居家生活"),
            row("5", "2026-10-05T10:00", 888, name = "购物消费"),
            row("6", "2026-10-06T10:00", 777, name = "晚餐", parent = "食品餐饮", excluded = true),
            row("7", "2026-10-07T10:00", 666, name = "晚餐", parent = "食品餐饮", type = TxType.INCOME),
        )
        val node = StatsMath.nodeRows(rows, TxType.EXPENSE, "食品餐饮", parent = null)
        assertEquals(listOf("1", "2", "3"), node.map { it.id })
    }

    @Test
    fun `node rows for a child category match name and parent`() {
        val rows = listOf(
            row("1", "2026-10-01T10:00", 5000, name = "食品餐饮"),
            row("2", "2026-10-02T10:00", 3000, name = "晚餐", parent = "食品餐饮"),
            row("3", "2026-10-03T10:00", 999, name = "晚餐", parent = "居家生活"),
        )
        val node = StatsMath.nodeRows(rows, TxType.EXPENSE, "晚餐", parent = "食品餐饮")
        assertEquals(listOf("2"), node.map { it.id })
    }

    @Test
    fun `child totals keep the direct bucket and skip the others merge`() {        val rows = listOf(
            row("1", "2026-10-01T10:00", 2000, name = "食品餐饮"),
            row("2", "2026-10-02T10:00", 3000, name = "晚餐", parent = "食品餐饮"),
            row("3", "2026-10-03T10:00", 3000, name = "午餐", parent = "食品餐饮"),
            row("4", "2026-10-04T10:00", 1000, name = "水果", parent = "食品餐饮"),
        )
        val node = StatsMath.nodeRows(rows, TxType.EXPENSE, "食品餐饮", parent = null)
        val children = StatsMath.childTotals(node, TxType.EXPENSE)
        // 降序、无 topN 合并；直挂在节点上的行聚成与节点同名的桶
        assertEquals(listOf("晚餐", "午餐", "食品餐饮", "水果"), children.map { it.name })
        assertEquals(listOf(3000L, 3000L, 2000L, 1000L), children.map { it.cents })
        assertEquals(1.0f, children.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun `has children only for top level categories with sub rows`() {
        val rows = listOf(
            row("1", "2026-10-01T10:00", 2000, name = "食品餐饮"),
            row("2", "2026-10-02T10:00", 3000, name = "晚餐", parent = "食品餐饮"),
            row("3", "2026-10-03T10:00", 1500, name = "购物消费"),
        )
        // 一级分类有子分类的行 → 可下钻；只有直挂行的分类 → 叶子（出流水列表）
        assertTrue(StatsMath.hasChildren(rows, TxType.EXPENSE, "食品餐饮", parent = null))
        assertFalse(StatsMath.hasChildren(rows, TxType.EXPENSE, "购物消费", parent = null))
        // 二级分类恒为叶子（分类树只有两级）
        assertFalse(StatsMath.hasChildren(rows, TxType.EXPENSE, "晚餐", parent = "食品餐饮"))
        // 不计入收支/别的类型的子行不算下钻空间
        val onlyExcluded = listOf(
            row("1", "2026-10-01T10:00", 2000, name = "食品餐饮"),
            row("2", "2026-10-02T10:00", 3000, name = "晚餐", parent = "食品餐饮", excluded = true),
            row("3", "2026-10-03T10:00", 3000, name = "晚餐", parent = "食品餐饮", type = TxType.INCOME),
        )
        assertFalse(StatsMath.hasChildren(onlyExcluded, TxType.EXPENSE, "食品餐饮", parent = null))
    }

    // —— 逐日 / 逐月 ——

    @Test
    fun `daily totals fill missing days with zero inside the window`() {
        val rows = listOf(
            row("1", "2026-10-01T09:00", 1600),
            row("2", "2026-10-01T19:00", 400),
            row("3", "2026-10-03T12:00", 700),
            // 窗口外（9 月 30 日）与不计入收支的行都不该出现
            row("4", "2026-09-30T12:00", 888),
            row("5", "2026-10-03T13:00", 999, excluded = true),
            row("6", "2026-10-03T14:00", 555, type = TxType.INCOME),
        )
        val daily = StatsMath.dailyTotals(rows, TxType.EXPENSE, october, zone)
        assertEquals(31, daily.size)
        assertEquals(2000L, daily[0])
        assertEquals(0L, daily[1])
        assertEquals(700L, daily[2])
        assertEquals(0L, daily[30])
        assertEquals(2700L, daily.sum())
    }

    @Test
    fun `monthly totals fill twelve months of the year`() {
        val rows = listOf(
            row("1", "2026-01-15T09:00", 1600),
            row("2", "2026-10-01T09:00", 2000),
            row("3", "2026-10-20T09:00", 3000),
            // 别的年份与不计入收支的不算
            row("4", "2025-10-20T09:00", 888),
            row("5", "2026-10-21T09:00", 999, excluded = true),
        )
        val monthly = StatsMath.monthlyTotals(rows, TxType.EXPENSE, 2026, zone)
        assertEquals(12, monthly.size)
        assertEquals(1600L, monthly[0])
        assertEquals(0L, monthly[8])
        assertEquals(5000L, monthly[9])
        assertEquals(6600L, monthly.sum())
    }

    // —— 节点概览 ——

    @Test
    fun `summary computes total count and average per day`() {
        val rows = listOf(
            row("1", "2026-10-01T09:00", 95344, name = "食品餐饮"),
            row("2", "2026-10-02T09:00", 50000, name = "晚餐", parent = "食品餐饮"),
            row("3", "2026-10-02T10:00", 999, name = "晚餐", parent = "食品餐饮", excluded = true),
            row("4", "2026-10-02T11:00", 777, name = "工资", type = TxType.INCOME),
        )
        val node = StatsMath.nodeRows(rows, TxType.EXPENSE, "食品餐饮", parent = null)
        val summary = StatsMath.summary(node, TxType.EXPENSE, dayCount = 31)
        assertEquals(145344L, summary.totalCents)
        assertEquals(2, summary.count)
        // 953.44 + 500.00 = 1453.44 ÷ 31 = 46.88
        assertEquals(4688L, summary.avgPerDayCents)

        // 分母至少 1，不会除零
        assertEquals(145344L, StatsMath.summary(node, TxType.EXPENSE, dayCount = 0).avgPerDayCents)
    }

    @Test
    fun `excluded rows never count in any stats math`() {
        val rows = listOf(
            row("1", "2026-10-04T19:30", 1600, name = "食品餐饮"),
            row("2", "2026-10-04T20:00", 5000, name = "食品餐饮", excluded = true),
        )
        assertEquals(0L, StatsMath.totals(rows).incomeCents)
        assertEquals(1600L, StatsMath.totals(rows).expenseCents)
        assertEquals(1600L, StatsMath.categoryTotals(rows, TxType.EXPENSE).sumOf { it.cents })
        assertEquals(1, StatsMath.categoryTotals(rows, TxType.EXPENSE).sumOf { it.count })
        assertEquals(1600L, StatsMath.dailyTotals(rows, TxType.EXPENSE, october, zone).sum())
        assertEquals(1600L, StatsMath.monthlyTotals(rows, TxType.EXPENSE, 2026, zone).sum())
        assertEquals(1, StatsMath.nodeRows(rows, TxType.EXPENSE, "食品餐饮", parent = null).size)
    }
}
