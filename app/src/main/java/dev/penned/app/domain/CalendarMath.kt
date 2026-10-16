package dev.penned.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历视图的日期计算（M3-17，纯函数 + 表驱动测试在 `CalendarMathTest`）。
 *
 * 口径：
 * - **周一开头**（中文习惯"一 二 三 四 五 六 日"），所以首格是"该月 1 号所在周的周一"
 * - 网格行数按需（5 行或 6 行）：`ceil((首日偏移 + 当月天数) / 7)`——不硬凑 6 行，
 *   免得 2 月这种"刚好 4 行就够"的月份底下多出一整行空白
 * - 网格里会带出**上/下月的日子**（灰掉显示，可点），`inMonth` 用来判断"是不是当月"
 * - **不做农历/节气/节日**（规格 M3-17 的明确边界）
 */
object CalendarMath {

    /** 星期表头（周一开头）。 */
    val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

    /** 该月 1 号是周几的**偏移**（周一 = 0，周日 = 6）。 */
    fun firstDayOffset(month: YearMonth): Int =
        month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value

    /** 当月天数（闰年 2 月 = 29）。 */
    fun daysInMonth(month: YearMonth): Int = month.lengthOfMonth()

    /** 网格行数：5 或 6（按内容算，不硬凑）。 */
    fun rowCount(month: YearMonth): Int = (firstDayOffset(month) + daysInMonth(month) + 6) / 7

    /** 网格格数 = 行数 × 7。 */
    fun cellCount(month: YearMonth): Int = rowCount(month) * 7

    /** 网格第一格的日期（该月 1 号所在周的周一）。 */
    fun gridStart(month: YearMonth): Long =
        month.atDay(1).minusDays(firstDayOffset(month).toLong()).toEpochDay()

    /** 网格全部日期（epochDay，含上/下月的日子）。 */
    fun gridDays(month: YearMonth): List<Long> {
        val start = gridStart(month)
        return (0 until cellCount(month)).map { start + it }
    }

    /** 这个 epochDay 属于该月吗（网格里的上/下月日子会返回 false）。 */
    fun inMonth(epochDay: Long, month: YearMonth): Boolean =
        LocalDate.ofEpochDay(epochDay).let { it.year == month.year && it.monthValue == month.monthValue }

    /** 翻月（跨年自动处理）：delta 可正可负。 */
    fun shiftMonth(month: YearMonth, delta: Long): YearMonth = month.plusMonths(delta)

    /**
     * 翻月时选中日怎么走（优化轮 D091）：**跟到新月的同一天号**，该月没有这天就夹到月末
     * （8/31 翻到 2 月 = 2/28，闰年 2/29）。
     *
     * 选中日本来就在目标月里时原样返回——点到网格里上/下月的日子走的就是这条
     * （用户点的就是那一天，不该被"同号"改写）。
     */
    fun followDay(selectedEpochDay: Long, target: YearMonth): Long {
        if (inMonth(selectedEpochDay, target)) return selectedEpochDay
        val dayOfMonth = LocalDate.ofEpochDay(selectedEpochDay).dayOfMonth
        return target.atDay(minOf(dayOfMonth, daysInMonth(target))).toEpochDay()
    }

    /** 该日所在周的周一（收起态的周视图取它）。 */
    fun weekStart(epochDay: Long): Long {
        val date = LocalDate.ofEpochDay(epochDay)
        return date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()).toEpochDay()
    }

    /** 周一开头的七天（与 [WEEKDAY_LABELS] 同口径）；跨月/跨年照常排下去。 */
    fun weekDays(epochDay: Long): List<Long> {
        val start = weekStart(epochDay)
        return (0L until 7L).map { start + it }
    }

    /** 标题：`2026年10月`。 */
    fun monthTitle(month: YearMonth): String = "${month.year}年${month.monthValue}月"

    /**
     * 该月的时间窗（本地墙钟分钟，闭区间）——**只取当月**（不含网格里带出的上下月日子），
     * 与 `StatsPeriods.window` 同一套换算（`AddDate` 的墙钟口径）。
     */
    fun monthWindow(month: YearMonth, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Pair<Long, Long> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val from = first.atStartOfDay(zone).toEpochSecond() / 60
        val to = last.atTime(23, 59).atZone(zone).toEpochSecond() / 60
        return from to to
    }

    /** 网格窗口：把带出的上/下月日子也包进去（网格上的小计要有数）。 */
    fun gridWindow(month: YearMonth, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Pair<Long, Long> {
        val days = gridDays(month)
        val from = LocalDate.ofEpochDay(days.first()).atStartOfDay(zone).toEpochSecond() / 60
        val to = LocalDate.ofEpochDay(days.last()).atTime(23, 59).atZone(zone).toEpochSecond() / 60
        return from to to
    }
}
