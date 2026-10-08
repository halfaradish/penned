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

    /** 未分类的显示名——不是分类，配色走中性（见 PennedPigment.UNKNOWN），也不可下钻。 */
    const val UNCATEGORIZED = "未分类"

    data class CategoryTotal(
        val name: String,
        val cents: Long,
        val count: Int,
        val fraction: Float,
        /**
         * 这行是否对应一个可下钻的真实分类节点——统计页数据卡按它决定点不点得动：
         * - [categoryTotals]：真实分类 true；纯「未分类」组 false（没有分类可进）。
         * - [childTotals]：真实子分类 true；直挂在节点上的同名桶 false（再点就是节点自己）。
         *
         * **不许用名字判断**：真实分类可能就叫「其他」（种子数据就有）、
         * 「未分类」（用户自建）——名字当身份正是 D049 前的 bug。
         */
        val drillable: Boolean = true,
    )

    /** 分组中间态：合计、笔数、这组是否可下钻（各调用点自己定口径）。 */
    private data class Group(val cents: Long, val count: Int, val drillable: Boolean)

    data class PeriodTotals(val incomeCents: Long, val expenseCents: Long)

    /** 窗口内的收/支合计（rows 已按期窗口取好）。 */
    fun totals(rows: List<TxWithCategory>): PeriodTotals {
        val counted = rows.filter { !it.excludeFromStats }
        return PeriodTotals(
            incomeCents = counted.sumOf(TxType.INCOME::takeAmount),
            expenseCents = counted.sumOf(TxType.EXPENSE::takeAmount),
        )
    }

    /**
     * 分类占比（统计页首屏）：按**一级分类**汇总（二级分类并进它的父分类，与参考图一致——
     * 「食品餐饮 60.10%」含晚餐/午餐/早餐），金额降序，**全部列出**。
     *
     * 不再把尾部合并成 topN 之外的「其他」桶（D049：桶名与预置分类「其他」撞名，
     * 两行同名；用户裁决"展示事实的分类"）。无分类的行单列「未分类」，
     * [CategoryTotal.drillable] = false——它没有可进的分类页。
     */
    fun categoryTotals(rows: List<TxWithCategory>, type: TxType): List<CategoryTotal> {
        val byCategory = rows.filter { it.type == type && !it.excludeFromStats }
            .groupBy { topLevelName(it) }
            .mapValues { (_, items) ->
                Group(
                    cents = items.sumOf { it.amountCents },
                    count = items.size,
                    // 组里有真实分类行才可下钻：纯「未分类」组没有分类可进
                    drillable = items.any { it.categoryName != null },
                )
            }
            .filterValues { it.cents > 0 }
        val total = byCategory.values.sumOf { it.cents }
        if (total == 0L) return emptyList()
        return byCategory.entries
            .sortedByDescending { it.value.cents }
            .map { (name, group) ->
                CategoryTotal(name, group.cents, group.count, group.cents.toFloat() / total, group.drillable)
            }
    }

    /** 行所属的一级分类名：二级分类并进父分类（与 [nodeRows] 的一级节点筛选同一口径）。 */
    fun topLevelName(row: TxWithCategory): String =
        row.categoryParentName ?: row.categoryName ?: UNCATEGORIZED

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
     * 这个节点还有下钻空间吗：一级分类若有子分类的行，就先出「子分类占比/数据」；
     * 否则（二级分类，或一级下没有子分类）出流水列表。分类树只有两级，
     * 二级分类恒为叶子。
     */
    fun hasChildren(rows: List<TxWithCategory>, type: TxType, name: String, parent: String?): Boolean =
        parent == null && rows.any { it.type == type && !it.excludeFromStats && it.categoryParentName == name }

    /**
     * 节点下的子分类聚合（分类详细页）：按二级分类名分组，降序，全部列出；
     * 直接记在节点上的行聚成一个与节点同名的桶——它不是"这个节点的子分类"，
     * [CategoryTotal.drillable] = false（再点就是节点自己）。
     */
    fun childTotals(rows: List<TxWithCategory>, type: TxType): List<CategoryTotal> {
        val byCategory = rows.filter { it.type == type && !it.excludeFromStats }
            .groupBy { it.categoryName ?: UNCATEGORIZED }
            .mapValues { (_, items) ->
                Group(
                    cents = items.sumOf { it.amountCents },
                    count = items.size,
                    // 有父分类的行才是可下钻的子分类；直挂行（无父分类）聚成的桶不可点
                    drillable = items.any { it.categoryParentName != null },
                )
            }
            .filterValues { it.cents > 0 }
        val total = byCategory.values.sumOf { it.cents }
        if (total == 0L) return emptyList()
        return byCategory.entries
            .sortedByDescending { it.value.cents }
            .map { (name, group) ->
                CategoryTotal(name, group.cents, group.count, group.cents.toFloat() / total, group.drillable)
            }
    }

    /** 按日合计（分类详细页的柱状图）：长度 = 窗口自然日数，没有记录的日子补 0。 */
    fun dailyTotals(
        rows: List<TxWithCategory>,
        type: TxType,
        window: StatsWindow,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> = dailyTotals(rows, type, window.startEpochDay, window.dayCount, zone)

    /** [dailyTotals] 的展开形态：详细页手里只有分钟区间与天数，不必凑出一个 StatsWindow。 */
    fun dailyTotals(
        rows: List<TxWithCategory>,
        type: TxType,
        startEpochDay: Long,
        dayCount: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> {
        val days = dayCount.coerceAtLeast(1)
        val out = LongArray(days)
        rows.forEach { row ->
            if (row.type != type || row.excludeFromStats) return@forEach
            val index = (epochMinuteToLocalDate(row.dateEpochMinute, zone).toEpochDay() - startEpochDay).toInt()
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
