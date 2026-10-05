package dev.penned.app.ui.home

import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首页聚合纯函数：日分组 + 月汇总。D004——全部从流水重算，不落库。
 * 转账不计入收/支合计。
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
            DayGroup(
                epochDay = epochDay,
                incomeCents = items.sumOf(TxType.INCOME::takeAmount),
                expenseCents = items.sumOf(TxType.EXPENSE::takeAmount),
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
        }
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

    private fun epochMinuteToLocalDate(epochMinute: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(epochMinute * 60).atZone(zone).toLocalDate()
}

private fun TxType.takeAmount(row: TxWithCategory): Long = if (row.type == this) row.amountCents else 0L
