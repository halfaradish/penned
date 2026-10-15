package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 信用卡年费到期判定（M3-11）表驱动：跨年、闰年 2/29、收费日 31 日遇短月、已标记的当年不再到期。
 */
class FeeMathTest {

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    @Test
    fun `到期 = 今年过了收费日且今年还没标记过`() {
        val cases = listOf(
            // 今天已过收费日、今年没标记 → 到期
            Triple("收费日当天", day(2026, 3, 12) to Triple(2025, 3, 12), true),
            Triple("收费日之后", day(2026, 4, 1) to Triple(2025, 3, 12), true),
            // 还没到收费日 → 不到期
            Triple("收费日之前", day(2026, 3, 1) to Triple(2025, 3, 12), false),
            // 今年已标记 → 不到期（跨年后再到期）
            Triple("今年已标记", day(2026, 4, 1) to Triple(2026, 3, 12), false),
        )
        cases.forEach { (name, input, expected) ->
            val (today, spec) = input
            val (lastPaidYear, month, dayOfMonth) = spec
            assertEquals(
                name,
                expected,
                FeeMath.isDue(
                    feeCents = 48_000L,
                    feeMonth = month,
                    feeDay = dayOfMonth,
                    lastPaidYear = lastPaidYear,
                    today = today,
                ),
            )
        }
    }

    @Test
    fun `收费日 29_30_31 遇短月夹到当月最后一天`() {
        // 2/29 的收费日：平年按 2/28 到期、闰年按 2/29
        assertFalse("平年 2/27 还没到", FeeMath.isDue(48_000L, 2, 29, null, day(2027, 2, 27)))
        assertTrue("平年 2/28 到期", FeeMath.isDue(48_000L, 2, 29, null, day(2027, 2, 28)))
        assertFalse("闰年 2/28 还没到", FeeMath.isDue(48_000L, 2, 29, null, day(2028, 2, 28)))
        assertTrue("闰年 2/29 到期", FeeMath.isDue(48_000L, 2, 29, null, day(2028, 2, 29)))
        // 31 日在 4 月 → 4/30
        assertFalse(FeeMath.isDue(48_000L, 4, 31, null, day(2026, 4, 29)))
        assertTrue(FeeMath.isDue(48_000L, 4, 31, null, day(2026, 4, 30)))
    }

    @Test
    fun `跨年：去年标记过，今年照样到期`() {
        assertTrue(FeeMath.isDue(48_000L, 1, 5, lastPaidYear = 2025, today = day(2026, 1, 5)))
    }

    @Test
    fun `没设年费或没设日期：一律不到期`() {
        assertFalse("年费 0", FeeMath.isDue(0L, 3, 12, null, day(2026, 12, 31)))
        assertFalse("没设月", FeeMath.isDue(48_000L, null, 12, null, day(2026, 12, 31)))
        assertFalse("没设日", FeeMath.isDue(48_000L, 3, null, null, day(2026, 12, 31)))
    }

    @Test
    fun `收费日换算与文案`() {
        assertEquals(day(2026, 2, 28), FeeMath.feeDateIn(2026, 2, 31))
        assertEquals(day(2028, 2, 29), FeeMath.feeDateIn(2028, 2, 31))
        assertEquals("年费 ¥480.00 · 每年 3 月 12 日", FeeMath.label(48_000L, 3, 12))
        assertEquals(null, FeeMath.label(0L, 3, 12))
        assertEquals(null, FeeMath.label(48_000L, null, null))
    }

    @Test
    fun `年费流水的分类：优先含年费、其次含手续费、都没有就不落分类`() {
        val categories = listOf("c1" to "餐饮", "c2" to "手续费", "c3" to "年费")
        assertEquals("c3", FeeMath.feeCategoryId(categories))
        assertEquals("c2", FeeMath.feeCategoryId(listOf("c1" to "餐饮", "c2" to "手续费")))
        assertEquals("c4", FeeMath.feeCategoryId(listOf("c4" to "银行年费与手续费")))
        assertEquals(null, FeeMath.feeCategoryId(listOf("c1" to "餐饮", "c2" to "交通")))
        assertEquals(null, FeeMath.feeCategoryId(emptyList()))
    }
}
