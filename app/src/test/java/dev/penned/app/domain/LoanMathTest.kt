package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 房贷两公式（M3-11）表驱动：与手算基准对账（含 0 利率、1 期、360 期），
 * 并钉住三条恒等式：Σ本金 = 本金、末期剩余 = 0、Σ还款 = Σ本金 + Σ利息。
 */
class LoanMathTest {

    private fun assertConsistent(plan: LoanMath.Plan) {
        assertEquals("Σ本金 = 本金", plan.principalCents, plan.periods.sumOf { it.principalCents })
        assertEquals("末期剩余 = 0", 0L, plan.periods.last().remainingCents)
        assertEquals("Σ还款 = Σ本金 + Σ利息", plan.periods.sumOf { it.paymentCents }, plan.totalPaymentCents)
        assertTrue("每期剩余单调不增", plan.periods.zipWithNext().all { (a, b) -> b.remainingCents <= a.remainingCents })
    }

    @Test
    fun `等额本息：手算基准 100 万 30 年 4_9%`() {
        // 基准：P=1,000,000 元、年利率 4.9%、360 期 → 月供约 5307.27 元（标准房贷计算器口径）
        val plan = LoanMath.plan(
            principalCents = 100_000_000L,
            annualRatePercent = 4.9,
            periods = 360,
            method = LoanMath.Method.EQUAL_INSTALLMENT,
        )
        assertConsistent(plan)
        assertEquals(360, plan.periods.size)
        assertTrue("月供约 5307.27", abs(plan.firstPaymentCents - 530_727L) <= 1L)
        assertEquals("首期利息 ≈ 4083.33", 408_333L, plan.periods.first().interestCents)
        assertTrue("总利息约 91.06 万", abs(plan.totalInterestCents - 91_061_600L) <= 200_000L)
        assertTrue(
            "等额本息月供恒定",
            plan.periods.dropLast(1).all { abs(it.paymentCents - plan.firstPaymentCents) <= 1L },
        )
    }

    @Test
    fun `等额本金：手算基准 100 万 30 年 4_9%`() {
        val plan = LoanMath.plan(
            principalCents = 100_000_000L,
            annualRatePercent = 4.9,
            periods = 360,
            method = LoanMath.Method.EQUAL_PRINCIPAL,
        )
        assertConsistent(plan)
        assertEquals("每期本金 = 100000000/360 向下取整", 277_777L, plan.periods[1].principalCents)
        assertEquals("首期利息 ≈ 4083.33", 408_333L, plan.periods.first().interestCents)
        // 首期本金含零头 280 分：278_057 + 408_333 = 686_390
        assertEquals("首期月供 = 首期本金（含零头）+ 首期利息", 686_390L, plan.firstPaymentCents)
        assertEquals("末月月供 = 末期本金 + 末期利息", 277_777L + 1_134L, plan.lastPaymentCents)
        assertTrue("等额本金月供递减", plan.periods.zipWithNext().all { (a, b) -> b.paymentCents <= a.paymentCents })
        assertTrue("总利息约 73.7 万", abs(plan.totalInterestCents - 73_704_166L) <= 200_000L)
    }

    @Test
    fun `0 利率：两种方式都是本金平摊、总利息 0`() {
        val equal = LoanMath.plan(120_000L, 0.0, 12, LoanMath.Method.EQUAL_INSTALLMENT)
        assertConsistent(equal)
        assertEquals(10_000L, equal.firstPaymentCents)
        assertEquals(0L, equal.totalInterestCents)
        assertEquals(120_000L, equal.totalPaymentCents)

        val principal = LoanMath.plan(120_000L, 0.0, 12, LoanMath.Method.EQUAL_PRINCIPAL)
        assertConsistent(principal)
        assertEquals(10_000L, principal.firstPaymentCents)
        assertEquals(0L, principal.totalInterestCents)
    }

    @Test
    fun `1 期：利息 = 本金 × 月利率，末期把本金还清`() {
        val equal = LoanMath.plan(1_000_000L, 12.0, 1, LoanMath.Method.EQUAL_INSTALLMENT)
        assertConsistent(equal)
        assertEquals(1, equal.periods.size)
        assertEquals("月利率 1%", 10_000L, equal.periods.first().interestCents)
        assertEquals(1_000_000L, equal.periods.first().principalCents)

        val principal = LoanMath.plan(1_000_000L, 12.0, 1, LoanMath.Method.EQUAL_PRINCIPAL)
        assertConsistent(principal)
        assertEquals(1_000_000L, principal.periods.first().principalCents)
        assertEquals(10_000L, principal.periods.first().interestCents)
    }

    @Test
    fun `零头抹平：本金除不尽时也刚好还完`() {
        // 1000 元分 3 期、0 利率：零头（1 分）放在第一期
        val plan = LoanMath.plan(100_000L, 0.0, 3, LoanMath.Method.EQUAL_PRINCIPAL)
        assertConsistent(plan)
        assertEquals(listOf(33_334L, 33_333L, 33_333L), plan.periods.map { it.principalCents })

        // 等额本息同样刚好还完（末期用剩余本金兜底）
        val equal = LoanMath.plan(100_000L, 6.0, 3, LoanMath.Method.EQUAL_INSTALLMENT)
        assertConsistent(equal)
        assertEquals(0L, equal.periods.last().remainingCents)
    }

    @Test
    fun `360 期：计划长度与末期归零（两种方式）`() {
        listOf(LoanMath.Method.EQUAL_INSTALLMENT, LoanMath.Method.EQUAL_PRINCIPAL).forEach { method ->
            val plan = LoanMath.plan(50_000_000L, 3.5, 360, method)
            assertEquals(360, plan.periods.size)
            assertConsistent(plan)
        }
    }

    @Test
    fun `非法输入：本金或期数非正返回空计划`() {
        assertTrue(LoanMath.plan(0L, 5.0, 12, LoanMath.Method.EQUAL_INSTALLMENT).periods.isEmpty())
        assertTrue(LoanMath.plan(100_000L, 5.0, 0, LoanMath.Method.EQUAL_PRINCIPAL).periods.isEmpty())
    }

    @Test
    fun `月供公式：0 利率退化成本金除期数`() {
        assertEquals(10_000L, LoanMath.equalInstallmentPaymentCents(120_000L, 0.0, 12))
        assertEquals(0L, LoanMath.equalInstallmentPaymentCents(120_000L, 5.0, 0))
    }

    @Test
    fun `逐期还款日：锚定日 31 遇短月夹月末、后续回到 31`() {
        // 2026-01-31 起：2 月夹到 28、3 月回到 31、4 月夹到 30
        val dates = LoanMath.periodDates(java.time.LocalDate.of(2026, 1, 31).toEpochDay(), 4)
            .map { java.time.LocalDate.ofEpochDay(it) }
        assertEquals(
            listOf(
                java.time.LocalDate.of(2026, 1, 31),
                java.time.LocalDate.of(2026, 2, 28),
                java.time.LocalDate.of(2026, 3, 31),
                java.time.LocalDate.of(2026, 4, 30),
            ),
            dates,
        )
    }

    @Test
    fun `逐期还款日：跨年与闰年 2 月 29`() {
        // 2027-12-15 起跨年
        val crossYear = LoanMath.periodDates(java.time.LocalDate.of(2027, 12, 15).toEpochDay(), 3)
            .map { java.time.LocalDate.ofEpochDay(it) }
        assertEquals(java.time.LocalDate.of(2028, 1, 15), crossYear[1])
        // 2028 是闰年：1/29 起，2 月对齐到 2/29
        val leap = LoanMath.periodDates(java.time.LocalDate.of(2028, 1, 29).toEpochDay(), 3)
            .map { java.time.LocalDate.ofEpochDay(it) }
        assertEquals(java.time.LocalDate.of(2028, 2, 29), leap[1])
        // 平年同一起点：2 月夹到 28
        val common = LoanMath.periodDates(java.time.LocalDate.of(2027, 1, 29).toEpochDay(), 3)
            .map { java.time.LocalDate.ofEpochDay(it) }
        assertEquals(java.time.LocalDate.of(2027, 2, 28), common[1])
    }

    @Test
    fun `逐期还款日：期数非正返回空、期数 1 只有首期`() {
        assertTrue(LoanMath.periodDates(20_000L, 0).isEmpty())
        assertTrue(LoanMath.periodDates(20_000L, -3).isEmpty())
        assertEquals(listOf(20_000L), LoanMath.periodDates(20_000L, 1))
    }
}
