package dev.penned.app.ui.home

import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.AddDate
import dev.penned.app.domain.CalendarMath
import dev.penned.app.domain.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 首页聚合纯函数：日分组 + 月汇总。D004——全部从流水重算，不落库。
 * 转账不计入收/支合计；D020「不计入收支」的行仍显示在列表里，但不进任何合计
 * （账户余额不在此列——钱实际动了，余额照算，见 AccountDao.observeWithBalance）。
 * 统计页的分类/窗口聚合已搬到 `domain/StatsMath`（AI 轮）。
 */
object HomeGroups {

    data class DayGroup(
        val epochDay: Long,
        val incomeCents: Long,
        val expenseCents: Long,
        val items: List<TxWithCategory>,
    )

    /** 一个自然月 = 首页的一张卡片（卡片头是月标题行 + 展开时的汇总/预算块）。 */
    data class MonthGroup(
        val yearMonth: YearMonth,
        val days: List<DayGroup>,
        val totals: MonthTotals,
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

    /** 按月分组（月份降序、月内日降序、日内行降序）；合计口径与 [groupByDay] 一致。 */
    fun groupByMonth(rows: List<TxWithCategory>, zone: ZoneId = ZoneId.systemDefault()): List<MonthGroup> =
        groupByDay(rows, zone)
            .groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }
            .map { (yearMonth, days) ->
                MonthGroup(
                    yearMonth = yearMonth,
                    days = days,
                    // 月合计 = 各日合计之和：日合计已经按 D004/D020 口径算过（转账不算、「不计入收支」不算）
                    totals = MonthTotals(
                        incomeCents = days.sumOf { it.incomeCents },
                        expenseCents = days.sumOf { it.expenseCents },
                    ),
                )
            }
            .sortedByDescending { it.yearMonth }

    /**
     * 本月还没流水时的空月卡（只有标题行与汇总/预算块，没有日与行）。
     * 首页用它把「本月」钉在列表最前面——月汇总与预算都挂在本月卡上，月初不该整块消失。
     */
    fun emptyMonth(yearMonth: YearMonth): MonthGroup =
        MonthGroup(yearMonth, days = emptyList(), totals = MonthTotals(0L, 0L))

    /**
     * 某月的收/支合计：**只算落在该月的天**（日历的月小字总结用它）。
     *
     * 日历网格会带出上/下月的日子（`CalendarMath.gridWindow`），直接对网格里的小计求和会把邻月
     * 的流水算进本月——所以这里按 [CalendarMath.inMonth] 过滤。日合计仍是 [groupByDay] 那一份
     * （转账不算、`excludeFromStats` 不算），本函数只做归属过滤 + 相加，不新开一套求和口径。
     */
    fun monthTotalsOf(days: Collection<DayGroup>, month: YearMonth): MonthTotals {
        val inMonth = days.filter { CalendarMath.inMonth(it.epochDay, month) }
        return MonthTotals(
            incomeCents = inMonth.sumOf { it.incomeCents },
            expenseCents = inMonth.sumOf { it.expenseCents },
        )
    }

    /** 结余 = 收 − 支（**不为负而改口径**：负数照带减号，颜色由界面层定）。 */
    fun balanceOf(totals: MonthTotals): Long = totals.incomeCents - totals.expenseCents

    /**
     * 月卡是否展开：**本月默认展开、其余默认折叠**，点标题行一次翻转。
     * [toggledMonths] 只记"被点过的月份"，所以跨月（本月变了）时默认态自动跟上。
     */
    fun monthExpanded(yearMonth: YearMonth, currentMonth: YearMonth, toggledMonths: Set<YearMonth>): Boolean =
        (yearMonth == currentMonth) != (yearMonth in toggledMonths)

    /**
     * 日头条文案：`今天 10月6日 周二` / `10月5日 周一` / 非本年 `2025年12月31日 周三`。
     * 非本年必须带年份（同名日期跨年会歧义），此时也不再带「今天/昨天/前天」——
     * 「昨天」跨到上一年只出现在 1 月 1 日，「昨天 2025年12月31日」读起来别扭，完整日期更清楚。
     */
    fun dayLabel(
        epochDay: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val sameYear = date.year == today.year
        val relative = if (!sameYear) {
            null
        } else {
            when (epochDay) {
                today.toEpochDay() -> "今天"
                today.toEpochDay() - 1 -> "昨天"
                today.toEpochDay() - 2 -> "前天"
                else -> null
            }
        }
        val datePart = buildString {
            if (!sameYear) append(date.year).append('年')
            append(date.monthValue).append('月').append(date.dayOfMonth).append('日')
        }
        return listOfNotNull(relative, datePart, AddDate.weekdayLabel(date)).joinToString(" ")
    }

    /** 月标题（卡片最上面一行）：`2026/10`，只到月、不补零、不带收支。 */
    fun monthLabel(year: Int, month: Int): String = "$year/$month"

    /** 流水行的时段（24 小时制、补零）：`09:05`。 */
    fun clockLabel(epochMinute: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochSecond(epochMinute * 60).atZone(zone).toLocalTime()
        return "%02d:%02d".format(time.hour, time.minute)
    }

    private fun epochMinuteToLocalDate(epochMinute: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(epochMinute * 60).atZone(zone).toLocalDate()
}

private fun TxType.takeAmount(row: TxWithCategory): Long = if (row.type == this) row.amountCents else 0L
