package dev.penned.app.domain

import java.time.YearMonth

/**
 * 统计页「时间切换条」的跨度与文案（AK 轮）——纯函数，表驱动单测。
 *
 * 条从**本账本最早有流水的月份**一直排到**本月**（新→旧），并兜底"至少最近 12 个月 / 5 年"：
 * 还没记账或只有三两笔时也滑得动，不会出现一格死条。
 * 未来月份一律不进条（统计未来一定是空的），所以条的最左端就是本月/今年。
 */
object StatsStrip {

    /** 月份条下限：保证最近 12 个月可点。 */
    const val MIN_MONTHS = 12

    /** 年份条下限：保证最近 5 年可点。 */
    const val MIN_YEARS = 5

    /**
     * 月份条：新→旧，第一个是本月。参数 [include] 是"当前统计期所在的月"——
     * 自定义区间切回月视图时锚点可能比最早流水还老，得把它补进条里，否则选中项在条上找不到。
     */
    fun months(earliest: YearMonth?, today: YearMonth, include: YearMonth? = null): List<YearMonth> {
        val floor = today.minusMonths((MIN_MONTHS - 1).toLong())
        var oldest = if (earliest == null || earliest > floor) floor else earliest
        if (include != null && include < oldest) oldest = include
        val newest = if (include != null && include > today) include else today
        val out = mutableListOf<YearMonth>()
        var month = newest
        while (month >= oldest) {
            out += month
            month = month.minusMonths(1)
        }
        return out
    }

    /** 年份条：新→旧，第一个是今年（[include] 同 [months]）。 */
    fun years(earliest: YearMonth?, today: YearMonth, include: Int? = null): List<Int> {
        val floor = today.year - (MIN_YEARS - 1)
        var oldest = if (earliest == null || earliest.year > floor) floor else earliest.year
        if (include != null && include < oldest) oldest = include
        val newest = if (include != null && include > today.year) include else today.year
        return (newest downTo oldest).toList()
    }

    /** 月份格文案：本月 / 上月 / `M月`（跨年也只用 `M月`，年份看左边那列）。 */
    fun monthLabel(month: YearMonth, today: YearMonth): String = when (month) {
        today -> "本月"
        today.minusMonths(1) -> "上月"
        else -> "${month.monthValue}月"
    }

    /** 快速定位弹窗的月份列：今年只到本月（未来月份没有数据可看），往年 1..12。 */
    fun pickerMonths(year: Int, today: YearMonth): List<Int> =
        if (year >= today.year) (1..today.monthValue).toList() else (1..12).toList()

    /** 弹窗里换年之后，把之前选中的月份夹回该年可用的范围（今年最多到本月）。 */
    fun clampMonth(month: Int, year: Int, today: YearMonth): Int =
        month.coerceIn(1, pickerMonths(year, today).last())
}
