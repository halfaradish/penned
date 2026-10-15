package dev.penned.app.domain

import dev.penned.app.data.db.dao.TxWithCategory

/**
 * 资产走势（M3-14，纯函数 + 表驱动测试在 `AssetTrendMathTest`）。
 *
 * 口径：**账户合计余额**（不落库，从流水重算，D004）——
 * 期初 = 可见账户初始余额之和 + 窗口前的净变化（`TransactionDao.observeOpeningAssetCents`，与余额重算同源）；
 * 之后逐日累计。一天的变化 = 收入 − 支出 − 转账手续费（转账本身上下账户对冲，只剩手续费离开账户体系）；
 * `excludeFromStats` 的流水不进（与统计页其余模块同源）。
 *
 * **债务的应付/应收不计入**（净资产口径里它是单独两项）：要按日重算债务余额得再拉一条债务时间线，
 * 代价与收益不成比例——卡片副标题写明"账户合计"，别让人以为是净资产。
 */
object AssetTrendMath {

    /** 单笔流水对"账户合计"的影响（分）。 */
    fun deltaCents(row: TxWithCategory): Long = when (row.type) {
        TxType.INCOME -> row.amountCents
        TxType.EXPENSE -> -row.amountCents
        TxType.TRANSFER -> -row.feeCents
    }

    /**
     * 这笔流水落在账户上吗：**账户合计只看动过账户的流水**——
     * D005 允许"纯现金无账户"记账（`accountId` 与 `targetAccountId` 都为空），那种流水不改变任何账户余额，
     * 计进来会把走势算成"凭空蒸发"（模拟机走查在用户账本上抓到过：756 笔导入流水大多没有账户）。
     */
    fun affectsAccount(row: TxWithCategory): Boolean = row.accountId != null || row.targetAccountId != null

    /** 某一天的变化合计（只算落在账户上、且计入收支的流水）。 */
    fun dayDeltaCents(rows: List<TxWithCategory>): Long =
        rows.filter { !it.excludeFromStats && affectsAccount(it) }.sumOf { deltaCents(it) }

    /**
     * 逐日累计：第 i 项 = 第 i 天收盘时的账户合计。
     * [dayDeltas] 长度应与天数一致（缺的按 0 算，多的忽略）；返回长度 = [dayCount]。
     */
    fun series(openingCents: Long, dayCount: Int, dayDeltas: List<Long>): List<Long> {
        if (dayCount <= 0) return emptyList()
        var running = openingCents
        return (0 until dayCount).map { index ->
            running += dayDeltas.getOrElse(index) { 0L }
            running
        }
    }

    /** 走势四数：期初 / 期末 / 最高 / 最低（空序列给全 0）。 */
    data class Summary(
        val openingCents: Long,
        val closingCents: Long,
        val highestCents: Long,
        val lowestCents: Long,
    ) {
        val changeCents: Long get() = closingCents - openingCents
    }

    fun summary(openingCents: Long, series: List<Long>): Summary = Summary(
        openingCents = openingCents,
        closingCents = series.lastOrNull() ?: openingCents,
        highestCents = series.maxOrNull() ?: openingCents,
        lowestCents = series.minOrNull() ?: openingCents,
    )
}
