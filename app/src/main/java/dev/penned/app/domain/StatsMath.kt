package dev.penned.app.domain

import dev.penned.app.data.db.dao.TxWithCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 统计聚合纯函数（D004：一律从流水重算，不落库）。
 *
 * 口径与首页一致：转账不计收/支；D020「不计入收支」的行不参与任何合计
 * （列表/详情仍看得到它，只是不进数）。
 */
object StatsMath {

    /** 未分类的显示名——不是分类，配色走中性（见 PennedPigment.UNKNOWN）。 */
    const val UNCATEGORIZED = "未分类"

    /** 超 topN 的尾巴合并成这个桶；它不是真实分类，不可下钻、配色走中性。 */
    const val OTHERS = "其他"

    data class CategoryTotal(
        val name: String,
        val cents: Long,
        val count: Int,
        val fraction: Float,
    )

    data class PeriodTotals(val incomeCents: Long, val expenseCents: Long)

    /** 窗口内的收/支合计（rows 已按期窗口取好）。 */
    fun totals(rows: List<TxWithCategory>): PeriodTotals {
        val counted = rows.filter { !it.excludeFromStats }
        return PeriodTotals(
            incomeCents = counted.sumOf(TxType.INCOME::takeAmount),
            expenseCents = counted.sumOf(TxType.EXPENSE::takeAmount),
        )
    }

    /** 分类占比（统计页首屏）：降序、topN 之后合并为「其他」，fraction 相对该类型总额。 */
    fun categoryTotals(
        rows: List<TxWithCategory>,
        type: TxType,
        topN: Int = 6,
    ): List<CategoryTotal> {
        val byCategory = rows.filter { it.type == type && !it.excludeFromStats }
            .groupBy { it.categoryName ?: UNCATEGORIZED }
            .mapValues { (_, items) -> items.sumOf { it.amountCents } to items.size }
            .filterValues { (cents, _) -> cents > 0 }
        val total = byCategory.values.sumOf { it.first }
        if (total == 0L) return emptyList()
        val sorted = byCategory.entries.sortedByDescending { it.value.first }
        val head = sorted.take(topN)
        val rest = sorted.drop(topN)
        val result = head.map { (name, value) ->
            CategoryTotal(name, value.first, value.second, value.first.toFloat() / total)
        }
        return if (rest.isEmpty()) {
            result
        } else {
            val restCents = rest.sumOf { it.value.first }
            val restCount = rest.sumOf { it.value.second }
            result + CategoryTotal(OTHERS, restCents, restCount, restCents.toFloat() / total)
        }
    }

    /**
     * 下钻节点的流水：顶层节点（parent == null）含它自己的行 + 子分类的行；
     * 二级节点（parent != null）只取「该父分类下的该子分类」。转账与「不计入收支」不算。
     */
    fun nodeRows(
        rows: List<TxWithCategory>,
        type: TxType,
        name: String,
        parent: String?,
    ): List<TxWithCategory> = rows.filter {
        it.type == type && !it.excludeFromStats && matchesNode(it, name, parent)
    }

    private fun matchesNode(row: TxWithCategory, name: String, parent: String?): Boolean =
        if (parent == null) {
            row.categoryName == name || row.categoryParentName == name
        } else {
            row.categoryName == name && row.categoryParentName == parent
        }

    /**
     * 节点下的子分类聚合（分类详细页）：按二级分类名分组，降序；
     * 直接记在节点上的行聚成一个与节点同名的桶（点击不再下钻）。
     * 这里**不做** topN 合并——一个节点下通常没几个子分类，全列出来更有用。
     */
    fun childTotals(rows: List<TxWithCategory>, type: TxType): List<CategoryTotal> {
        val byCategory = rows.filter { it.type == type && !it.excludeFromStats }
            .groupBy { it.categoryName ?: UNCATEGORIZED }
            .mapValues { (_, items) -> items.sumOf { it.amountCents } to items.size }
            .filterValues { (cents, _) -> cents > 0 }
        val total = byCategory.values.sumOf { it.first }
        if (total == 0L) return emptyList()
        return byCategory.entries
            .sortedByDescending { it.value.first }
            .map { (name, value) ->
                CategoryTotal(name, value.first, value.second, value.first.toFloat() / total)
            }
    }

    /** 按日合计（分类详细页的柱状图）：长度 = 窗口自然日数，没有记录的日子补 0。 */
    fun dailyTotals(
        rows: List<TxWithCategory>,
        type: TxType,
        window: StatsWindow,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> {
        val days = window.dayCount
        val out = LongArray(days)
        rows.forEach { row ->
            if (row.type != type || row.excludeFromStats) return@forEach
            val index = (epochMinuteToLocalDate(row.dateEpochMinute, zone).toEpochDay() - window.startEpochDay).toInt()
            if (index in 0 until days) out[index] += row.amountCents
        }
        return out.toList()
    }

    /** 按年 1…12 月的合计（年统计的月度趋势）：固定 12 个数，没有记录的月补 0。 */
    fun monthlyTotals(
        rows: List<TxWithCategory>,
        type: TxType,
        year: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> {
        val out = LongArray(12)
        rows.forEach { row ->
            if (row.type != type || row.excludeFromStats) return@forEach
            val date = epochMinuteToLocalDate(row.dateEpochMinute, zone)
            if (date.year == year) out[date.monthValue - 1] += row.amountCents
        }
        return out.toList()
    }

    /** 节点概览：合计 / 笔数 / 日均（= 合计 ÷ 期自然日数，与参考图 953.44÷31=30.76 同口径）。 */
    data class NodeSummary(val totalCents: Long, val count: Int, val avgPerDayCents: Long)

    fun summary(rows: List<TxWithCategory>, type: TxType, dayCount: Int): NodeSummary {
        val counted = rows.filter { it.type == type && !it.excludeFromStats }
        val total = counted.sumOf { it.amountCents }
        return NodeSummary(
            totalCents = total,
            count = counted.size,
            avgPerDayCents = total / dayCount.coerceAtLeast(1),
        )
    }

    private fun epochMinuteToLocalDate(epochMinute: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(epochMinute * 60).atZone(zone).toLocalDate()
}

private fun TxType.takeAmount(row: TxWithCategory): Long = if (row.type == this) row.amountCents else 0L
