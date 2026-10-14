package dev.penned.app.domain

/**
 * 一笔债务的余额三数（本金 / 已归还 / 剩余）。
 *
 * 债务是**实体**（D068）：本金存在 `debts.principalCents`，"已归还"由名下资金流水重算
 * （D004 不落库）——所以这里只需要三数就能算清，DAO 的行投影 [dev.penned.app.data.db.dao.DebtWithRepaid]
 * 与资产页汇总共用同一个口径。
 */
data class DebtBalance(
    val direction: DebtDirection,
    val principalCents: Long,
    val repaidCents: Long,
    /** 用户手动标的"结清"：标了就不再计入应付/应收（哪怕账面上还剩零头）。 */
    val closed: Boolean = false,
) {
    /** 「剩余」= 本金 − 已归还。**原值**，多还时为负——多还的那部分反过来是对方欠我。 */
    val remainingCents: Long get() = DebtMath.remainingCents(principalCents, repaidCents)
}

/**
 * 债务口径（M3-4，D068 + D077）——一处写清，UI 与 SQL 都不许自己算。
 *
 * 1. **钱的方向**：债务 Tab 的两格 × 债务方向 = 生成流水的类型（[flowType]）：
 *    - 债务发生（OPEN）：BORROW 我借进来 → 收入流水；LEND 我借出去 → 支出流水
 *    - 债务清偿（SETTLE）：BORROW 我还款 → 支出流水；LEND 对方还我 → 收入流水
 * 2. **已归还**：名下「清偿方向」的流水合计（[settleType]）——借入那一笔不算归还。
 *    这层口径在 SQL 里有一份孪生实现（`DebtDao.observeByBook` 的 `CASE WHEN type = …`），
 *    **两处必须一起改**，表驱动测试在 [DebtMathTest]，SQL 由 `DebtDaoTest` 钉住。
 * 3. **应付/应收**：未结清债务的剩余之和，两面各一（[payableCents]/[receivableCents]）；
 *    剩余为负（多还）时照负计入——净资产才不会凭空多出一笔。
 * 4. **净资产** = Σ账户余额 − 应付 + 应收（[netAssetCents]）：账户余额本身不受债务影响
 *    （借入时钱确实进了账户），只在"欠/被欠"两端调整。
 */
object DebtMath {

    /** 剩余 = 本金 − 已归还。 */
    fun remainingCents(principalCents: Long, repaidCents: Long): Long = principalCents - repaidCents

    /** 债务 Tab 选的这一格生成什么类型的流水（钱往哪边走）。 */
    fun flowType(direction: DebtDirection, action: DebtAction): TxType = when (action) {
        DebtAction.OPEN -> if (direction == DebtDirection.BORROW) TxType.INCOME else TxType.EXPENSE
        DebtAction.SETTLE -> if (direction == DebtDirection.BORROW) TxType.EXPENSE else TxType.INCOME
    }

    /** 该债务名下**算作已归还**的流水类型（借入/借出那一笔不算）。 */
    fun settleType(direction: DebtDirection): TxType = flowType(direction, DebtAction.SETTLE)

    /** 给定一笔名下流水，它落在债务 Tab 的哪一格（编辑回填与行标题共用）。 */
    fun actionOf(direction: DebtDirection, type: TxType): DebtAction =
        if (type == settleType(direction)) DebtAction.SETTLE else DebtAction.OPEN

    /** 一格的中文（照一木的口径，两面各一套词）：借入/借出、还款/收到还款。 */
    fun actionLabel(direction: DebtDirection, action: DebtAction): String = when (action) {
        DebtAction.OPEN -> if (direction == DebtDirection.BORROW) "借入" else "借出"
        DebtAction.SETTLE -> if (direction == DebtDirection.BORROW) "还款" else "收到还款"
    }

    /** 一笔债务的立场（新建/详情页展示）：我欠对方 / 对方欠我。 */
    fun directionLabel(direction: DebtDirection): String =
        if (direction == DebtDirection.BORROW) "我欠对方" else "对方欠我"

    /** 流水的行标题用词：由债务方向 + 流水类型推出「借入 / 借出 / 还款 / 收到还款」。 */
    fun flowLabel(direction: DebtDirection, type: TxType): String =
        actionLabel(direction, actionOf(direction, type))

    /** 结清与否（账面口径：剩余 ≤ 0；用户手动关掉的那笔由 [DebtBalance.closed] 另行处理）。 */
    fun isSettled(remainingCents: Long): Boolean = remainingCents <= 0L

    /** 应付 = BORROW 未结清债务的剩余合计（多还为负照负算）。 */
    fun payableCents(debts: List<DebtBalance>): Long =
        debts.filter { it.direction == DebtDirection.BORROW && !it.closed }.sumOf { it.remainingCents }

    /** 应收 = LEND 未结清债务的剩余合计。 */
    fun receivableCents(debts: List<DebtBalance>): Long =
        debts.filter { it.direction == DebtDirection.LEND && !it.closed }.sumOf { it.remainingCents }

    /** 净资产 = Σ账户余额 − 应付 + 应收（资产页 NetAssetCard 的口径）。 */
    fun netAssetCents(accountCents: Long, payableCents: Long, receivableCents: Long): Long =
        accountCents - payableCents + receivableCents
}
