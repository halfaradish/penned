package dev.penned.app.ui.calendar

import dev.penned.app.domain.Money
import dev.penned.app.ui.home.HomeGroups

/**
 * 月小字总结（优化轮）：年月下方一行，`收 3,000.00 / 支 6.00 / 余 2,994.00`。
 *
 * - **口径**：三个数都来自 `HomeGroups.monthTotalsOf`（只算当月——网格带出的上/下月日子不算），
 *   余额 = 收 − 支，负数照带减号（颜色固定是弱灰，不随正负变）
 * - **空月照渲染 0.00 三项**：标题下面那行不忽有忽无，行高不跳
 * - **超大额的退化规则**：三项先带千分位，整行量不下就**一起去掉千分位**（同一视觉语言，不混用）；
 *   再放不下交给布局层的省略号——精确数在选中那天的日头条里（与格子只给颜色不给前缀同一条思路）
 */
internal object CalendarSummary {

    /** 三段文案；[grouped] = 是否带千分位（退化时整行一起去掉）。 */
    fun labels(totals: HomeGroups.MonthTotals, grouped: Boolean): List<String> = listOf(
        "收 ${Money.format(totals.incomeCents, grouped)}",
        "支 ${Money.format(totals.expenseCents, grouped)}",
        "余 ${Money.format(HomeGroups.balanceOf(totals), grouped)}",
    )

    /**
     * 三个数并排的近似宽度（像素）：三段各量一次 + 两处间隔。
     * 测量交给调用方（`rememberTextMeasurer`），这里只做"加起来"这一步，方便单测。
     */
    fun totalWidth(labelWidths: List<Int>, gapPx: Int): Int =
        labelWidths.sum() + gapPx * (labelWidths.size - 1).coerceAtLeast(0)

    /** 带千分位放得下就用带千分位的（退化只发生一次，不做多级）。 */
    fun fitsWithGrouping(groupedWidthPx: Int, availablePx: Int): Boolean = groupedWidthPx <= availablePx
}
