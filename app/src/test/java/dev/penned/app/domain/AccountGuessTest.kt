package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 新建账户的类型猜测（M4-5，B2 裁决：**按名猜 + 预览页可改**）。
 *
 * 猜错不要紧，但常见的支付方式要猜对——它们直接决定这笔钱挂在哪个资产分组下。
 */
class AccountGuessTest {

    @Test
    fun `信贷：先花后还的一律进信贷组`() {
        assertEquals(AccountType.CREDIT, AccountGuess.typeFor("花呗"))
        assertEquals(AccountType.CREDIT, AccountGuess.typeFor("京东白条"))
        assertEquals(AccountType.CREDIT, AccountGuess.typeFor("招商银行信用卡"))
        assertEquals(AccountType.CREDIT, AccountGuess.typeFor("京东金融"))
        assertEquals(AccountType.CREDIT, AccountGuess.typeFor("美团月付"))
        assertEquals(AccountType.CREDIT, AccountGuess.typeFor("分付"))
    }

    @Test
    fun `理财：余额宝零钱通这类进理财组`() {
        assertEquals(AccountType.INVEST, AccountGuess.typeFor("余额宝"))
        assertEquals(AccountType.INVEST, AccountGuess.typeFor("零钱通"))
        assertEquals(AccountType.INVEST, AccountGuess.typeFor("某理财账户"))
        assertEquals(AccountType.INVEST, AccountGuess.typeFor("黄金"))
    }

    @Test
    fun `负债：名字里写明负债的`() {
        assertEquals(AccountType.DEBT, AccountGuess.typeFor("负债-朋友"))
        assertEquals(AccountType.DEBT, AccountGuess.typeFor("欠款"))
    }

    @Test
    fun `其余都是资金账户（具体渠道）`() {
        assertEquals(AccountType.DEBIT, AccountGuess.typeFor("零钱"))
        assertEquals(AccountType.DEBIT, AccountGuess.typeFor("账户余额"))
        assertEquals(AccountType.DEBIT, AccountGuess.typeFor("桂林银行储蓄卡(4936)"))
        assertEquals(AccountType.DEBIT, AccountGuess.typeFor("XX银行储蓄卡(1234)"))
        assertEquals(AccountType.CASH, AccountGuess.typeFor(""))
        assertEquals(AccountType.CASH, AccountGuess.typeFor("   "))
    }

    @Test
    fun `信贷优先于理财与资金（花呗分期这种名字）`() {
        assertEquals(AccountType.CREDIT, AccountGuess.typeFor("花呗分期"))
        assertEquals(AccountType.CREDIT, AccountGuess.typeFor("信用卡分期"))
    }

    @Test
    fun `分组名与资产页一致`() {
        assertEquals("资金", AccountGuess.groupLabel(AccountType.DEBIT))
        assertEquals("资金", AccountGuess.groupLabel(AccountType.CASH))
        assertEquals("信贷", AccountGuess.groupLabel(AccountType.CREDIT))
        assertEquals("理财", AccountGuess.groupLabel(AccountType.INVEST))
        assertEquals("负债", AccountGuess.groupLabel(AccountType.DEBT))
    }
}
