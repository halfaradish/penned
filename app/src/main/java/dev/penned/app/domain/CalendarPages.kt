package dev.penned.app.domain

import java.time.YearMonth

/**
 * 日历横向翻月的页↔月映射（M3-17 优化轮 D091）：纯函数，表驱动单测。
 *
 * **页序 = 新→旧**（第 0 页恒是本月）：`HorizontalPager` 用 `reverseLayout` 把它翻成
 * "往右拉 = 看更早的月"，滑到头（本月）就动不了了——账号里没有未来月的数据，翻过去只会是一屏空网格。
 * 新→旧还有一个结构上的好处：跨度变长（最早流水晚到）时**新的一头不动**，第 0 页永远是本月，
 * pager 的页码不会整体平移。
 *
 * 跨度与统计页的时间切换条**共用同一份口径**（[StatsStrip.months]）：
 * 下界 = 本账本最早有账的月，兜底最近 [StatsStrip.MIN_MONTHS] 个月；上界 = 本月。
 */
object CalendarPages {

    /** 可翻的月份，新→旧（第 0 页恒是本月）。 */
    fun months(earliest: YearMonth?, today: YearMonth): List<YearMonth> =
        StatsStrip.months(earliest, today)

    /** 某月在条上是第几页；不在条上时夹到最近的端（防外部传进条外的月份时崩）。 */
    fun pageOf(month: YearMonth, months: List<YearMonth>): Int = when {
        months.isEmpty() -> 0
        month >= months.first() -> 0
        month <= months.last() -> months.lastIndex
        else -> months.indexOf(month).coerceAtLeast(0)
    }

    /** 第几页是哪个月（页号越界返回 null，由调用方按空处理）。 */
    fun monthOf(page: Int, months: List<YearMonth>): YearMonth? = months.getOrNull(page)

    /** 选年月弹窗的年份列（新→旧）：条上有哪些年就给哪些年（选不到的年不给，免得选完被夹回来）。 */
    fun years(months: List<YearMonth>): List<Int> = months.map { it.year }.distinct()

    /**
     * 选年月弹窗的月份列：先按统计页同口径（今年只到本月，[StatsStrip.pickerMonths]），
     * 再按"这条上真能翻到"收窄——最早有账那个月之前的月份不列出来。
     */
    fun pickerMonths(year: Int, today: YearMonth, months: List<YearMonth>): List<Int> =
        StatsStrip.pickerMonths(year, today).filter { month ->
            months.any { it.year == year && it.monthValue == month }
        }
}
