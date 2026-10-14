package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 债务页面的文案口径（M3-4）：日期、两数摘要、详情页主按钮用词。表驱动。 */
class DebtTextTest {

    private val today = LocalDate.of(2026, 10, 14)

    @Test
    fun `完整日期`() {
        val cases = listOf(
            LocalDate.of(2026, 10, 14) to "2026年10月14日",
            LocalDate.of(2026, 1, 5) to "2026年1月5日",
            LocalDate.of(2025, 12, 31) to "2025年12月31日",
            LocalDate.of(2028, 2, 29) to "2028年2月29日",
        )
        cases.forEach { (date, expected) ->
            assertEquals(expected, DebtText.fullDate(date.toEpochDay()))
        }
    }

    @Test
    fun `到期日：没设、当年、跨年`() {
        val cases = listOf(
            null to "未设到期",
            LocalDate.of(2026, 10, 14).toEpochDay() to "10月14日",
            LocalDate.of(2026, 12, 31).toEpochDay() to "12月31日",
            // 跨年（今年 2026，到期 2027）才带年份
            LocalDate.of(2027, 1, 1).toEpochDay() to "2027年1月1日",
            LocalDate.of(2025, 12, 31).toEpochDay() to "2025年12月31日",
        )
        cases.forEach { (day, expected) ->
            assertEquals(expected, DebtText.dueLabel(day, today))
        }
    }

    @Test
    fun `本金与已还摘要`() {
        assertEquals("本金 1,000.00 · 已还 400.00", DebtText.principalRepaidLabel(100_000L, 40_000L))
        assertEquals("本金 0.00 · 已还 0.00", DebtText.principalRepaidLabel(0L, 0L))
        assertEquals("本金 300.00 · 已还 0.00", DebtText.principalRepaidLabel(30_000L, 0L))
    }

    @Test
    fun `详情页主按钮用词跟着方向走`() {
        assertEquals("记一笔还款", DebtText.settleCtaLabel(DebtDirection.BORROW))
        assertEquals("记一笔收款", DebtText.settleCtaLabel(DebtDirection.LEND))
    }
}
