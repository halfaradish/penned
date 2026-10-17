package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 一句话解析（M4-3）：规格点名的 ≥12 例 + 日期词、类型词、备注清理、失败态。
 *
 * 「今天」固定成 2026-10-17（周六），日期断言才不随运行日漂。
 */
class QuickParseTest {

    private val today = LocalDate.of(2026, 10, 17)

    private fun ok(text: String): QuickDraft {
        val result = QuickParse.parse(text, today)
        assertEquals("应当解析成功：$text（实际 $result）", true, result is QuickParseResult.Ok)
        return (result as QuickParseResult.Ok).draft
    }

    private fun day(text: String): Long? = ok(text).epochDay

    @Test
    fun `规格点名的一批`() {
        assertEquals(1500L, ok("肯打鸡花了15").amountCents)
        assertEquals(3250L, ok("昨天打车 32.5").amountCents)
        assertEquals(800_000L, ok("上周三工资 8000").amountCents)
        assertEquals(20_000L, ok("10月1日 收到红包 200").amountCents)
        assertEquals(123_456L, ok("交房租 1,234.56").amountCents)
        assertEquals(1234L, ok("１２．３４ 的早饭").amountCents)
        assertEquals(1000L, ok("买了2个包子花了10").amountCents) // 量词干扰：取末
        assertEquals(1200L, ok("超市 10 12").amountCents) // 多金额**取末**（12）
    }

    @Test
    fun `没找到金额就是失败态 不猜`() {
        assertEquals(QuickParseResult.NoAmount, QuickParse.parse("今天心情不错", today))
        assertEquals(QuickParseResult.NoAmount, QuickParse.parse("昨天买了点东西", today))
        assertEquals(QuickParseResult.Empty, QuickParse.parse("   ", today))
    }

    @Test
    fun `量词后的整数不算金额_但没有别的候选时仍判失败`() {
        // 只有量词数量、没有钱的写法：不猜成 2 元
        assertEquals(QuickParseResult.NoAmount, QuickParse.parse("买了2个包子", today))
    }

    @Test
    fun `金额带单位与符号`() {
        assertEquals(1200L, ok("奶茶 12元").amountCents)
        assertEquals(1200L, ok("奶茶 12块").amountCents)
        assertEquals(123_456L, ok("¥1,234.56 的机票").amountCents)
    }

    @Test
    fun `类型：收入词命中即收入 否则支出`() {
        assertEquals(TxType.INCOME, ok("收到红包 200").type)
        assertEquals(TxType.INCOME, ok("发工资 8000").type)
        assertEquals(TxType.INCOME, ok("退款 30").type)
        assertEquals(TxType.EXPENSE, ok("买菜 30").type)
        assertEquals(TxType.EXPENSE, ok("还信用卡 3000").type)
    }

    @Test
    fun `日期词：相对日`() {
        assertEquals(today.toEpochDay(), day("今天买菜 30"))
        assertEquals(today.toEpochDay() - 1, day("昨天打车 32.5"))
        assertEquals(today.toEpochDay() - 2, day("前天买书 45"))
        assertEquals(today.toEpochDay() - 3, day("大前天买书 45"))
        assertEquals(today.toEpochDay() - 5, day("5天前理发 30"))
        assertEquals(today.toEpochDay() - 3, day("三天前理发 30"))
        assertNull(day("买菜 30")) // 没日期词 = 今天（epochDay null，由调用方定）
    }

    @Test
    fun `日期词：周几取最近一次`() {
        // 今天 2026-10-17 是周六 → 本周一 = 10-12
        assertEquals(LocalDate.of(2026, 10, 12).toEpochDay(), day("周一交房租 1500"))
        assertEquals(LocalDate.of(2026, 10, 17).toEpochDay(), day("周六吃饭 60"))
        // 本周日还没到（10-18）→ 退到上周日 10-11
        assertEquals(LocalDate.of(2026, 10, 11).toEpochDay(), day("周日看电影 40"))
        assertEquals(LocalDate.of(2026, 10, 14).toEpochDay(), day("星期三咖啡 25"))
        assertEquals(LocalDate.of(2026, 10, 7).toEpochDay(), day("上周三咖啡 25"))
    }

    @Test
    fun `日期词：月日与本月X日`() {
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), day("10月1日 收到红包 200"))
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(), day("本月5日交水费 80"))
        // 今年还没到 → 退到去年
        assertEquals(LocalDate.of(2025, 12, 25).toEpochDay(), day("12月25日 买礼物 200"))
    }

    @Test
    fun `备注剔掉日期词与金额_压空格`() {
        // 备注只按规格剔"日期词 + 金额"，剩下的原样留着（不猜哪些是动词）
        assertEquals("肯打鸡花了", ok("肯打鸡花了15").note)
        assertEquals("打车", ok("昨天打车 32.5").note)
        assertEquals("工资", ok("上周三工资 8000").note)
        assertEquals("收到红包", ok("10月1日 收到红包 200").note)
        assertNull(ok("15").note) // 只剩金额 → 备注为空
    }

    @Test
    fun `只有日期或只有备注的输入`() {
        assertEquals(QuickParseResult.NoAmount, QuickParse.parse("昨天", today))
        assertEquals("超市", ok("超市 88").note)
    }

    @Test
    fun `日期词原文回显给结果卡`() {
        assertEquals("昨天", ok("昨天打车 32.5").dayWord)
        assertEquals("上周三", ok("上周三工资 8000").dayWord)
        assertNull(ok("打车 32.5").dayWord)
    }

    @Test
    fun `不存在的月日不当日期词`() {
        // 2月30日不存在 → 不认日期，整串留在备注里（金额照样解析）
        val draft = ok("2月30日 买菜 50")
        assertNull(draft.epochDay)
        assertEquals(5000L, draft.amountCents)
    }

    @Test
    fun `小数与负数写法`() {
        assertEquals(500L, ok(".5 的糖").amountCents) // 候选从数字起算，前导小数点不算（取到 5）
        assertEquals(1235L, ok("12.345 的散装").amountCents) // 三位小数四舍五入到分
        assertEquals(1000L, ok("10.00 的早饭").amountCents)
    }
}
