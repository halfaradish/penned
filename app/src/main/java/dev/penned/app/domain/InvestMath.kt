package dev.penned.app.domain

/**
 * 理财账户的总额 / 成本 / 盈亏（M3-10，纯函数，表驱动测试在 `InvestMathTest`）。
 *
 * 口径（D004 的推广：一切从快照与流水重算，不落库）：
 * - **总额** = 最近一次快照的现值；**没有快照就退回账户余额重算**（`balanceCents`）
 * - **成本** = 初始余额 + **净转入**（转入 − 转出；转出含手续费，与余额重算同一口径）
 * - **盈亏** = 总额 − 成本（可为负）
 */
data class InvestSnapshot(val dateEpochDay: Long, val valueCents: Long)

object InvestMath {

    /** 最近一次快照（按日期，再按写入顺序取最后一条）；没有快照返回 null。 */
    fun latest(snapshots: List<InvestSnapshot>): InvestSnapshot? =
        snapshots.maxWithOrNull(compareBy({ it.dateEpochDay }, { it.valueCents }))

    /** 总额：最近快照值，没有快照退回余额。 */
    fun totalCents(latestSnapshotCents: Long?, balanceCents: Long): Long =
        latestSnapshotCents ?: balanceCents

    /** 成本 = 初始余额 + 净转入。 */
    fun costCents(initialBalanceCents: Long, netTransferCents: Long): Long =
        initialBalanceCents + netTransferCents

    /** 盈亏 = 总额 − 成本（负盈亏照算）。 */
    fun profitCents(totalCents: Long, costCents: Long): Long = totalCents - costCents

    /** 三数一次算齐（资产页理财卡与理财详情页共用一处口径）。 */
    fun summary(
        snapshots: List<InvestSnapshot>,
        balanceCents: Long,
        initialBalanceCents: Long,
        netTransferCents: Long,
    ): InvestSummary {
        val latest = latest(snapshots)
        val total = totalCents(latest?.valueCents, balanceCents)
        val cost = costCents(initialBalanceCents, netTransferCents)
        return InvestSummary(
            totalCents = total,
            costCents = cost,
            profitCents = profitCents(total, cost),
            latest = latest,
        )
    }
}

/** 理财账户的三数 + 最近快照（没有快照时 [latest] 为 null，总额走余额）。 */
data class InvestSummary(
    val totalCents: Long,
    val costCents: Long,
    val profitCents: Long,
    val latest: InvestSnapshot?,
)
