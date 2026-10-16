package dev.penned.app.domain

import java.time.YearMonth

/**
 * 日历横向翻月的页↔月映射（M3-17 优化轮 D091）：纯函数，表驱动单测。
 *
 * **页序 = 旧→新**（第 0 页是最老的月、最后一页恒是本月）：左右滑语义就是"左滑看下一个月"，
 * 滑到头（本月）不能再往未来——账号里没有未来月的数据，翻过去只会是一屏空网格。
 *
 * 跨度与统计页的时间切换条**同一份口径**（[StatsStrip.months]，新→旧；这里翻过来）：
 * 下界 = 本账本最早有账的月，兜底最近 [StatsStrip.MIN_MONTHS] 个月；上界 = 本月。
 */
object CalendarPages {

    /** 可翻的月份，旧→新（最后一页恒是本月）。 */
    fun months(earliest: YearMonth?, today: YearMonth): List<YearMonth> =
        StatsStrip.months(earliest, today).reversed()

    /** 某月在条上是第几页；不在条上时夹到最近的端（防外部传进条外的月份时崩）。 */
    fun pageOf(month: YearMonth, months: List<YearMonth>): Int = when {
        months.isEmpty() -> 0
        month <= months.first() -> 0
        month >= months.last() -> months.lastIndex
        else -> months.indexOf(month).coerceAtLeast(0)
    }

    /** 第几页是哪个月（页号越界返回 null，由调用方按空处理）。 */
    fun monthOf(page: Int, months: List<YearMonth>): YearMonth? = months.getOrNull(page)
}
