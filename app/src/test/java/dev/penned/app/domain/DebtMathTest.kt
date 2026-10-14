package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 债务口径（M3-4）表驱动：剩余 = 本金 − 已归还、应付/应收（含结清与多还）、净资产 = Σ账户 − 应付 + 应收、
 * 两格 × 两面的流水类型与用词。SQL 侧的孪生口径由 `DebtDaoTest` 钉。
 */
class DebtMathTest {

    private data class Case(
        val name: String,
        val direction: DebtDirection,
        val action: DebtAction,
        val type: TxType,
        val label: String,
    )

    @Test
    fun `剩余 = 本金 减 已归还`() {
        val cases = listOf(
            Triple("没还过", 100_000L to 0L, 100_000L),
            Triple("还了一部分", 100_000L to 40_000L, 60_000L),
            Triple("还清", 100_000L to 100_000L, 0L),
            Triple("还多了（负数照算）", 100_000L to 120_000L, -20_000L),
            Triple("本金 0（空壳）", 0L to 0L, 0L),
        )
        cases.forEach { (name, input, expected) ->
            val (principal, repaid) = input
            assertEquals(name, expected, DebtMath.remainingCents(principal, repaid))
        }
    }

    @Test
    fun `两格 × 两面 = 四种流水类型与用词`() {
        val cases = listOf(
            Case("我欠人，借入 → 钱进来", DebtDirection.BORROW, DebtAction.OPEN, TxType.INCOME, "借入"),
            Case("我欠人，还款 → 钱出去", DebtDirection.BORROW, DebtAction.SETTLE, TxType.EXPENSE, "还款"),
            Case("人欠我，借出 → 钱出去", DebtDirection.LEND, DebtAction.OPEN, TxType.EXPENSE, "借出"),
            Case("人欠我，还我 → 钱进来", DebtDirection.LEND, DebtAction.SETTLE, TxType.INCOME, "收到还款"),
        )
        cases.forEach { c ->
            assertEquals(c.name, c.type, DebtMath.flowType(c.direction, c.action))
            assertEquals(c.name, c.label, DebtMath.actionLabel(c.direction, c.action))
            // 行标题由流水的真实类型反推同一格（首页/详情标题用）
            assertEquals(c.name, c.label, DebtMath.flowLabel(c.direction, c.type))
        }
    }

    @Test
    fun `已归还只认清偿方向的流水（借入那笔不算）`() {
        // 同债务两笔：借入 INCOME 不算、还款 EXPENSE 才算
        assertEquals(TxType.EXPENSE, DebtMath.settleType(DebtDirection.BORROW))
        assertEquals(TxType.INCOME, DebtMath.settleType(DebtDirection.LEND))
        assertEquals(DebtAction.OPEN, DebtMath.actionOf(DebtDirection.BORROW, TxType.INCOME))
        assertEquals(DebtAction.SETTLE, DebtMath.actionOf(DebtDirection.BORROW, TxType.EXPENSE))
        assertEquals(DebtAction.OPEN, DebtMath.actionOf(DebtDirection.LEND, TxType.EXPENSE))
        assertEquals(DebtAction.SETTLE, DebtMath.actionOf(DebtDirection.LEND, TxType.INCOME))
    }

    @Test
    fun `应付应收：两面分开、结清不计、软删与负数照算`() {
        val debts = listOf(
            // 我欠人：本金 1000，还了 400 → 应付 +600
            DebtBalance(DebtDirection.BORROW, 100_000L, 40_000L),
            // 我欠人、已结清：不计
            DebtBalance(DebtDirection.BORROW, 50_000L, 0L, closed = true),
            // 人欠我：本金 300 → 应收 +300
            DebtBalance(DebtDirection.LEND, 30_000L, 0L),
            // 人欠我、多还 → 应收 −200（负数使净资产不会凭空多出）
            DebtBalance(DebtDirection.LEND, 10_000L, 30_000L),
        )
        assertEquals(60_000L, DebtMath.payableCents(debts))
        assertEquals(10_000L, DebtMath.receivableCents(debts))
        assertEquals(0L, DebtMath.payableCents(emptyList()))
        assertEquals(0L, DebtMath.receivableCents(emptyList()))
    }

    @Test
    fun `净资产 = Σ账户余额 减 应付 加 应收`() {
        data class Case(val name: String, val accounts: Long, val payable: Long, val receivable: Long, val net: Long)

        val cases = listOf(
            Case("没有债务 = 账户净额", 500_000L, 0L, 0L, 500_000L),
            Case("只有应付", 500_000L, 100_000L, 0L, 400_000L),
            Case("只有应收", 500_000L, 0L, 30_000L, 530_000L),
            Case("两面都有", 500_000L, 100_000L, 30_000L, 430_000L),
            Case("账户为负（信用卡）", -20_000L, 100_000L, 0L, -120_000L),
        )
        cases.forEach { c ->
            assertEquals(c.name, c.net, DebtMath.netAssetCents(c.accounts, c.payable, c.receivable))
        }
    }

    @Test
    fun `走查场景：借入 1000 净资产不变，归还 400 应付剩 600`() {
        // 记账前：账户 200 元、没有债务
        val before = DebtMath.netAssetCents(20_000L, 0L, 0L)
        assertEquals(20_000L, before)

        // 借入 1000：现金 +1000（账户 1200），欠款 1000 → 净资产不变
        val borrowed = DebtBalance(DebtDirection.BORROW, 100_000L, 0L)
        val afterBorrow = DebtMath.netAssetCents(
            accountCents = 120_000L,
            payableCents = DebtMath.payableCents(listOf(borrowed)),
            receivableCents = 0L,
        )
        assertEquals("借入不改变净资产", before, afterBorrow)
        assertEquals(100_000L, borrowed.remainingCents)

        // 归还 400：现金 −400（账户 800），已还 400 → 应付 600，净资产仍不变
        val repaid = borrowed.copy(repaidCents = 40_000L)
        val afterRepay = DebtMath.netAssetCents(
            accountCents = 80_000L,
            payableCents = DebtMath.payableCents(listOf(repaid)),
            receivableCents = 0L,
        )
        assertEquals("还款也不改变净资产", before, afterRepay)
        assertEquals("应付剩 600", 60_000L, DebtMath.payableCents(listOf(repaid)))
        assertEquals(true, DebtMath.isSettled(0L))
        assertEquals(false, DebtMath.isSettled(60_000L))
    }
}
