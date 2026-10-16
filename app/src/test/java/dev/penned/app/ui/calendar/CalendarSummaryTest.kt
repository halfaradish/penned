package dev.penned.app.ui.calendar

import dev.penned.app.ui.home.HomeGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 月小字总结的文案（优化轮）：三个数、负数带减号、空月照渲染 0.00 三项、超大额退化规则。 */
class CalendarSummaryTest {

    @Test
    fun `三段文案：收绿支红余灰的三个数，负数带减号`() {
        val cases = listOf(
            // 收、支、期望三段（带千分位）
            Triple(300_000L, 600L, listOf("收 3,000.00", "支 6.00", "余 2,994.00")),
            Triple(0L, 0L, listOf("收 0.00", "支 0.00", "余 0.00")),            // 空月：三项照渲染
            Triple(100L, 1600L, listOf("收 1.00", "支 16.00", "余 -15.00")),    // 负数带减号
            Triple(123_456_789L, 987_654_321L, listOf("收 1,234,567.89", "支 9,876,543.21", "余 -8,641,975.32")),
        )
        cases.forEach { (income, expense, expected) ->
            val totals = HomeGroups.MonthTotals(incomeCents = income, expenseCents = expense)
            assertEquals("$income / $expense", expected, CalendarSummary.labels(totals, grouped = true))
        }
    }

    @Test
    fun `退化：整行一起去掉千分位（不混用），精确数仍带两位小数`() {
        val totals = HomeGroups.MonthTotals(incomeCents = 123_456_789L, expenseCents = 600L)
        val ungrouped = CalendarSummary.labels(totals, grouped = false)
        assertEquals(listOf("收 1234567.89", "支 6.00", "余 1234561.89"), ungrouped)
        assertTrue(ungrouped.none { it.contains(",") })
        // 带千分位那份与退化那份是同一批数（只是分隔符不同）
        assertEquals(
            CalendarSummary.labels(totals, grouped = true).map { it.replace(",", "") },
            ungrouped,
        )
    }

    @Test
    fun `量宽与判定：三段加两处间隔，超宽就退化`() {
        // 三段各 100px、间隔 16px → 100*3 + 16*2 = 332
        assertEquals(332, CalendarSummary.totalWidth(listOf(100, 100, 100), 16))
        // 只有一段时不加间隔
        assertEquals(100, CalendarSummary.totalWidth(listOf(100), 16))
        assertEquals(0, CalendarSummary.totalWidth(emptyList(), 16))
        assertTrue(CalendarSummary.fitsWithGrouping(groupedWidthPx = 332, availablePx = 332))
        assertTrue(CalendarSummary.fitsWithGrouping(groupedWidthPx = 300, availablePx = 332))
        assertFalse(CalendarSummary.fitsWithGrouping(groupedWidthPx = 333, availablePx = 332))
    }
}
