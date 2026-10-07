package dev.penned.app.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsChartsTest {

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
        assertEquals("1", labels.first())
        assertEquals("15", labels[14])
        assertEquals("31", labels.last())

        // 跨月（9/28 起 7 天）也是日号
        val crossMonth = dayAxisLabels(LocalDate.of(2026, 9, 28).toEpochDay(), 7)
        assertEquals(listOf("28", "29", "30", "1", "2", "3", "4"), crossMonth)
    }

    @Test
    fun `day axis labels switch to months on long windows`() {
        // 一整年：只标月份（跨季度日子号会重复，看不出是哪月）
        val labels = dayAxisLabels(LocalDate.of(2026, 1, 1).toEpochDay(), 365)
        assertEquals(365, labels.size)
        assertEquals("1月", labels.first())
        assertEquals("12月", labels.last())
    }

    @Test
    fun `month axis labels are twelve months`() {
        assertEquals(listOf("1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月"), monthAxisLabels())
    }
}
