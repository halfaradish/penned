package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BudgetMathTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `state computes remaining and fraction`() {
        val table = listOf(
            // amount, spent, remaining, fraction
            Triple(10000L, 2500L, 7500L),
            Triple(10000L, 10000L, 0L),
            Triple(10000L, 12500L, -2500L),
            Triple(0L, 100L, -100L),
        )
        table.forEach { (amount, spent, remaining) ->
            val s = BudgetMath.state(amount, spent)
            assertEquals(remaining, s.remainingCents)
            assertEquals(remaining < 0, s.overspent)
        }
        assertEquals(0.25f, BudgetMath.state(10000, 2500).usedFraction, 0.0001f)
        // 超支时比例封顶 1.0
        assertEquals(1.0f, BudgetMath.state(10000, 12500).usedFraction, 0.0001f)
        // 预算为 0 不除零
        assertEquals(0f, BudgetMath.state(0, 500).usedFraction, 0.0001f)
    }

    @Test
    fun `month window covers whole month in minutes`() {
        val window = BudgetMath.monthWindowMinute(2026, 10, zone)
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(zone).toEpochSecond() / 60, window.first)
        assertEquals(LocalDate.of(2026, 11, 1).atStartOfDay(zone).toEpochSecond() / 60 - 1, window.last)
    }

    @Test
    fun `spent for budget sums category with its direct subs`() {
        val spend = mapOf<String?, Long>(
            "food" to 5000L,
            "dinner" to 2000L,
            "lunch" to 1000L,
            "traffic" to 300L,
            null to 700L, // 未分类
        )
        // 分类预算：本类 + 直接子类
        assertEquals(8000L, BudgetMath.spentForBudget(spend, "food", setOf("dinner", "lunch")))
        // 无子分类
        assertEquals(300L, BudgetMath.spentForBudget(spend, "traffic"))
        // 总预算：全部（含未分类）
        assertEquals(9000L, BudgetMath.spentForBudget(spend, null))
        // 孙分类不计入
        assertEquals(5000L, BudgetMath.spentForBudget(spend, "food", emptySet()))
    }

    @Test
    fun `month anchor is first day of month`() {
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), BudgetMath.monthAnchorEpochDay(LocalDate.of(2026, 10, 15)))
    }
}
