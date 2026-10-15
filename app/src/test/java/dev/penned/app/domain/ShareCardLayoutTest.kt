package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数据小卡片的版式计划（M3-16）表驱动：画布尺寸、关键文案与落位、进度条只在有预算时出现、
 * 占比卡最多 6 条、小票不含用户标识（只有用户自己写的备注/账户名）。
 */
class ShareCardLayoutTest {

    private fun StatsMath.CategoryTotal2(name: String, cents: Long, fraction: Float) =
        StatsMath.CategoryTotal(name, cents, 1, fraction)

    @Test
    fun `月汇总卡：尺寸固定、三数落位、页脚在最后`() {
        val plan = ShareCardLayout.monthSummary(
            monthLabel = "2026/10",
            incomeCents = 350_000L,
            expenseCents = 296_770L,
            budgetTotalCents = null,
            budgetSpentCents = null,
        )
        assertEquals(ShareCardLayout.WIDTH, plan.width)
        assertEquals(ShareCardLayout.SUMMARY_HEIGHT, plan.height)
        assertEquals("月汇总", plan.title)
        // 关键文案都在
        val texts = plan.texts.map { it.text }
        assertTrue(texts.contains("2026/10"))
        assertTrue(texts.contains("本月结余"))
        assertTrue(texts.contains("532.30"))            // 3500 - 2967.70
        assertTrue(texts.contains("3,500.00"))
        assertTrue(texts.contains("2,967.70"))
        assertTrue(texts.contains("收入"))
        assertTrue(texts.contains("支出"))
        // 没设预算：不画预算三件套与进度条
        assertFalse(texts.contains("本月预算"))
        assertTrue(plan.bars.isEmpty())
        // 页脚永远在最后一行，且不带用户标识
        assertEquals("由「一笔一记」生成", plan.footer.text)
        assertTrue(plan.footer.y > ShareCardLayout.SUMMARY_HEIGHT - 200)
    }

    @Test
    fun `月汇总卡：有预算时画进度条，超支的填充用支出红`() {
        val plan = ShareCardLayout.monthSummary(
            monthLabel = "2026/10",
            incomeCents = 0L,
            expenseCents = 250_000L,
            budgetTotalCents = 200_000L,
            budgetSpentCents = 250_000L,
        )
        val texts = plan.texts.map { it.text }
        assertTrue(texts.contains("本月预算"))
        assertTrue(texts.contains("2,500.00 / 2,000.00"))
        // 轨道 + 填充两条：填充用支出红、宽度不超过轨道
        assertEquals(2, plan.bars.size)
        val track = plan.bars[0]
        val fill = plan.bars[1]
        assertEquals(track.width, fill.width)                       // 超支 = 填满（fraction 夹到 1）
        assertEquals(ShareCardLayout.CardRole.EXPENSE, fill.role)
    }

    @Test
    fun `占比卡：最多 6 条、分段比例照搬、总额带单位`() {
        val categories = (1..8).map { StatsMath.CategoryTotal2("分类$it", (9 - it) * 10_000L, (9 - it) / 100f) }
        val plan = ShareCardLayout.categoryShare(
            periodLabel = "2026年9月",
            unit = "支出",
            totalCents = 296_770L,
            categories = categories,
        )
        assertEquals(ShareCardLayout.DONUT_HEIGHT, plan.height)
        assertEquals("支出占比", plan.title)
        assertTrue(plan.texts.map { it.text }.contains("2026年9月"))
        assertTrue(plan.texts.map { it.text }.contains("总支出"))
        assertTrue(plan.texts.map { it.text }.contains("2,967.70"))
        // 只列前 6 条
        assertTrue(plan.texts.map { it.text }.contains("分类6"))
        // 图例只列名称 + 占比（金额不列，避免两列叠字）
        assertFalse(plan.texts.map { it.text }.contains("500.00"))
        assertFalse(plan.texts.map { it.text }.contains("分类7"))
        assertEquals(6, plan.segments.size)
        // 分段起点按占比累加（第一条从 0 开始）
        assertEquals(0f, plan.segments[0].startFraction, 0.0001f)
        assertEquals(plan.segments[0].fraction, plan.segments[1].startFraction, 0.0001f)
        // 槽位序号就是顺序（与统计页环同一个 assignSlots 口径）
        assertEquals(listOf(0, 1, 2, 3, 4, 5), plan.segments.map { it.slot })
    }

    @Test
    fun `小票卡：五要素齐、可选两项缺了就不占行`() {
        val full = ShareCardLayout.receipt(
            categoryName = "食品餐饮 · 午餐",
            amountLabel = "-12.00",
            dateLabel = "2026年10月15日",
            note = "轻食",
            accountName = "微信",
            isExpense = true,
        )
        assertEquals(ShareCardLayout.RECEIPT_HEIGHT, full.height)
        val texts = full.texts.map { it.text }
        assertTrue(texts.contains("账单小票"))
        assertTrue(texts.contains("食品餐饮 · 午餐"))
        assertTrue(texts.contains("-12.00"))
        assertTrue(texts.contains("2026年10月15日"))
        assertTrue(texts.contains("备注") && texts.contains("轻食"))
        assertTrue(texts.contains("账户") && texts.contains("微信"))
        // 无用户标识：不出现账本名、设备号这类
        assertFalse(texts.any { it.contains("测试账本") })
        assertEquals("由「一笔一记」生成", full.footer.text)

        // 没账户没备注：两行都不出现（也不留空位）
        val lean = ShareCardLayout.receipt(
            categoryName = "未分类",
            amountLabel = "+3,000.00",
            dateLabel = "2026年10月15日",
            note = null,
            accountName = null,
            isExpense = false,
        )
        val leanTexts = lean.texts.map { it.text }
        assertFalse(leanTexts.contains("账户"))
        assertFalse(leanTexts.contains("备注"))
    }

    @Test
    fun `所有文字都落在画布内（不越界）`() {
        val plans = listOf(
            ShareCardLayout.monthSummary("2026/10", 1L, 2L, 100L, 50L),
            ShareCardLayout.categoryShare("2026年9月", "收入", 100L, listOf(StatsMath.CategoryTotal2("工资", 100L, 1f))),
            ShareCardLayout.receipt("未分类", "-1.00", "2026年10月15日", "备注", "现金", true),
        )
        plans.forEach { plan ->
            plan.texts.forEach { text ->
                assertTrue("${plan.title} 的「${text.text}」越界：x=${text.x}", text.x in 0 until plan.width)
                assertTrue("${plan.title} 的「${text.text}」越界：y=${text.y}", text.y in 0 until plan.height)
            }
            plan.bars.forEach { bar ->
                assertTrue("${plan.title} 的色块越界", bar.x >= 0 && bar.x + bar.width <= plan.width)
                assertTrue("${plan.title} 的色块越界", bar.y >= 0 && bar.y + bar.height <= plan.height)
            }
        }
    }
}
