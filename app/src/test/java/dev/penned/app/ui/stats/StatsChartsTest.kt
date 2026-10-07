package dev.penned.app.ui.stats

import dev.penned.app.domain.TrendAxis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsChartsTest {

    /** 测试里的"今天"：浮层日期里非本年才带年份，固定住才可复现。 */
    private val today = LocalDate.of(2026, 10, 7)

    @Test
    fun `label indices cover short windows fully`() {
        assertEquals((0 until 7).toList(), labelIndices(7))
        assertEquals((0 until 12).toList(), labelIndices(12))
        assertEquals(emptyList<Int>(), labelIndices(0))
    }

    @Test
    fun `label indices thin out long windows and keep the last one`() {
        // 31 天：每 6 根一个 + 最后一根（1/7/13/19/25/31）
        assertEquals(listOf(0, 6, 12, 18, 24, 30), labelIndices(31))
        // 恰好整除时也不重复最后一根
        assertEquals(listOf(0, 6, 12, 18, 24, 30), labelIndices(31))
        // 30 天：step=5 → 0/5/…/25 + 29
        assertEquals(listOf(0, 5, 10, 15, 20, 25, 29), labelIndices(30))
        // 365 天：step=61 → 稀疏 + 最后一天
        val yearly = labelIndices(365)
        assertEquals(0, yearly.first())
        assertEquals(364, yearly.last())
        assertEquals(7, yearly.size)
    }

    @Test
    fun `day axis labels use day of month inside a quarter`() {
        // 2026-10-01 起 31 天 → 1…31（参考图口径）
        val labels = dayAxisLabels(LocalDate.of(2026, 10, 1).toEpochDay(), 31)
        assertEquals(31, labels.size)
        assertEquals("1", labels.first().bottom)
        assertEquals("15", labels[14].bottom)
        assertEquals("31", labels.last().bottom)
        // 按住浮层用的是整天日期（非本年才带年份）
        assertEquals("10月1日", labels.first().tooltip)

        // 跨月（9/28 起 7 天）也是日号
        val crossMonth = dayAxisLabels(LocalDate.of(2026, 9, 28).toEpochDay(), 7)
        assertEquals(
            listOf("28", "29", "30", "1", "2", "3", "4"),
            crossMonth.map { it.bottom },
        )
        assertEquals("9月28日", crossMonth.first().tooltip)
    }

    @Test
    fun `day axis labels switch to months on long windows`() {
        // 一整年：只标月份（跨季度日子号会重复，看不出是哪月）
        val labels = dayAxisLabels(LocalDate.of(2026, 1, 1).toEpochDay(), 365)
        assertEquals(365, labels.size)
        assertEquals("1月", labels.first().bottom)
        assertEquals("12月", labels.last().bottom)
        // 浮层仍给完整日期；跨年（今天是 2026 年）时带年份
        assertEquals(
            "2025年12月31日",
            dayAxisLabels(LocalDate.of(2025, 12, 1).toEpochDay(), 31, today = LocalDate.of(2026, 1, 15)).last().tooltip,
        )
    }

    @Test
    fun `trend axis uses two lines for short day windows`() {
        // 近七日（2026-10-01 起）：上行周几、下行 月/日
        val week = trendAxisLabels(TrendAxis.Days(LocalDate.of(2026, 10, 1).toEpochDay(), 7), today)
        assertEquals(7, week.size)
        assertEquals(AxisLabel(bottom = "10/1", top = "周四", tooltip = "10月1日"), week.first())
        assertEquals(AxisLabel(bottom = "10/7", top = "周三", tooltip = "10月7日"), week.last())

        // 跨月也照样两行（9/28 周一 … 10/4 周日）
        val crossMonth = trendAxisLabels(TrendAxis.Days(LocalDate.of(2026, 9, 28).toEpochDay(), 7), today)
        assertEquals(AxisLabel(bottom = "9/28", top = "周一", tooltip = "9月28日"), crossMonth.first())
        assertEquals(AxisLabel(bottom = "10/4", top = "周日", tooltip = "10月4日"), crossMonth.last())
    }

    @Test
    fun `trend axis falls back to one line month labels on long windows`() {
        // 超过两个月：日号会重复，退回单行月份（与分类详细页同口径）
        val year = trendAxisLabels(TrendAxis.Days(LocalDate.of(2025, 9, 1).toEpochDay(), 365), today)
        assertEquals(365, year.size)
        assertEquals(AxisLabel(bottom = "9月", tooltip = "2025年9月1日"), year.first())
        // 收尾这天落在今年 → 浮层不带年份（与自定义区间 chip 同口径）
        assertEquals(AxisLabel(bottom = "8月", tooltip = "8月31日"), year.last())

        // 恰好 62 天仍是两行（2026-09-01 是周二）
        val twoMonths = trendAxisLabels(TrendAxis.Days(LocalDate.of(2026, 9, 1).toEpochDay(), 62), today)
        assertEquals("9/1", twoMonths.first().bottom)
        assertEquals("周二", twoMonths.first().top)
    }

    @Test
    fun `trend axis uses single line month labels for the year view`() {
        val months = trendAxisLabels(TrendAxis.Months(2024), today)
        assertEquals(12, months.size)
        assertEquals(AxisLabel(bottom = "1月", tooltip = "2024年1月"), months.first())
        assertEquals(AxisLabel(bottom = "12月", tooltip = "2024年12月"), months.last())
    }

    // —— 纵轴：原点 / 最大值 / 非零最小值 ——

    @Test
    fun `chart scale picks max and smallest non zero bar`() {
        assertEquals(ChartScale(max = 15_30L, minNonZero = 2_50L), chartScale(listOf(0, 2_50, 15_30, 0)))
        // 全 0 / 空：没有非零最小值
        assertEquals(ChartScale(max = 0L, minNonZero = null), chartScale(listOf(0, 0)))
        assertEquals(ChartScale(max = 0L, minNonZero = null), chartScale(emptyList()))
        // 只有一根柱：最大值与非零最小值是同一个数
        assertEquals(ChartScale(max = 333L, minNonZero = 333L), chartScale(listOf(0, 333)))
    }

    @Test
    fun `chart ticks are max, smallest non zero, and origin`() {
        val ticks = chartTicks(ChartScale(max = 1000L, minNonZero = 250L))
        assertEquals(listOf("10.00", "2.50", "0"), ticks.map { it.label })
        assertEquals(listOf(1000L, 250L, 0L), ticks.map { it.cents })
        assertEquals(listOf(1f, 0.25f, 0f), ticks.map { it.fraction })

        // 最大值 == 非零最小值（只有一根柱）：只标两条
        assertEquals(listOf("3.33", "0"), chartTicks(ChartScale(max = 333L, minNonZero = 333L)).map { it.label })
        // 没有数据：没有刻度
        assertEquals(emptyList<AxisTick>(), chartTicks(ChartScale(max = 0L, minNonZero = null)))
    }

    @Test
    fun `tick centers map values onto the panel and keep a gap`() {
        // 面板 100px、行高 14px：最大在顶、0 在底、25% 落在中间
        val normal = tickCentersPx(
            ticks = chartTicks(ChartScale(max = 1000L, minNonZero = 250L)),
            panelHeightPx = 100f,
            labelHeightPx = 14f,
        )
        assertEquals(listOf(0f, 75f, 100f), normal)

        // 最小值远小于最大值（1000 里的 1）：会与 0 撞车 → 上提到 0 之上一个行高 + 2
        val crowded = tickCentersPx(
            ticks = chartTicks(ChartScale(max = 1000L, minNonZero = 1L)),
            panelHeightPx = 100f,
            labelHeightPx = 14f,
        )
        assertEquals(100f, crowded[2])
        assertEquals(100f - 16f, crowded[1], 0.01f)
        assertEquals(0f, crowded[0])
    }

    @Test
    fun `press position maps to the bar under the finger`() {
        // 刻度栏 40px、一格 10px：栏里也算第 0 根，右边溢出算最后一根
        assertEquals(0, indexAt(0f, gutterPx = 40f, slotPx = 10f, count = 5))
        assertEquals(0, indexAt(45f, 40f, 10f, 5))
        assertEquals(2, indexAt(65f, 40f, 10f, 5))
        assertEquals(4, indexAt(999f, 40f, 10f, 5))
    }
}
