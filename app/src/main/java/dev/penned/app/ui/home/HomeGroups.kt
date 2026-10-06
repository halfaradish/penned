package dev.penned.app.ui.home

import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首页聚合纯函数：日分组 + 月汇总。D004——全部从流水重算，不落库。
 * 转账不计入收/支合计；D020「不计入收支」的行仍显示在列表里，但不进任何合计
 * （账户余额不在此列——钱实际动了，余额照算，见 AccountDao.observeWithBalance）。
 */
object HomeGroups {

    data class DayGroup(
        val epochDay: Long,
        val incomeCents: Long,
        val expenseCents: Long,
        val items: List<TxWithCategory>,
    )

    data class MonthTotals(val incomeCents: Long, val expenseCents: Long)

    fun groupByDay(rows: List<TxWithCategory>, zone: ZoneId = ZoneId.systemDefault()): List<DayGroup> {
        val sorted = rows.sortedByDescending { it.dateEpochMinute }
        val byDay = sorted.groupBy { epochMinuteToLocalDate(it.dateEpochMinute, zone).toEpochDay() }
        return byDay.map { (epochDay, items) ->
            val counted = items.filter { !it.excludeFromStats }
            DayGroup(
                epochDay = epochDay,
                incomeCents = counted.sumOf(TxType.INCOME::takeAmount),
                expenseCents = counted.sumOf(TxType.EXPENSE::takeAmount),
                items = items,
            )
        }
    }

    fun monthTotals(
        rows: List<TxWithCategory>,
        year: Int,
        month: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MonthTotals {
        val inMonth = rows.filter {
            val d = epochMinuteToLocalDate(it.dateEpochMinute, zone)
            d.year == year && d.monthValue == month
        }.filter { !it.excludeFromStats }
        return MonthTotals(
            incomeCents = inMonth.sumOf(TxType.INCOME::takeAmount),
            expenseCents = inMonth.sumOf(TxType.EXPENSE::takeAmount),
        )
    }

    fun dayLabel(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val today = LocalDate.now(zone).toEpochDay()
        val relative = when (epochDay) {
            today -> "今天"
            today - 1 -> "昨天"
            today - 2 -> "前天"
            else -> null
        }
        val base = "${date.monthValue}月${date.dayOfMonth}日"
        return if (relative != null) "$base $relative" else base
    }

    data class CategoryTotal(val name: String, val cents: Long, val fraction: Float)

    /**
     * 统计页分类聚合（specs/M1.md 第二批）：按一级分类名汇总某类型金额，降序；
     * 超出 [topN] 的合并为「其他」，fraction 相对该类型总额（含未分类）。
     * 「不计入收支」的行不参与（与首页月汇总同口径）。
     */
    fun categoryTotals(
        rows: List<TxWithCategory>,
        type: TxType,
        topN: Int = 6,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CategoryTotal> {
        val byCategory = rows.filter { it.type == type && !it.excludeFromStats }
            .groupBy { it.categoryName ?: "未分类" }
            .mapValues { (_, items) -> items.sumOf { it.amountCents } }
            .filterValues { it > 0 }
        val total = byCategory.values.sum()
        if (total == 0L) return emptyList()
        val sorted = byCategory.entries.sortedByDescending { it.value }
        val head = sorted.take(topN)
        val rest = sorted.drop(topN)
        val result = head.map { (name, cents) ->
            CategoryTotal(name, cents, cents.toFloat() / total)
        }
        return if (rest.isEmpty()) {
            result
        } else {
            val restCents = rest.sumOf { it.value }
            result + CategoryTotal("其他", restCents, restCents.toFloat() / total)
        }
    }

    private fun epochMinuteToLocalDate(epochMinute: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(epochMinute * 60).atZone(zone).toLocalDate()
}

private fun TxType.takeAmount(row: TxWithCategory): Long = if (row.type == this) row.amountCents else 0L
