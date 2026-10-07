package dev.penned.app.domain

import dev.penned.app.data.db.dao.TxWithCategory
import java.time.ZoneId

/**
 * 统计页「趋势」卡的横轴（AL 轮）：按日（近七日 / 月统计 / 自定义）或按月（年统计 12 根）。
 * 卡片位置与口径见 docs/界面重构方案.md 第十四节。
 */
sealed interface TrendAxis {
    /** 按日：窗口内每一天一根柱，[startEpochDay] 起共 [count] 天。 */
    data class Days(val startEpochDay: Long, val count: Int) : TrendAxis

    /** 按月：该年 1~12 月各一根（[year] 供按住浮层写完整年月）。 */
    data class Months(val year: Int) : TrendAxis
}

/** 趋势卡的纯口径（表驱动测试）：横轴类型 + 每根柱的金额。 */
object StatsTrend {

    fun axisOf(period: StatsPeriod, window: StatsWindow): TrendAxis = when (period) {
        is StatsPeriod.Year -> TrendAxis.Months(period.year)
        else -> TrendAxis.Days(window.startEpochDay, window.dayCount)
    }

    /** 柱高数据：按日窗口走 [StatsMath.dailyTotals]，年统计走 [StatsMath.monthlyTotals]（都零填充）。 */
    fun bars(
        rows: List<TxWithCategory>,
        type: TxType,
        period: StatsPeriod,
        window: StatsWindow,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> = when {
        period is StatsPeriod.Year -> StatsMath.monthlyTotals(rows, type, period.year, zone)
        else -> StatsMath.dailyTotals(rows, type, window, zone)
    }

    /** 卡片标题：跟着卡头的支出/收入切换走（与「支出占比/支出数据」同一命名法，非支出即收入）。 */
    fun title(type: TxType): String = if (type == TxType.EXPENSE) "支出趋势" else "收入趋势"
}
