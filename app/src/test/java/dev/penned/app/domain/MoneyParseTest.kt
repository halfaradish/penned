package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 宽松金额解析（M4-0，规格 §二·7 / D103）：表驱动覆盖
 * 货币符号 / 千分位 / 全角 / 单位字 / 负数 / 坏值 / 超两位小数 / 数值来源。
 *
 * 每一条都是"账单文件或 LLM 真会吐出来的形状"——真实样本里的金额形如 `12.00` / `10.5` / `17.01`。
 */
class MoneyParseTest {

    @Test
    fun `纯数字与小数`() {
        assertEquals(1200L, MoneyParse.toCents("12"))
        assertEquals(1250L, MoneyParse.toCents("12.5"))
        assertEquals(1235L, MoneyParse.toCents("12.35"))
        assertEquals(5L, MoneyParse.toCents("0.05"))
        assertEquals(0L, MoneyParse.toCents("0"))
    }

    @Test
    fun `带货币符号`() {
        assertEquals(123456L, MoneyParse.toCents("¥1,234.56"))
        assertEquals(123456L, MoneyParse.toCents("￥1,234.56"))
        assertEquals(1200L, MoneyParse.toCents("¥12"))
    }

    @Test
    fun `千分位与全角逗号`() {
        assertEquals(123456L, MoneyParse.toCents("1,234.56"))
        assertEquals(100000000L, MoneyParse.toCents("1,000,000"))
        assertEquals(123456L, MoneyParse.toCents("1，234.56"))
    }

    @Test
    fun `全角数字与全角小数点`() {
        assertEquals(1234L, MoneyParse.toCents("１２．３４"))
        assertEquals(1200L, MoneyParse.toCents("１２"))
    }

    @Test
    fun `中文单位`() {
        assertEquals(1200L, MoneyParse.toCents("12元"))
        assertEquals(1200L, MoneyParse.toCents("12块"))
        assertEquals(123456L, MoneyParse.toCents("¥1,234.56元"))
    }

    @Test
    fun `前后空白与内部空白`() {
        assertEquals(1234L, MoneyParse.toCents("  12.34  "))
        assertEquals(1234L, MoneyParse.toCents("12 . 34"))
    }

    @Test
    fun `负数按符号位`() {
        assertEquals(-1250L, MoneyParse.toCents("-12.5"))
        assertEquals(-1250L, MoneyParse.toCents("－12.5"))
        assertEquals(-123456L, MoneyParse.toCents("-¥1,234.56"))
        assertEquals(1250L, MoneyParse.toCents("+12.5"))
    }

    @Test
    fun `省略整数位的小数`() {
        assertEquals(50L, MoneyParse.toCents(".5"))
        assertEquals(5L, MoneyParse.toCents(".05"))
    }

    @Test
    fun `解析不出就返回 null 不猜`() {
        assertNull(MoneyParse.toCents(""))
        assertNull(MoneyParse.toCents("   "))
        assertNull(MoneyParse.toCents("-"))
        assertNull(MoneyParse.toCents("/")) // 账单里的空值占位
        assertNull(MoneyParse.toCents("待定"))
        assertNull(MoneyParse.toCents("12元3角"))
        assertNull(MoneyParse.toCents("1.2.3"))
        assertNull(MoneyParse.toCents("¥"))
    }

    @Test
    fun `超过两位小数四舍五入到分`() {
        assertEquals(1235L, MoneyParse.toCents("12.345"))
        assertEquals(1234L, MoneyParse.toCents("12.344"))
        assertEquals(1200L, MoneyParse.toCents("12.001"))
        assertEquals(-1235L, MoneyParse.toCents("-12.345"))
    }

    @Test
    fun `整数部分过长判失败防溢出`() {
        assertEquals(999_999_999_999L, MoneyParse.toCents("9999999999.99"))
        assertNull(MoneyParse.toCents("9999999999999")) // 13 位
    }

    @Test
    fun `数值来源走 Math round 折分`() {
        assertEquals(1200L, MoneyParse.centsFromNumber(12.0))
        assertEquals(1210L, MoneyParse.centsFromNumber(12.1)) // 12.1*100 = 1209.9999… 截断会少 1 分
        assertEquals(1701L, MoneyParse.centsFromNumber(17.01))
        assertEquals(34L, MoneyParse.centsFromNumber(0.34))
        assertEquals(16L, MoneyParse.centsFromNumber(0.16))
        assertEquals(0L, MoneyParse.centsFromNumber(0.0))
    }

    @Test
    fun `与输入框契约 Money 的分工不混`() {
        // 输入框那套不吃符号与千分位——这是**有意的**（改宽会破坏光标与回填的既有口径，D103）
        assertNull(Money.parseToCents("¥12.00"))
        assertNull(Money.parseToCents("1,234.56"))
        assertEquals(123456L, MoneyParse.toCents("¥1,234.56"))
    }
}
