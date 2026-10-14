package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 周期期次推进（M3-6）表驱动：日/周/月/年 + **月对齐**（29/30/31 遇短月取月末、后续期回到锚定日）
 * + 跨月/跨年/闰年 + 打开即补的到期列表（不产未来、受 end 与上限约束）。
 */
class RecurringMathTest {

    private fun day(year: Int, month: Int, d: Int) = LocalDate.of(year, month, d).toEpochDay()

    @Test
    fun `按天与按周：直接加天数`() {
        assertEquals(day(2026, 10, 15), RecurringMath.advance(day(2026, 10, 14), RecurringPeriod.DAILY, 1, 14))
        assertEquals(day(2026, 10, 17), RecurringMath.advance(day(2026, 10, 14), RecurringPeriod.DAILY, 3, 14))
        assertEquals(day(2026, 10, 21), RecurringMath.advance(day(2026, 10, 14), RecurringPeriod.WEEKLY, 1, 14))
        // 跨月
        assertEquals(day(2026, 11, 3), RecurringMath.advance(day(2026, 10, 27), RecurringPeriod.WEEKLY, 1, 27))
    }

    @Test
    fun `按月：月末对齐到短月的最后一天`() {
        val cases = listOf(
            // 起始 31 日：1/31 → 2/28（平年）
            Triple(day(2026, 1, 31), day(2026, 2, 28), 31),
            // 闰年 2/29
            Triple(day(2028, 1, 31), day(2028, 2, 29), 31),
            // 起始 30 日：3/30 → 4/30（4 月有 30 天，不夹）
            Triple(day(2026, 3, 30), day(2026, 4, 30), 30),
            // 起始 29 日：平年 2 月夹到 28
            Triple(day(2027, 1, 29), day(2027, 2, 28), 29),
        )
        cases.forEach { (from, expected, anchor) ->
            assertEquals("从 $from（锚定 $anchor）", expected, RecurringMath.advance(from, RecurringPeriod.MONTHLY, 1, anchor))
        }
    }

    @Test
    fun `按月：夹过短月后，后续期回到锚定日`() {
        // 1/31 → 2/28 → 3/31（不是 3/28）
        val feb = RecurringMath.advance(day(2026, 1, 31), RecurringPeriod.MONTHLY, 1, 31)
        assertEquals(day(2026, 2, 28), feb)
        assertEquals(day(2026, 3, 31), RecurringMath.advance(feb, RecurringPeriod.MONTHLY, 1, 31))
        // 连续推一年：1/31 的每一期都落在当月最后一天或 31 日
        var cursor = day(2026, 1, 31)
        val months = mutableListOf<LocalDate>()
        repeat(12) {
            cursor = RecurringMath.advance(cursor, RecurringPeriod.MONTHLY, 1, 31)
            months += LocalDate.ofEpochDay(cursor)
        }
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 1), months.map { it.monthValue })
        assertTrue("二月是 28（2026 平年）", months.first { it.monthValue == 2 }.dayOfMonth == 28)
        assertEquals("三月回到 31", 31, months.first { it.monthValue == 3 }.dayOfMonth)
        assertEquals("四月 30", 30, months.first { it.monthValue == 4 }.dayOfMonth)
    }

    @Test
    fun `按年：2 月 29 起，平年落到 2 月 28、闰年回到 29`() {
        val y1 = RecurringMath.advance(day(2028, 2, 29), RecurringPeriod.YEARLY, 1, 29)
        assertEquals(day(2029, 2, 28), y1)
        val y2 = RecurringMath.advance(y1, RecurringPeriod.YEARLY, 1, 29)
        assertEquals(day(2030, 2, 28), y2)
        // 2032 是闰年 → 回到 29
        var cursor = y2
        repeat(2) { cursor = RecurringMath.advance(cursor, RecurringPeriod.YEARLY, 1, 29) }
        assertEquals(day(2032, 2, 29), cursor)
    }

    @Test
    fun `打开即补：只列到期（≤ 今天）的期次，未来不产`() {
        val today = day(2026, 10, 14)
        val dates = RecurringMath.dueDates(
            nextEpochDay = day(2026, 10, 10),
            period = RecurringPeriod.DAILY,
            interval = 1,
            anchorDay = 10,
            todayEpochDay = today,
        )
        assertEquals(listOf(10, 11, 12, 13, 14).map { day(2026, 10, it) }, dates)
        assertTrue("没有未来的期次", dates.all { it <= today })
    }

    @Test
    fun `打开即补：没有到期的期次就空手而归`() {
        val dates = RecurringMath.dueDates(
            nextEpochDay = day(2026, 10, 20),
            period = RecurringPeriod.MONTHLY,
            interval = 1,
            anchorDay = 20,
            todayEpochDay = day(2026, 10, 14),
        )
        assertEquals(emptyList<Long>(), dates)
    }

    @Test
    fun `打开即补：受结束日与单次上限约束`() {
        // 结束日挡在前面：只补到结束日
        val capped = RecurringMath.dueDates(
            nextEpochDay = day(2026, 10, 1),
            period = RecurringPeriod.DAILY,
            interval = 1,
            anchorDay = 1,
            todayEpochDay = day(2026, 12, 31),
            endEpochDay = day(2026, 10, 5),
        )
        assertEquals(listOf(1, 2, 3, 4, 5).map { day(2026, 10, it) }, capped)

        // 单次上限：一次最多 MAX_CATCH_UP 期，剩下的下次继续
        val many = RecurringMath.dueDates(
            nextEpochDay = day(2024, 1, 1),
            period = RecurringPeriod.DAILY,
            interval = 1,
            anchorDay = 1,
            todayEpochDay = day(2026, 12, 31),
            maxCount = 10,
        )
        assertEquals(10, many.size)
        assertEquals(day(2024, 1, 1), many.first())
        assertEquals(day(2024, 1, 10), many.last())
    }

    @Test
    fun `周期文案：单数与多期`() {
        assertEquals("每天", RecurringMath.periodLabel(RecurringPeriod.DAILY, 1))
        assertEquals("每周", RecurringMath.periodLabel(RecurringPeriod.WEEKLY, 1))
        assertEquals("每月", RecurringMath.periodLabel(RecurringPeriod.MONTHLY, 1))
        assertEquals("每年", RecurringMath.periodLabel(RecurringPeriod.YEARLY, 1))
        assertEquals("每 2 周", RecurringMath.periodLabel(RecurringPeriod.WEEKLY, 2))
        assertEquals("每 3 个月", RecurringMath.periodLabel(RecurringPeriod.MONTHLY, 3))
    }
}
