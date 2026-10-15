package dev.penned.app.domain

import dev.penned.app.data.db.dao.TxWithCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 资产走势（M3-14）表驱动：单笔影响（收入/支出/转账手续费）、不计收支剔除、逐日累计、四数汇总。
 */
class AssetTrendMathTest {

    private fun tx(
        type: TxType,
        cents: Long,
        fee: Long = 0,
        excludeFromStats: Boolean = false,
        accountId: String? = "a1",
        targetAccountId: String? = null,
    ) = TxWithCategory(
        id = "t", type = type, amountCents = cents, accountId = accountId, categoryId = null,
        dateEpochMinute = 0L, note = null, categoryName = null, categoryParentName = null,
        categoryIconKey = null, targetAccountName = null, targetAccountId = targetAccountId,
        excludeFromStats = excludeFromStats,
        refundCents = 0, discountCents = 0, feeCents = fee,
    )

    @Test
    fun `单笔影响：收入加、支出减、转账只减手续费`() {
        assertEquals(1_200L, AssetTrendMath.deltaCents(tx(TxType.INCOME, 1_200L)))
        assertEquals(-1_200L, AssetTrendMath.deltaCents(tx(TxType.EXPENSE, 1_200L)))
        // 转账本身上下账户对冲，只剩手续费离开账户体系
        assertEquals(-200L, AssetTrendMath.deltaCents(tx(TxType.TRANSFER, 50_000L, fee = 200L)))
        assertEquals(0L, AssetTrendMath.deltaCents(tx(TxType.TRANSFER, 50_000L)))
    }

    @Test
    fun `没落在账户上的流水不算（纯现金无账户记账不进"账户合计"）`() {
        // 收入/支出都挂账户；无账户的那笔不进合计（D005 允许无账户记账）
        assertEquals(false, AssetTrendMath.affectsAccount(tx(TxType.EXPENSE, 100L, accountId = null)))
        assertEquals(true, AssetTrendMath.affectsAccount(tx(TxType.TRANSFER, 100L, accountId = null, targetAccountId = "a2")))
        assertEquals(
            100L,
            AssetTrendMath.dayDeltaCents(
                listOf(
                    tx(TxType.EXPENSE, 100L),
                    tx(TxType.EXPENSE, 9_999L, accountId = null),   // 无账户：不算
                )
            ).let { -it },
        )
    }

    @Test
    fun `一天的变化：不计收支的流水不算`() {
        val rows = listOf(
            tx(TxType.INCOME, 300_000L),
            tx(TxType.EXPENSE, 1_200L),
            tx(TxType.TRANSFER, 50_000L, fee = 200L),
            tx(TxType.EXPENSE, 9_999L, excludeFromStats = true),
        )
        assertEquals(300_000L - 1_200L - 200L, AssetTrendMath.dayDeltaCents(rows))
    }

    @Test
    fun `逐日累计：期初 + 每天变化，长度不足按 0 补、多了忽略`() {
        val series = AssetTrendMath.series(openingCents = 1_000L, dayCount = 4, dayDeltas = listOf(100L, -50L, 0L))
        assertEquals(listOf(1_100L, 1_050L, 1_050L, 1_050L), series)
        // 多出来的变化忽略（窗口外的行理论上不该有）
        assertEquals(listOf(1_100L, 1_100L), AssetTrendMath.series(1_000L, 2, listOf(100L, 0L, 999L)))
        // 天数非正：空序列
        assertEquals(emptyList<Long>(), AssetTrendMath.series(1_000L, 0, listOf(100L)))
    }

    @Test
    fun `四数汇总：期末取最后一天、最高最低、变化可负`() {
        val summary = AssetTrendMath.summary(openingCents = 4_000L, series = listOf(4_100L, 3_900L, 4_050L))
        assertEquals(4_000L, summary.openingCents)
        assertEquals(4_050L, summary.closingCents)
        assertEquals(4_100L, summary.highestCents)
        assertEquals(3_900L, summary.lowestCents)
        assertEquals(50L, summary.changeCents)

        val loss = AssetTrendMath.summary(4_000L, listOf(3_500L))
        assertEquals(-500L, loss.changeCents)
    }

    @Test
    fun `空序列：四数都退成期初`() {
        val summary = AssetTrendMath.summary(openingCents = 4_000L, series = emptyList())
        assertEquals(4_000L, summary.closingCents)
        assertEquals(4_000L, summary.highestCents)
        assertEquals(4_000L, summary.lowestCents)
        assertEquals(0L, summary.changeCents)
    }
}
