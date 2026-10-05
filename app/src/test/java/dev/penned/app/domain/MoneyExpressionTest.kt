package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyExpressionTest {

    private fun type(vararg keys: String): String {
        var expr = ""
        keys.forEach { expr = MoneyExpression.append(expr, it) }
        return expr
    }

    @Test
    fun `append builds expressions`() {
        assertEquals("12", type("1", "2"))
        assertEquals("12+8", type("1", "2", "+", "8"))
        assertEquals("3.5×2", type("3", ".", "5", "×", "2"))
        assertEquals("0.", type(".", "."))
        assertEquals("12+0.", type("1", "2", "+", "."))
        // 连续运算符：后一个替换前一个
        assertEquals("12-8", type("1", "2", "+", "-", "8")) // 连续运算符：替换
        // 空表达式不允许运算符开头
        assertEquals("", MoneyExpression.append("", "+"))
        // 每段最多 9 位整数、2 位小数
        assertEquals("999999999", type(*(1..12).map { "9" }.toTypedArray())) // 上限 9 位
        assertEquals("1.55", type("1", ".", "5", "5", "6"))
    }

    @Test
    fun `evaluate basic arithmetic in cents`() {
        assertEquals(2000L, MoneyExpression.evaluate("12+8"))          // 20 元
        assertEquals(400L, MoneyExpression.evaluate("12-8"))
        assertEquals(2400L, MoneyExpression.evaluate("3×8"))           // 24 元
        assertEquals(300L, MoneyExpression.evaluate("6÷2"))
        assertEquals(1500L, MoneyExpression.evaluate("3.5+11.5"))
        assertEquals(1250L, MoneyExpression.evaluate("2.5×5"))
        assertEquals(150L, MoneyExpression.evaluate("1.5÷1"))          // 1.5÷1 → 1.5
        assertEquals(167L, MoneyExpression.evaluate("5÷3"))            // 5/3 元 = 166.67 分（HALF_UP）
        assertEquals(1600L, MoneyExpression.evaluate("16"))
        assertNull(MoneyExpression.evaluate("5÷0"))
        assertNull(MoneyExpression.evaluate(""))
        assertNull(MoneyExpression.evaluate("12+"))
        assertNull(MoneyExpression.evaluate("+12"))
        assertNull(MoneyExpression.evaluate("abc"))
    }

    @Test
    fun `precedence multiply and divide first`() {
        // 2+3×4 = 14 元 = 1400 分（不是 20 元）
        assertEquals(1400L, MoneyExpression.evaluate("2+3×4"))
        // 10-6÷3 = 8 元
        assertEquals(800L, MoneyExpression.evaluate("10-6÷3"))
        // 同级从左到右：10÷2×3 = 15 元
        assertEquals(1500L, MoneyExpression.evaluate("10÷2×3"))
    }

    @Test
    fun `pending operator detection`() {
        assertFalse(MoneyExpression.hasPendingOperator("12"))
        assertTrue(MoneyExpression.hasPendingOperator("12+"))
        assertTrue(MoneyExpression.hasPendingOperator("12+8"))
    }

    @Test
    fun `round trip with Money`() {
        val cents = MoneyExpression.evaluate("12+8")!!
        assertEquals("20.00", Money.format(cents))
    }
}
