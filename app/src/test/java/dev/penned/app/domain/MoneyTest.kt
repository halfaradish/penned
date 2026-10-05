package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `format - cents to yuan string`() {
        assertEquals("0.00", Money.format(0))
        assertEquals("16.00", Money.format(1600))
        assertEquals("16.50", Money.format(1650))
        assertEquals("16.55", Money.format(1655))
        assertEquals("-16.00", Money.format(-1600))
        assertEquals("8499.00", Money.format(849900))
        assertEquals("0.05", Money.format(5))
    }

    @Test
    fun `parse - valid inputs`() {
        assertEquals(1600L, Money.parseToCents("16"))
        assertEquals(1600L, Money.parseToCents("16.0"))
        assertEquals(1650L, Money.parseToCents("16.5"))
        assertEquals(1655L, Money.parseToCents("16.55"))
        assertEquals(50L, Money.parseToCents(".5"))
        assertEquals(50L, Money.parseToCents("0.5"))
        assertEquals(0L, Money.parseToCents("0"))
        assertEquals(99999999900L, Money.parseToCents("999999999")) // 9 位整数为上限
    }

    @Test
    fun `parse - invalid inputs return null`() {
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("  "))
        assertNull(Money.parseToCents("16.555"))
        assertNull(Money.parseToCents("16."))
        assertNull(Money.parseToCents("-5"))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents("1,000"))
        assertNull(Money.parseToCents("1234567890")) // 10 位整数，超限
    }

    @Test
    fun `round trip`() {
        listOf(0L, 5L, 1650L, 1655L, 849900L).forEach { cents ->
            assertEquals(cents, Money.parseToCents(Money.format(cents)))
        }
    }
}

class MoneyInputTest {

    @Test
    fun `digit append`() {
        assertEquals("1", MoneyInput.append("", '1'))
        assertEquals("16", MoneyInput.append("1", '6'))
        // 前导 0 替换
        assertEquals("5", MoneyInput.append("0", '5'))
        assertEquals("0", MoneyInput.append("0", '0'))
    }

    @Test
    fun `decimal point`() {
        assertEquals("0.", MoneyInput.append("", '.'))
        assertEquals("16.", MoneyInput.append("16", '.'))
        assertEquals("16.", MoneyInput.append("16.", '.')) // 已有小数点
        assertEquals("16.5", MoneyInput.append("16.", '5'))
        assertEquals("16.55", MoneyInput.append("16.5", '5'))
        assertEquals("16.55", MoneyInput.append("16.55", '6')) // 最多 2 位小数
    }

    @Test
    fun `integer digit limit`() {
        var s = ""
        repeat(12) { s = MoneyInput.append(s, '9') }
        assertEquals(9, s.length)
    }

    @Test
    fun `backspace and clear`() {
        assertEquals("16.", MoneyInput.backspace("16.5"))
        assertEquals("16", MoneyInput.backspace("16."))
        assertEquals("", MoneyInput.backspace("1"))
        assertEquals("", MoneyInput.backspace(""))
        assertEquals("", MoneyInput.clear())
    }
}
