package dev.penned.app.domain

import java.time.YearMonth

/**
 * 日历热力分档（M3-17 优化轮 D091）：**纯口径**，颜色在 `ui/theme/Heat.kt`。
 *
 * 口径三条：
 * - **标尺 = 当月最大额，且支出/收入各用各的尺**——每月都有对比度（这个月都很小也不会全糊成一档），
 *   收入那种"一笔工资顶一个月"的量级也不会把支出全压到第 1 档
 * - **有账至少 1 档、封顶 [LEVELS] 档**（分档是 `ceil(额 × 档数 / 当月最大额)`）
 * - **一天两面都有账时支出优先**（工资日也消费：格子的热力按支出走，与"金额是主角、支出口径为主"的
 *   既有习惯一致；两支的精确数在同一天的日头条里）
 */
object CalendarHeat {

    /** 档位数：4 档（小格子里也分得清，见界面规范 §13·补二十八）。 */
    const val LEVELS = 4

    /** 一天的热力输入：只带要显示的两个数（不依赖 UI 层的日分组类型）。 */
    data class Day(val epochDay: Long, val expenseCents: Long, val incomeCents: Long)

    /** 一格的热力：哪种色相 + 第几档（1..[LEVELS]）。 */
    data class Cell(val expense: Boolean, val level: Int)

    /**
     * 单笔额相对标尺落在第几档：**向上取整**（有账至少 1 档，等于标尺即顶档）。
     * 纯整数运算（金额一律 Long 分，D001）。
     */
    fun level(cents: Long, maxCents: Long): Int {
        if (cents <= 0L) return 1
        if (maxCents <= cents) return LEVELS
        val scaled = (cents * LEVELS + maxCents - 1) / maxCents
        return scaled.coerceIn(1L, LEVELS.toLong()).toInt()
    }

    /**
     * 某月的热力表（键 = epochDay）：标尺只由**当月**的日子决定，带出的上/下月日子不参与；
     * 无账的日子不进表（格子没有热力底）。邻月日子要显示热力时，调用方把它当"那一页的月"再算一次。
     */
    fun monthHeat(days: List<Day>, month: YearMonth): Map<Long, Cell> {
        val inMonth = days.filter { CalendarMath.inMonth(it.epochDay, month) }
        val maxExpense = inMonth.maxOfOrNull { it.expenseCents } ?: 0L
        val maxIncome = inMonth.maxOfOrNull { it.incomeCents } ?: 0L
        val out = mutableMapOf<Long, Cell>()
        inMonth.forEach { day ->
            when {
                day.expenseCents > 0L ->
                    out[day.epochDay] = Cell(expense = true, level = level(day.expenseCents, maxExpense))

                day.incomeCents > 0L ->
                    out[day.epochDay] = Cell(expense = false, level = level(day.incomeCents, maxIncome))

                else -> Unit
            }
        }
        return out
    }
}
