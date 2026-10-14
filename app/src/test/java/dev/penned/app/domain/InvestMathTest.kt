package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 理财三数口径（M3-10）表驱动：无快照退回余额、单/多快照取最近、成本 = 初始 + 净转入、盈亏可负。 */
class InvestMathTest {

    @Test
    fun `总额：有快照用最近一条，没有退回余额`() {
        assertEquals(12_000L, InvestMath.totalCents(12_000L, 9_999L))
        assertEquals("无快照退回余额", 9_999L, InvestMath.totalCents(null, 9_999L))
    }

    @Test
    fun `最近快照：按日期取，同一天取后写入的`() {
        val snapshots = listOf(
            InvestSnapshot(dateEpochDay = 20_000L, valueCents = 10_000L),
            InvestSnapshot(dateEpochDay = 20_010L, valueCents = 12_000L),
            InvestSnapshot(dateEpochDay = 20_005L, valueCents = 11_000L),
        )
        assertEquals(20_010L, InvestMath.latest(snapshots)!!.dateEpochDay)
        assertEquals(null, InvestMath.latest(emptyList()))
    }

    @Test
    fun `成本 = 初始余额 + 净转入（净转入可为负）`() {
        val cases = listOf(
            Triple("只投过一次", 10_000L to 5_000L, 15_000L),
            Triple("又转入又转出", 10_000L to 3_000L, 13_000L),
            Triple("净转出（取出本金）", 10_000L to -4_000L, 6_000L),
            Triple("什么都没动", 0L to 0L, 0L),
        )
        cases.forEach { (name, input, expected) ->
            assertEquals(name, expected, InvestMath.costCents(input.first, input.second))
        }
    }

    @Test
    fun `盈亏 = 总额 − 成本，负盈亏照算`() {
        val cases = listOf(
            Triple("赚了", 12_000L to 10_000L, 2_000L),
            Triple("亏了", 8_000L to 10_000L, -2_000L),
            Triple("平", 10_000L to 10_000L, 0L),
        )
        cases.forEach { (name, input, expected) ->
            assertEquals(name, expected, InvestMath.profitCents(input.first, input.second))
        }
    }

    @Test
    fun `三数一次算齐：无快照、单快照、多快照`() {
        // 无快照：总额 = 余额（初始 100 + 净转入 20 + 收益 5 都在余额里），成本 = 120
        val noSnapshot = InvestMath.summary(
            snapshots = emptyList(), balanceCents = 12_500L, initialBalanceCents = 10_000L, netTransferCents = 2_000L,
        )
        assertEquals(12_500L, noSnapshot.totalCents)
        assertEquals(12_000L, noSnapshot.costCents)
        assertEquals(500L, noSnapshot.profitCents)
        assertEquals(null, noSnapshot.latest)

        // 单快照：总额取快照值
        val single = InvestMath.summary(
            snapshots = listOf(InvestSnapshot(20_000L, 13_000L)),
            balanceCents = 12_500L, initialBalanceCents = 10_000L, netTransferCents = 2_000L,
        )
        assertEquals(13_000L, single.totalCents)
        assertEquals(1_000L, single.profitCents)

        // 多快照：取最近的（按日期）
        val many = InvestMath.summary(
            snapshots = listOf(
                InvestSnapshot(20_000L, 13_000L),
                InvestSnapshot(20_030L, 11_000L),
            ),
            balanceCents = 12_500L, initialBalanceCents = 10_000L, netTransferCents = 2_000L,
        )
        assertEquals(11_000L, many.totalCents)
        assertEquals("负盈亏", -1_000L, many.profitCents)
        assertEquals(20_030L, many.latest!!.dateEpochDay)
    }
}
