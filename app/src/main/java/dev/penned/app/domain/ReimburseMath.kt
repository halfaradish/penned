package dev.penned.app.domain

/**
 * 报销（M3-3）：流水上的状态机（`NONE` / `PENDING` / `DONE`，D008）+ 报销账户 + 报销金额。
 *
 * 金额口径：`reimburseCents` **缺省 0 = 按流水净额全额报销**——0 不是"报 0 元"，是"没单独指定"。
 * 资产页「可报 / 已报」= 各自状态的 [claimCents] 求和，**SQL 与这里同一个口径**
 * （`TransactionDao.observeReimburseSums` 里的 `CASE WHEN reimburseCents > 0 …`，改一处必须改另一处）。
 */
object ReimburseMath {

    /** 这一笔的报销金额：填了就按填的，没填（0）按流水净额全额。超额（> 净额）不拦，照填的算。 */
    fun claimCents(reimburseCents: Long, amountCents: Long): Long =
        if (reimburseCents > 0L) reimburseCents else amountCents

    /** 没单独指定报销金额（= 按净额全额）。 */
    fun isFullClaim(reimburseCents: Long): Boolean = reimburseCents <= 0L

    /** 记一笔报销抽屉里「报销金额」行的文案：`全额 100.00`（缺省）/ `30.00`（自定）。 */
    fun claimLabel(reimburseCents: Long, amountCents: Long): String =
        if (isFullClaim(reimburseCents)) "全额 ${Money.format(amountCents)}" else Money.format(reimburseCents)
}
